package com.fetchly.app.ui.share

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.download.*
import com.fetchly.app.data.repository.*
import com.fetchly.app.domain.batch.BatchFixture
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareDownloadViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Commands : DownloadCommands {
        val added = mutableListOf<QualityOption>()
        var fail = false
        var disposition = EnqueueDisposition.QUEUED
        override suspend fun enqueue(media: MediaInfo, option: QualityOption): String {
            if (fail) throw java.io.IOException()
            added += option; return "queued"
        }
        override suspend fun enqueueWithReceipt(media: MediaInfo, option: QualityOption) = EnqueueReceipt(enqueue(media, option), disposition)
        override suspend fun cancel(id: String) = true
        override suspend fun retry(id: String) = true
        override suspend fun delete(id: String, deleteFile: Boolean) = true
    }
    private fun resolver(outcome: ResolveOutcome) = ResolveUrlUseCase(object : MediaRepository {
        override suspend fun analyze(url: String, onStage: (String) -> Unit) = outcome
    })
    private val media = BatchFixture.media
    private val video = BatchFixture.option
    private val audio = video.copy(id = "audio", label = "128k", kind = QualityKind.AUDIO, container = "m4a",
        streams = listOf(BatchFixture.stream.copy(vcodec = "none", height = null, abr = 128)))

    @Test fun `share resolves and selects an available audible video without downloading`() = runTest(main.dispatcher) {
        val commands = Commands()
        val vm = ShareDownloadViewModel("Watch https://example.com/post", resolver(ResolveOutcome.Success(media)), commands)
        advanceUntilIdle()
        assertEquals(video, vm.state.value.selected)
        assertTrue(vm.state.value.canDownload)
        assertTrue(commands.added.isEmpty())
    }
    @Test fun `audio choices only appear when offered and download is guarded against repeated taps`() = runTest(main.dispatcher) {
        val commands = Commands()
        val vm = ShareDownloadViewModel(media.sourceUrl, resolver(ResolveOutcome.Success(media.copy(options = listOf(video, audio)))), commands)
        advanceUntilIdle(); vm.selectKind(QualityKind.AUDIO)
        assertEquals(listOf(audio), vm.state.value.options)
        vm.download(); vm.download(); advanceUntilIdle(); vm.download()
        assertEquals(listOf(audio), commands.added)
        assertNotNull(vm.state.value.receipt)
    }
    @Test fun `unavailable media types and formats cannot be selected`() = runTest(main.dispatcher) {
        val vm = ShareDownloadViewModel(media.sourceUrl, resolver(ResolveOutcome.Success(media)), Commands())
        advanceUntilIdle(); vm.selectKind(QualityKind.AUDIO); vm.select("invented")
        assertEquals(video, vm.state.value.selected)
        assertEquals(QualityKind.VIDEO, vm.state.value.kind)
    }
    @Test fun `silent video requires explicit selection and consent`() = runTest(main.dispatcher) {
        val silent = video.copy(streams = listOf(BatchFixture.stream.copy(acodec = "none")))
        val vm = ShareDownloadViewModel(media.sourceUrl, resolver(ResolveOutcome.Success(media.copy(options = listOf(silent)))), Commands())
        advanceUntilIdle(); assertNull(vm.state.value.selected)
        vm.select(silent.id); assertFalse(vm.state.value.canDownload)
        vm.confirmSilent(true); assertTrue(vm.state.value.canDownload)
    }
    @Test fun `invalid share does not reach repository`() = runTest(main.dispatcher) {
        var calls = 0
        val resolver = ResolveUrlUseCase(object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome { calls++; error("unexpected network") }
        })
        for (input in listOf("not a link", "http://127.0.0.1/file", "x".repeat(128 * 1024 + 1))) {
            val vm = ShareDownloadViewModel(input, resolver, Commands()); advanceUntilIdle()
            assertNotNull(vm.state.value.error); assertFalse(vm.state.value.canDownload)
        }
        assertEquals(0, calls)
    }
    @Test fun `failed handoff stays open and can retry while duplicate saved result finishes`() = runTest(main.dispatcher) {
        val commands = Commands().apply { fail = true }
        val vm = ShareDownloadViewModel(media.sourceUrl, resolver(ResolveOutcome.Success(media)), commands)
        advanceUntilIdle(); vm.download(); advanceUntilIdle()
        assertNull(vm.state.value.receipt); assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.canDownload)
        commands.fail = false; commands.disposition = EnqueueDisposition.SAVED
        vm.download(); advanceUntilIdle()
        assertEquals(EnqueueDisposition.SAVED, vm.state.value.receipt?.disposition)
    }
    @Test fun `cancelling checking discards late extractor result`() = runTest(main.dispatcher) {
        val resolver = ResolveUrlUseCase(object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
                withContext(NonCancellable) { delay(100) }
                return ResolveOutcome.Success(media)
            }
        })
        val commands = Commands()
        val vm = ShareDownloadViewModel(media.sourceUrl, resolver, commands)
        runCurrent(); vm.cancelCheck(); advanceUntilIdle()
        assertNull(vm.state.value.media); assertTrue(commands.added.isEmpty())
    }
    @Test fun `source change cannot show stale result from previous shared link`() = runTest(main.dispatcher) {
        val first = "https://example.com/first"
        val second = "https://example.com/second"
        val resolver = ResolveUrlUseCase(object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
                if (url == first) withContext(NonCancellable) { delay(100) }
                return ResolveOutcome.Success(media.copy(sourceUrl = url))
            }
        })
        val vm = ShareDownloadViewModel("$first\n$second", resolver, Commands())
        runCurrent(); vm.check(second); advanceUntilIdle()
        assertEquals(second, vm.state.value.media?.sourceUrl)
    }
    @Test fun `image original is downloadable without pretending to offer audio or video`() = runTest(main.dispatcher) {
        val file = video.copy(kind = QualityKind.FILE, id = "image", container = "webp")
        val vm = ShareDownloadViewModel(media.sourceUrl, resolver(ResolveOutcome.Success(media.copy(mediaType = MediaType.IMAGE, options = listOf(file)))), Commands())
        advanceUntilIdle(); assertEquals(file, vm.state.value.selected)
        assertEquals(listOf(file), vm.state.value.options)
        assertTrue(vm.state.value.canDownload)
    }
}
