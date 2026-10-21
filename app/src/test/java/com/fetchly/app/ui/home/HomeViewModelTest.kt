package com.fetchly.app.ui.home

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.repository.MediaRepository
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import com.fetchly.app.domain.session.IncomingLinks
import com.fetchly.app.domain.session.ResolveSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeRepository(
        private val outcome: ResolveOutcome,
        private val stages: List<String> = emptyList(),
    ) : MediaRepository {

        var calls = 0
            private set

        override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
            calls++
            stages.forEach(onStage)
            return outcome
        }
    }

    private val info = MediaInfo(
        sourceUrl = "https://youtu.be/abc",
        platform = Platform.YOUTUBE,
        title = "Test video",
        uploader = null,
        durationSeconds = 60,
        thumbnailUrl = null,
        mediaType = MediaType.VIDEO,
        options = emptyList(),
    )

    private fun viewModel(
        repository: MediaRepository,
        incoming: IncomingLinks = IncomingLinks(),
    ): Pair<HomeViewModel, ResolveSession> {
        val session = ResolveSession()
        val viewModel = HomeViewModel(ResolveUrlUseCase(repository), session, incoming)
        return viewModel to session
    }

    @Test
    fun `success holds the info in the session and flags navigation`() =
        runTest(mainDispatcher.dispatcher) {
            val repository = FakeRepository(ResolveOutcome.Success(info))
            val (viewModel, session) = viewModel(repository)

            viewModel.onInputChange("https://youtu.be/abc")
            viewModel.fetch()
            advanceUntilIdle()

            assertEquals(info, session.info.value)
            assertTrue(viewModel.state.value.success)
            assertNull(viewModel.state.value.status)
            assertNull(viewModel.state.value.error)
            assertEquals(1, repository.calls)
        }

    @Test
    fun `failure shows the sentence and never flags navigation`() =
        runTest(mainDispatcher.dispatcher) {
            val repository = FakeRepository(
                ResolveOutcome.Failed("Sign-in required.", raw = "ERROR: you need to sign in")
            )
            val (viewModel, session) = viewModel(repository)

            viewModel.onInputChange("https://youtu.be/abc")
            viewModel.fetch()
            advanceUntilIdle()

            assertEquals("Sign-in required.", viewModel.state.value.error)
            assertFalse(viewModel.state.value.success)
            assertNull(session.info.value)
        }

    @Test
    fun `an invalid link fails locally and never reaches the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val repository = FakeRepository(ResolveOutcome.Success(info))
            val (viewModel, _) = viewModel(repository)

            viewModel.onInputChange("not a link")
            viewModel.fetch()
            advanceUntilIdle()

            assertEquals(0, repository.calls)
            assertEquals(
                "That doesn't look like a link — it contains spaces.",
                viewModel.state.value.error,
            )
        }

    @Test
    fun `the status line follows the repository stages`() =
        runTest(mainDispatcher.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = object : MediaRepository {
                override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
                    onStage("Checking the link…")
                    gate.await()
                    onStage("Asking yt-dlp…")
                    return ResolveOutcome.Success(info)
                }
            }
            val (viewModel, _) = viewModel(repository)

            viewModel.onInputChange("https://youtu.be/abc")
            viewModel.fetch()

            runCurrent()
            assertEquals("Checking the link…", viewModel.state.value.status)
            assertTrue(viewModel.state.value.analyzing)

            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(viewModel.state.value.status)
            assertTrue(viewModel.state.value.success)
        }

    @Test
    fun `shared text prefills the link and can be shared again`() =
        runTest(mainDispatcher.dispatcher) {
            val incoming = IncomingLinks()
            val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)), incoming)

            incoming.share("Check this out https://youtu.be/abc !")
            advanceUntilIdle()

            assertEquals("https://youtu.be/abc", viewModel.state.value.input)
            assertNull(incoming.text.value)

            incoming.share("Check this out https://youtu.be/abc !")
            advanceUntilIdle()

            assertEquals("https://youtu.be/abc", viewModel.state.value.input)
        }

    @Test
    fun `clipboard links become a chip and only fill the field on accept`() =
        runTest(mainDispatcher.dispatcher) {
            val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)))

            viewModel.offerClipboard("look at https://youtu.be/abc now")

            assertEquals("https://youtu.be/abc", viewModel.state.value.suggestion)
            assertEquals("", viewModel.state.value.input)

            viewModel.acceptSuggestion()

            assertEquals("https://youtu.be/abc", viewModel.state.value.input)
            assertNull(viewModel.state.value.suggestion)
        }

    @Test
    fun `typing a known platform link detects it and typing otherwise clears it`() =
        runTest(mainDispatcher.dispatcher) {
            val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)))

            viewModel.onInputChange("https://www.youtube.com/watch?v=abc")
            assertEquals(Platform.YOUTUBE, viewModel.state.value.detected)

            viewModel.onInputChange("https://example.com/some/page")
            assertNull(viewModel.state.value.detected)
        }

    @Test
    fun `a share without a link says so instead of vanishing`() =
        runTest(mainDispatcher.dispatcher) {
            val incoming = IncomingLinks()
            val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)), incoming)

            incoming.share("look at this cool cat")
            advanceUntilIdle()

            assertEquals("That share didn't contain a link.", viewModel.state.value.error)
        }

    @Test
    fun `a shared local link is refused with the real reason`() =
        runTest(mainDispatcher.dispatcher) {
            val incoming = IncomingLinks()
            val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)), incoming)

            incoming.share("try http://127.0.0.1/admin")
            advanceUntilIdle()

            assertEquals("Local network links aren't supported.", viewModel.state.value.error)
        }

    @Test
    fun `a share with a refused link and a valid one prefills the valid link`() =
        runTest(mainDispatcher.dispatcher) {
            val incoming = IncomingLinks()
            val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)), incoming)

            incoming.share("bad http://127.0.0.1/x good https://youtu.be/ok123")
            advanceUntilIdle()

            assertEquals("https://youtu.be/ok123", viewModel.state.value.input)
            assertNull(viewModel.state.value.error)
        }
    @Test
    fun `pasted social share text uses its link`() = runTest(mainDispatcher.dispatcher) {
        val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)))
        viewModel.onInputChange("Watch this video https://youtu.be/abc !")
        assertEquals("https://youtu.be/abc", viewModel.state.value.input)
        assertEquals(Platform.YOUTUBE, viewModel.state.value.detected)
        viewModel.fetch()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.success)
    }

    @Test
    fun `unexpected resolver failure releases loading state and allows retry`() =
        runTest(mainDispatcher.dispatcher) {
            val repository = object : MediaRepository {
                override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
                    throw java.io.IOException("Connection timed out")
                }
            }
            val (viewModel, _) = viewModel(repository)
            viewModel.onInputChange("https://youtu.be/abc")
            viewModel.fetch()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.analyzing)
            assertTrue(viewModel.state.value.canFetch)
            assertTrue(viewModel.state.value.error!!.contains("connection"))
        }

    @Test
    fun `typing query punctuation preserves the editable URL`() = runTest(mainDispatcher.dispatcher) {
        val (viewModel, _) = viewModel(FakeRepository(ResolveOutcome.Success(info)))
        viewModel.onInputChange("https://www.youtube.com/watch?")
        assertEquals("https://www.youtube.com/watch?", viewModel.state.value.input)
    }

}
