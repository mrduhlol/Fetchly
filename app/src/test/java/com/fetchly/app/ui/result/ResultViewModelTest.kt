package com.fetchly.app.ui.result

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import com.fetchly.app.domain.session.ResolveSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResultViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeCommands(var failEnqueue: Boolean = false) : DownloadCommands {
        val enqueued = mutableListOf<Pair<MediaInfo, QualityOption>>()

        override suspend fun enqueue(media: MediaInfo, option: QualityOption): String {
            if (failEnqueue) throw java.io.IOException("database is full")
            enqueued += media to option
            return "id-${enqueued.size}"
        }

        override suspend fun cancel(id: String) = true

        override suspend fun retry(id: String) = true

        override suspend fun delete(id: String, deleteFile: Boolean) = true
    }

    private val stream = StreamRef(
        formatId = "137",
        url = "https://cdn.example.invalid/137",
        ext = "mp4",
        vcodec = "avc1.640028",
        acodec = "none",
        height = 1080,
        abr = null,
        sizeBytes = null,
        protocol = "https",
    )

    private fun option(id: String, label: String, kind: QualityKind) = QualityOption(
        id = id,
        label = label,
        kind = kind,
        container = if (kind == QualityKind.AUDIO) "m4a" else "mp4",
        streams = listOf(stream),
        needsMerge = false,
        sizeBytes = null,
    )

    private val media = MediaInfo(
        sourceUrl = "https://youtu.be/abc",
        platform = Platform.YOUTUBE,
        title = "Test video",
        uploader = "Uploader",
        durationSeconds = 60,
        thumbnailUrl = null,
        mediaType = MediaType.VIDEO,
        options = listOf(
            option("v1080", "1080p", QualityKind.VIDEO),
            option("audio", "Audio", QualityKind.AUDIO),
        ),
    )

    private fun viewModel(
        choice: DefaultQuality,
        commands: FakeCommands = FakeCommands(),
    ): Pair<ResultViewModel, FakeCommands> {
        val session = ResolveSession().apply { hold(media) }
        return ResultViewModel(session, flowOf(choice), commands) to commands
    }

    @Test
    fun `best default preselects the top option`() = runTest(mainDispatcher.dispatcher) {
        val (viewModel, _) = viewModel(DefaultQuality.BEST)
        advanceUntilIdle()

        assertEquals("v1080", viewModel.state.value.selectedId)
    }

    @Test
    fun `ask default leaves nothing preselected`() = runTest(mainDispatcher.dispatcher) {
        val (viewModel, _) = viewModel(DefaultQuality.ASK)
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedId)
    }

    @Test
    fun `download enqueues the selected option and flags the move`() =
        runTest(mainDispatcher.dispatcher) {
            val (viewModel, commands) = viewModel(DefaultQuality.ASK)
            advanceUntilIdle()

            viewModel.select("audio")
            viewModel.download()
            advanceUntilIdle()

            assertEquals(1, commands.enqueued.size)
            assertEquals("audio", commands.enqueued.first().second.id)
            assertTrue(viewModel.state.value.queued)
            assertFalse(viewModel.state.value.enqueuing)

            viewModel.consumeQueued()
            assertFalse(viewModel.state.value.queued)
        }

    @Test
    fun `download without a selection does nothing`() = runTest(mainDispatcher.dispatcher) {
        val (viewModel, commands) = viewModel(DefaultQuality.ASK)
        advanceUntilIdle()

        viewModel.download()
        advanceUntilIdle()

        assertTrue(commands.enqueued.isEmpty())
        assertFalse(viewModel.state.value.queued)
    }

    @Test
    fun `preselect picks the top for best and nothing for ask`() {
        val options = media.options

        assertEquals("v1080", ResultViewModel.preselect(options, DefaultQuality.BEST))
        assertNull(ResultViewModel.preselect(options, DefaultQuality.ASK))
        assertNull(ResultViewModel.preselect(emptyList(), DefaultQuality.BEST))
    }
    @Test
    fun `queue failure is recoverable and retry can succeed`() = runTest(mainDispatcher.dispatcher) {
        val commands = FakeCommands(failEnqueue = true)
        val (viewModel, _) = viewModel(DefaultQuality.BEST, commands)
        advanceUntilIdle()
        viewModel.download()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.enqueuing)
        assertFalse(viewModel.state.value.queued)
        assertTrue(viewModel.state.value.error != null)
        commands.failEnqueue = false
        viewModel.download()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.queued)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `repeated taps after queue success do not enqueue twice`() = runTest(mainDispatcher.dispatcher) {
        val (viewModel, commands) = viewModel(DefaultQuality.BEST)
        advanceUntilIdle()
        viewModel.download()
        advanceUntilIdle()
        viewModel.download()
        advanceUntilIdle()
        assertEquals(1, commands.enqueued.size)
    }

}
