package com.fetchly.app.lite

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.download.*
import com.fetchly.app.data.repository.*
import com.fetchly.app.domain.batch.BatchFixture
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiteResolveViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val media = BatchFixture.media
    private class Commands : DownloadCommands {
        val added = mutableListOf<String>()
        var failAt: Int? = null
        override suspend fun enqueue(media: MediaInfo, option: QualityOption): String {
            if (added.size == failAt) throw java.io.IOException()
            added += media.title
            return "id-${added.size}"
        }
        override suspend fun cancel(id: String) = true
        override suspend fun retry(id: String) = true
        override suspend fun delete(id: String, deleteFile: Boolean) = true
    }
    private fun vm(info: MediaInfo = media, commands: Commands = Commands(), ask: Boolean = false) = LiteResolveViewModel(
        ResolveUrlUseCase(object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit) = ResolveOutcome.Success(info)
        }), commands, flowOf(if (ask) DefaultQuality.ASK else DefaultQuality.BEST))

    @Test fun `five photo post downloads only the two selected items`() = runTest(main.dispatcher) {
        val photos = (1..5).map { index -> media.copy(title = "Photo $index", mediaType = MediaType.IMAGE,
            options = listOf(BatchFixture.option.copy(kind = QualityKind.FILE, container = "jpg"))) }
        val commands = Commands()
        val vm = vm(media.copy(items = photos), commands)
        vm.resolve(media.sourceUrl); advanceUntilIdle()
        listOf(0, 2, 4).forEach(vm::toggle)
        assertEquals(setOf(1, 3), vm.state.value.selected)
        vm.download(); vm.download(); advanceUntilIdle()
        assertEquals(listOf("Photo 2", "Photo 4"), commands.added)
        assertTrue(vm.state.value.complete)
    }
    @Test fun `photos with one original format stay downloadable with Ask preference`() = runTest(main.dispatcher) {
        val photo = media.copy(mediaType = MediaType.IMAGE,
            options = listOf(BatchFixture.option.copy(kind = QualityKind.FILE, container = "jpg")))
        val vm = vm(photo, ask = true)
        vm.resolve(media.sourceUrl); advanceUntilIdle()
        assertTrue(vm.state.value.canDownload)
        assertEquals("jpg", vm.state.value.option(0)?.container)
    }
    @Test fun `partial enqueue failure retries only remaining selections`() = runTest(main.dispatcher) {
        val commands = Commands().apply { failAt = 1 }
        val vm = vm(media.copy(items = listOf(media.copy(title = "First"), media.copy(title = "Second"))), commands)
        vm.resolve(media.sourceUrl); advanceUntilIdle(); vm.download(); advanceUntilIdle()
        assertEquals(setOf(1), vm.state.value.selected)
        assertNotNull(vm.state.value.error)
        commands.failAt = null
        vm.download(); advanceUntilIdle()
        assertEquals(listOf("First", "Second"), commands.added)
    }
    @Test fun `ask preference requires a quality choice`() = runTest(main.dispatcher) {
        val vm = vm(ask = true)
        vm.resolve(media.sourceUrl); advanceUntilIdle()
        assertFalse(vm.state.value.canDownload)
        vm.select(media.options.first().id)
        assertTrue(vm.state.value.canDownload)
    }
    @Test fun `audio offers only MP3 conversions and no ZIP`() {
        val audio = BatchFixture.option.copy(id = "m4a", kind = QualityKind.AUDIO, container = "m4a")
        val zip = audio.copy(id = "bulk", kind = QualityKind.FILE, container = "zip")
        val options = LiteFormats.options(media.copy(options = listOf(audio, zip)))
        assertEquals(3, options.size)
        assertTrue(options.all { it.kind == QualityKind.AUDIO && it.container == "mp3" && it.needsMerge })
        assertEquals(listOf("128 kb/s", "192 kb/s", "320 kb/s"), options.map { it.label })
    }
    @Test fun `direct audio also converts instead of exposing an M4A file`() {
        val direct = BatchFixture.option.copy(kind = QualityKind.FILE, container = "m4a")
        val options = LiteFormats.options(media.copy(mediaType = MediaType.AUDIO, options = listOf(direct)))
        assertEquals(3, options.size)
        assertTrue(options.all { it.container == "mp3" })
    }
    @Test fun `silent video requires explicit confirmation`() = runTest(main.dispatcher) {
        val silent = BatchFixture.option.copy(streams = listOf(BatchFixture.stream.copy(acodec = "none")))
        val vm = vm(media.copy(options = listOf(silent)))
        vm.resolve(media.sourceUrl); advanceUntilIdle()
        assertFalse(vm.state.value.canDownload)
        vm.confirmSilent(true)
        assertTrue(vm.state.value.canDownload)
    }
    @Test fun `editing input cancels a stale resolve`() = runTest(main.dispatcher) {
        val vm = LiteResolveViewModel(ResolveUrlUseCase(object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
                delay(1000); return ResolveOutcome.Success(media)
            }
        }), Commands())
        vm.resolve(media.sourceUrl); runCurrent()
        vm.input("https://example.com/new")
        advanceUntilIdle()
        assertTrue(vm.state.value.items.isEmpty())
        assertEquals("https://example.com/new", vm.state.value.input)
    }
    @Test fun `private URLs and multiple links do not reach extraction`() = runTest(main.dispatcher) {
        var calls = 0
        val vm = LiteResolveViewModel(ResolveUrlUseCase(object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome { calls++; error("must not run") }
        }), Commands())
        for (text in listOf("https://127.0.0.1/photo", "https://example.com/a https://example.com/b")) {
            vm.resolve(text); advanceUntilIdle(); assertNotNull(vm.state.value.error)
        }
        assertEquals(0, calls)
    }
}
