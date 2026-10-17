package com.fetchly.app.data.direct

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DirectMediaResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: DirectMediaResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = DirectMediaResolver(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(
        code: Int = 200,
        mime: String? = null,
        bodyBytes: Int = 0,
        disposition: String? = null,
        contentRange: String? = null,
    ) {
        val response = MockResponse().setResponseCode(code)
        if (bodyBytes > 0) response.setBody("x".repeat(bodyBytes))
        mime?.let { response.setHeader("Content-Type", it) }
        disposition?.let { response.setHeader("Content-Disposition", it) }
        contentRange?.let { response.setHeader("Content-Range", it) }
        server.enqueue(response)
    }

    @Test
    fun `head response with media type resolves the file and its size`() = runBlocking {
        enqueue(mime = "video/mp4", bodyBytes = 123_456)
        val url = server.url("/videos/clip.mp4").toString()

        val file = resolver.resolve(url) as DirectResult.File

        assertEquals(url, file.url)
        assertEquals("video/mp4", file.mime)
        assertEquals(123_456L, file.sizeBytes)
        assertEquals("clip.mp4", file.filename)
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun `head 405 falls back to a ranged get and reads the content range total`() = runBlocking {
        enqueue(code = 405)
        enqueue(code = 206, mime = "video/webm", bodyBytes = 1, contentRange = "bytes 0-0/9999")
        val url = server.url("/movie.webm").toString()

        val file = resolver.resolve(url) as DirectResult.File

        assertEquals("video/webm", file.mime)
        assertEquals(9999L, file.sizeBytes)
        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("bytes=0-0", server.takeRequest().headers["Range"])
    }

    @Test
    fun `html pages are not direct media`() = runBlocking {
        enqueue(mime = "text/html; charset=utf-8", bodyBytes = 100)

        val result = resolver.resolve(server.url("/watch").toString())

        assertEquals(DirectResult.NotDirect, result)
        assertEquals(1, server.requestCount) // HEAD already answered; no ranged retry
    }

    @Test
    fun `json payloads are not direct media`() = runBlocking {
        enqueue(mime = "application/json", bodyBytes = 50)

        assertEquals(DirectResult.NotDirect, resolver.resolve(server.url("/api/info").toString()))
    }

    @Test
    fun `octet stream is accepted as a direct file`() = runBlocking {
        enqueue(mime = "application/octet-stream", bodyBytes = 512)

        val file = resolver.resolve(server.url("/download").toString()) as DirectResult.File

        assertEquals("application/octet-stream", file.mime)
        assertEquals(512L, file.sizeBytes)
        assertEquals("download", file.filename)
    }

    @Test
    fun `content disposition filename wins over the url path`() = runBlocking {
        enqueue(mime = "video/mp4", bodyBytes = 10, disposition = "attachment; filename=\"My Video.mp4\"")

        val file = resolver.resolve(server.url("/download?id=42").toString()) as DirectResult.File

        assertEquals("My Video.mp4", file.filename)
    }

    @Test
    fun `browser headers with a server error are not direct`() = runBlocking {
        enqueue(code = 404, mime = "text/html", bodyBytes = 5)
        enqueue(code = 404, mime = "text/html", bodyBytes = 5)

        assertEquals(DirectResult.NotDirect, resolver.resolve(server.url("/gone.mp4").toString()))
    }

    @Test
    fun `connection failures are not direct`() = runBlocking {
        val result = resolver.resolve("http://127.0.0.1:1/none.mp4")

        assertEquals(DirectResult.NotDirect, result)
    }
}
