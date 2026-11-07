package com.fetchly.app.data.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class DirectDownloaderTest {
    private val bytes = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }

    private fun full(data: ByteArray = bytes) = MockResponse()
        .setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"v1\"")
        .setBody(Buffer().write(data))

    private fun range(request: RecordedRequest, data: ByteArray = bytes, etag: String = "\"v1\""): MockResponse {
        val bounds = request.getHeader("Range")!!.removePrefix("bytes=").split('-')
        val start = bounds[0].toInt()
        val end = bounds[1].toInt()
        assertEquals(etag, request.getHeader("If-Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
        return MockResponse().setResponseCode(206).setHeader("ETag", etag)
            .setHeader("Content-Range", "bytes $start-$end/${data.size}")
            .setBody(Buffer().write(data, start, end - start + 1))
    }

    private fun scenario(dispatcher: Dispatcher, check: suspend CoroutineScope.(MockWebServer, File) -> Unit) = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = dispatcher
            server.start()
            val dest = File.createTempFile("fetchly-range", ".bin")
            try { check(server, dest) } finally { HttpDownloadState.clear(dest); dest.delete() }
        }
    }

    @Test fun parallelRangesProduceExactOriginalBytes() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) =
            if (request.getHeader("Range") == null) full() else range(request)
    }) { server, dest ->
        var finalBytes = 0L
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {
            finalBytes = it.downloadedBytes
        }
        assertArrayEquals(bytes, dest.readBytes())
        assertEquals(bytes.size.toLong(), finalBytes)
        assertEquals(5, server.requestCount)
    }

    @Test fun ignoredRangesRestartAsSingleDownload() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = full()
    }) { server, dest ->
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
        assertArrayEquals(bytes, dest.readBytes())
        assertTrue(server.requestCount >= 3)
    }

    @Test fun changedValidatorCannotMixFileVersions() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = if (request.getHeader("Range") == null) full()
            else range(request).setHeader("ETag", "\"v2\"")
    }) { server, dest ->
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
        assertArrayEquals(bytes, dest.readBytes())
        assertTrue(server.requestCount >= 3)
    }

    @Test fun wrongContentRangeFallsBack() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = if (request.getHeader("Range") == null) full()
            else range(request).setHeader("Content-Range", "bytes 0-1/${bytes.size}")
    }) { server, dest ->
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
        assertArrayEquals(bytes, dest.readBytes())
    }

    @Test fun weakValidatorUsesOneRequest() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = full().setHeader("ETag", "W/\"v1\"")
    }) { server, dest ->
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
        assertArrayEquals(bytes, dest.readBytes())
        assertEquals(1, server.requestCount)
    }

    @Test fun smallFilesUseOneRequestAndTruncateOldOutput() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = full(bytes.copyOf(1024))
    }) { server, dest ->
        dest.writeBytes(bytes)
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
        assertArrayEquals(bytes.copyOf(1024), dest.readBytes())
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationInterruptsBlockedResponse() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = full(bytes.copyOf(1024))
            .setSocketPolicy(SocketPolicy.NO_RESPONSE)
    }) { server, dest ->
        val job = async { DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {} }
        withTimeout(5000) {
            while (server.requestCount == 0) delay(10)
            job.cancelAndJoin()
        }
        assertTrue(job.isCancelled)
    }

    @Test fun cancellationStopsParallelRangeReads() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = if (request.getHeader("Range") == null) full()
            else range(request).setSocketPolicy(SocketPolicy.NO_RESPONSE)
    }) { server, dest ->
        val client = OkHttpClient()
        val job = async { DirectDownloader(client).download(server.url("/file").toString(), dest) {} }
        withTimeout(5000) {
            while (server.requestCount < 5) delay(10)
            job.cancelAndJoin()
        }
        assertTrue(job.isCancelled)
        assertEquals(0, client.dispatcher.runningCallsCount())
    }

    @Test fun missingContentLengthStaysSequential() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse()
            .setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"v1\"")
            .setChunkedBody(Buffer().write(bytes.copyOf(1024)), 128)
    }) { server, dest ->
        DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
        assertArrayEquals(bytes.copyOf(1024), dest.readBytes())
        assertEquals(1, server.requestCount)
    }

    @Test fun retriesOnlyTheInterruptedSegmentFromItsWrittenOffset() {
        val requests = ConcurrentLinkedQueue<String>()
        val failures = AtomicInteger()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range") ?: return full()
                requests.add(header)
                val response = range(request)
                return if (header == "bytes=0-2097151" && failures.getAndIncrement() == 0)
                    response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) else response
            }
        }) { server, dest ->
            DirectDownloader(OkHttpClient(), retryDelayMillis = 0).download(server.url("/file").toString(), dest) {}
            assertArrayEquals(bytes, dest.readBytes())
            val firstSegment = requests.filter { it.endsWith("-2097151") }
            assertEquals(2, firstSegment.size)
            val resumedAt = firstSegment.last().removePrefix("bytes=").substringBefore('-').toLong()
            assertTrue(resumedAt > 0 && resumedAt < 2097152)
            assertEquals(1, requests.count { it == "bytes=2097152-4194303" })
            assertEquals(1, requests.count { it == "bytes=4194304-6291455" })
            assertEquals(1, requests.count { it == "bytes=6291456-8388607" })
        }
    }

    @Test fun cancellationThenNewDownloaderResumesSavedBytes() {
        val phase = AtomicInteger()
        val resumedRequests = ConcurrentLinkedQueue<String>()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range") ?: return full()
                if (phase.get() == 1) resumedRequests.add(header)
                val response = range(request)
                return if (phase.get() == 0) response.throttleBody(64 * 1024, 25, TimeUnit.MILLISECONDS) else response
            }
        }) { server, dest ->
            val downloaded = AtomicLong()
            val url = server.url("/file").toString()
            val first = async {
                DirectDownloader(OkHttpClient()).download(url, dest) { downloaded.set(it.downloadedBytes) }
            }
            withTimeout(5000) {
                while (downloaded.get() < 256 * 1024) delay(5)
                first.cancelAndJoin()
            }
            assertTrue(HttpDownloadState.sidecar(dest).isFile)
            phase.set(1)
            val state = HttpDownloadState.open(dest, url, url, bytes.size.toLong(), "\"v1\"", 4)
            val saved = state.downloaded()
            assertTrue(saved > 0)
            val starts = (0..3).associate { state.end(it) to state.start(it) + state.offset(it) }
            var firstProgress: Long? = null
            DirectDownloader(OkHttpClient()).download(url, dest) {
                if (firstProgress == null) firstProgress = it.downloadedBytes
            }
            assertEquals(saved, firstProgress)
            assertArrayEquals(bytes, dest.readBytes())
            for (header in resumedRequests) {
                val bounds = header.removePrefix("bytes=").split('-').map { it.toLong() }
                assertEquals(starts[bounds[1]], bounds[0])
            }
        }
    }

    private fun seedCheckpoint(dest: File, url: String, etag: String = "\"v1\"") {
        val state = HttpDownloadState.open(dest, url, url, bytes.size.toLong(), etag, 4)
        RandomAccessFile(dest, "rw").use { output ->
            output.write(bytes, 0, 1024)
            state.checkpoint(0, 1024, output)
            output.seek(state.start(1))
            output.write(bytes, state.start(1).toInt(), state.length(1).toInt())
            state.checkpoint(1, state.length(1), output)
        }
    }

    @Test fun persistedResumeSkipsCompletedSegments() {
        val requests = ConcurrentLinkedQueue<String>()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range") ?: return full()
                requests.add(header)
                return range(request)
            }
        }) { server, dest ->
            val url = server.url("/file").toString()
            seedCheckpoint(dest, url)
            DirectDownloader(OkHttpClient()).download(url, dest) {}
            assertArrayEquals(bytes, dest.readBytes())
            assertTrue("bytes=1024-2097151" in requests)
            assertFalse("bytes=2097152-4194303" in requests)
            assertEquals(3, requests.size)
        }
    }

    @Test fun changedFileVersionDiscardsCheckpoint() {
        val fresh = ByteArray(bytes.size) { (it % 239).toByte() }
        val requests = ConcurrentLinkedQueue<String>()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range") ?: return full(fresh).setHeader("ETag", "\"v2\"")
                requests.add(header)
                return range(request, fresh, "\"v2\"")
            }
        }) { server, dest ->
            val url = server.url("/file").toString()
            seedCheckpoint(dest, url)
            DirectDownloader(OkHttpClient()).download(url, dest) {}
            assertArrayEquals(fresh, dest.readBytes())
            assertTrue("bytes=0-2097151" in requests)
            assertEquals(4, requests.size)
        }
    }

    @Test fun corruptCheckpointAndEvictedDataRestartSafely() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) =
            if (request.getHeader("Range") == null) full() else range(request)
    }) { server, dest ->
        val url = server.url("/file").toString()
        seedCheckpoint(dest, url)
        HttpDownloadState.sidecar(dest).writeText("{broken")
        DirectDownloader(OkHttpClient()).download(url, dest) {}
        assertArrayEquals(bytes, dest.readBytes())
        dest.delete()
        DirectDownloader(OkHttpClient()).download(url, dest) {}
        assertArrayEquals(bytes, dest.readBytes())
        assertEquals(10, server.requestCount)
    }

    @Test fun exhaustedSegmentHasOneBoundedRetryBudget() {
        val failedRequests = AtomicInteger()
        val metadataRequests = AtomicInteger()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range") ?: return full().also { metadataRequests.incrementAndGet() }
                return if (header.endsWith("-2097151")) {
                    failedRequests.incrementAndGet()
                    MockResponse().setResponseCode(503)
                } else range(request)
            }
        }) { server, dest ->
            try {
                DirectDownloader(OkHttpClient(), retryDelayMillis = 0).download(server.url("/file").toString(), dest) {}
                fail("Retries must stop")
            } catch (expected: DirectDownloader.HttpFailure) { assertEquals(503, expected.code) }
            assertEquals(3, failedRequests.get())
            assertEquals(1, metadataRequests.get())
            assertTrue(HttpDownloadState.sidecar(dest).isFile)
        }
    }

    @Test fun transientMetadataFailureRetriesButPermanentRefusalDoesNot() {
        val requests = AtomicInteger()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (requests.getAndIncrement() == 0) MockResponse().setResponseCode(503) else full(bytes.copyOf(1024))
        }) { server, dest ->
            DirectDownloader(OkHttpClient(), retryDelayMillis = 0).download(server.url("/file").toString(), dest) {}
            assertEquals(2, requests.get())
            assertArrayEquals(bytes.copyOf(1024), dest.readBytes())
        }
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(404)
        }) { server, dest ->
            try {
                DirectDownloader(OkHttpClient(), retryDelayMillis = 0).download(server.url("/file").toString(), dest) {}
                fail("404 must fail")
            } catch (expected: DirectDownloader.HttpFailure) { assertEquals(404, expected.code) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun sourceWithoutValidatorRestartsInsteadOfAppending() {
        val requests = AtomicInteger()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertNull(request.getHeader("Range"))
                val response = full(bytes.copyOf(1024)).removeHeader("ETag")
                return if (requests.getAndIncrement() == 0)
                    response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) else response
            }
        }) { server, dest ->
            DirectDownloader(OkHttpClient(), retryDelayMillis = 0).download(server.url("/file").toString(), dest) {}
            assertArrayEquals(bytes.copyOf(1024), dest.readBytes())
            assertEquals(2, requests.get())
            assertFalse(HttpDownloadState.sidecar(dest).exists())
        }
    }

    @Test fun cancellationDuringRetryAfterStopsWithoutAnotherRequest() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(429).setHeader("Retry-After", "30")
    }) { server, dest ->
        val job = async { DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {} }
        withTimeout(5000) {
            while (server.requestCount == 0) delay(5)
            delay(50)
            job.cancelAndJoin()
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun overlappingAttemptsWaitForCancelledWriterBeforeResuming() {
        val phase = AtomicInteger()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Range") == null) return full()
                val response = range(request)
                return if (phase.get() == 0) response.throttleBody(64 * 1024, 250, TimeUnit.MILLISECONDS) else response
            }
        }) { server, dest ->
            val url = server.url("/file").toString()
            val downloaded = AtomicLong()
            val first = async { DirectDownloader(OkHttpClient()).download(url, dest) { downloaded.set(it.downloadedBytes) } }
            withTimeout(5000) { while (downloaded.get() < 256 * 1024 || server.requestCount < 5) delay(5) }
            val before = server.requestCount
            val second = async { DirectDownloader(OkHttpClient()).download(url, dest) {} }
            delay(50)
            assertEquals(before, server.requestCount)
            phase.set(1)
            withTimeout(5000) { first.cancelAndJoin(); second.await() }
            assertArrayEquals(bytes, dest.readBytes())
        }
    }

    @Test fun smallFileResumesWithOneValidatedRange() {
        val data = bytes.copyOf(1024 * 1024)
        val phase = AtomicInteger()
        val resumed = ConcurrentLinkedQueue<String>()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range")
                if (header != null) {
                    resumed.add(header)
                    return range(request, data)
                }
                val response = full(data)
                return if (phase.get() == 0) response.throttleBody(64 * 1024, 25, TimeUnit.MILLISECONDS) else response
            }
        }) { server, dest ->
            val url = server.url("/small").toString()
            val downloaded = AtomicLong()
            val first = async { DirectDownloader(OkHttpClient()).download(url, dest) { downloaded.set(it.downloadedBytes) } }
            withTimeout(5000) {
                while (downloaded.get() < 64 * 1024) delay(5)
                first.cancelAndJoin()
            }
            phase.set(1)
            DirectDownloader(OkHttpClient()).download(url, dest) {}
            assertArrayEquals(data, dest.readBytes())
            assertEquals(1, resumed.size)
            assertTrue(resumed.single().removePrefix("bytes=").substringBefore('-').toLong() > 0)
        }
    }

    @Test fun differentSourceUrlCannotReuseAnUnrelatedCheckpoint() {
        val requests = ConcurrentLinkedQueue<String>()
        scenario(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range") ?: return full()
                requests.add(header)
                return range(request)
            }
        }) { server, dest ->
            seedCheckpoint(dest, server.url("/old").toString())
            DirectDownloader(OkHttpClient()).download(server.url("/new").toString(), dest) {}
            assertArrayEquals(bytes, dest.readBytes())
            assertEquals(4, requests.size)
            assertTrue("bytes=0-2097151" in requests)
        }
    }

    @Test fun storageFailureIsNotRetriedAsANetworkFailure() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = full()
    }) { server, dest ->
        // A file cannot also be the parent directory of the requested output.
        try {
            DirectDownloader(OkHttpClient(), retryDelayMillis = 0).download(
                server.url("/file").toString(), File(dest, "impossible.bin"),
            ) {}
            fail("Storage failure must surface")
        } catch (_: DownloadStorageException) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun perResponseThrottleBenchmark() = runBlocking {
        val timings = mutableListOf<Long>()
        for (parallel in listOf(false, true)) {
            scenario(object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val response = if (request.getHeader("Range") == null) full() else range(request)
                    if (!parallel) response.removeHeader("Accept-Ranges")
                    return response.throttleBody(256 * 1024, 100, TimeUnit.MILLISECONDS)
                }
            }) { server, dest ->
                val started = System.nanoTime()
                DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
                timings += (System.nanoTime() - started) / 1_000_000
                assertArrayEquals(bytes, dest.readBytes())
            }
        }
        // Diagnostic only: wall-clock speed assertions would be flaky on CI.
        println("CONTROLLED_BENCHMARK bytes=${bytes.size} single_ms=${timings[0]} ranges_ms=${timings[1]}")
    }

    @Test fun serverFailureIsNotSaved() = scenario(object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503)
    }) { server, dest ->
        try {
            DirectDownloader(OkHttpClient()).download(server.url("/file").toString(), dest) {}
            fail("HTTP failure must throw")
        } catch (expected: IOException) { assertTrue(expected.message!!.contains("503")) }
        assertEquals(0L, dest.length())
    }
}
