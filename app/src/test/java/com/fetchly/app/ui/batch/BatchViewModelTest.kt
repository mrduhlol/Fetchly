package com.fetchly.app.ui.batch

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.repository.*
import com.fetchly.app.domain.batch.BatchFixture
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import com.fetchly.app.domain.session.ResolveSession
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BatchViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Commands : DownloadCommands {
        val titles = mutableListOf<String>()
        var fails = true
        override suspend fun enqueue(media: MediaInfo, option: QualityOption): String {
            if (media.sourceUrl.endsWith("/b") && fails) throw java.io.IOException()
            titles += media.title; return media.sourceUrl
        }
        override suspend fun cancel(id: String) = true
        override suspend fun retry(id: String) = true
        override suspend fun delete(id: String, deleteFile: Boolean) = true
    }
    @Test fun `partial checks and partial queue failures preserve successful items on retry`() = runTest(main.dispatcher) {
        val commands = Commands()
        val repo = object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome =
                if (url.endsWith("/bad")) ResolveOutcome.Failed("Unavailable", null)
                else ResolveOutcome.Success(BatchFixture.media.copy(sourceUrl = url))
        }
        val vm = BatchViewModel(ResolveUrlUseCase(repo), ResolveSession(), commands)
        vm.input("https://example.com/a https://example.com/b https://example.com/bad")
        vm.check(); advanceUntilIdle()
        assertEquals(2, vm.state.value.rows.count { it.media != null })
        vm.name("https://example.com/a", "My/name")
        vm.download(); vm.download(); advanceUntilIdle()
        assertEquals(listOf("My name"), commands.titles)
        assertEquals(1, vm.state.value.rows.count { it.added })
        commands.fails = false
        vm.download(); advanceUntilIdle()
        assertEquals(listOf("My name", "Sample"), commands.titles)
        assertEquals(2, vm.state.value.rows.count { it.added })
    }
    @Test fun `cancelled checks discard a late non cooperative result`() = runTest(main.dispatcher) {
        val repo = object : MediaRepository {
            override suspend fun analyze(url: String, onStage: (String) -> Unit): ResolveOutcome {
                withContext(NonCancellable) { delay(100) }
                return ResolveOutcome.Success(BatchFixture.media)
            }
        }
        val vm = BatchViewModel(ResolveUrlUseCase(repo), ResolveSession(), Commands())
        vm.input("https://example.com/a"); vm.check(); runCurrent(); vm.cancel(); advanceUntilIdle()
        assertFalse(vm.state.value.checking)
        assertNull(vm.state.value.rows.single().media)
    }
}
