package com.fetchly.app.ui.downloads

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.db.DownloadEntity
import com.fetchly.app.data.db.HistoryDao
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.data.db.toDomain
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.download.DownloadWork
import com.fetchly.app.data.download.DownloadWorks
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DownloadsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeDao : HistoryDao {
        val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())

        override fun observeAll(): Flow<List<DownloadEntity>> = rows

        override suspend fun getById(id: String): DownloadEntity? =
            rows.value.firstOrNull { it.id == id }

        override suspend fun upsert(entity: DownloadEntity) {
            rows.value = rows.value.filterNot { it.id == entity.id } + entity
        }

        override suspend fun claim(id: String, updatedAt: Long): Int = 1

        override suspend fun finish(
            id: String,
            status: String,
            message: String?,
            raw: String?,
            updatedAt: Long,
        ): Int = 1

        override suspend fun setStatus(
            id: String,
            status: String,
            message: String?,
            raw: String?,
            updatedAt: Long,
        ) {
            rows.value = rows.value.map { row ->
                if (row.id == id) {
                    row.copy(status = status, errorMessage = message, errorRaw = raw, updatedAt = updatedAt)
                } else {
                    row
                }
            }
        }

        override suspend fun setCompleted(
            id: String,
            uri: String,
            name: String,
            path: String,
            size: Long?,
            updatedAt: Long,
        ): Int = 1

        override suspend fun cancelIfActive(id: String, updatedAt: Long): Int = 1

        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private class FakeCommands : DownloadCommands {
        val cancelled = mutableListOf<String>()
        val retried = mutableListOf<String>()
        val deleted = mutableListOf<Pair<String, Boolean>>()

        override suspend fun enqueue(media: MediaInfo, option: QualityOption): String = "id"

        override suspend fun cancel(id: String): Boolean {
            cancelled += id
            return true
        }

        override suspend fun retry(id: String): Boolean {
            retried += id
            return true
        }

        override suspend fun delete(id: String, deleteFile: Boolean): Boolean {
            deleted += id to deleteFile
            return true
        }
    }

    private class FakeWorks(private val flow: MutableStateFlow<List<DownloadWork>>) : DownloadWorks {
        override fun observe(): Flow<List<DownloadWork>> = flow
    }

    private fun entity(id: String, status: String, updatedAt: Long = 1_000L) = DownloadEntity(
        id = id,
        title = "Video $id",
        uploader = null,
        sourceUrl = "https://youtu.be/$id",
        platform = "YOUTUBE",
        mediaType = "VIDEO",
        thumbnailUrl = null,
        durationSeconds = 60,
        optionLabel = "1080p",
        kind = "VIDEO",
        container = "mp4",
        sizeBytes = null,
        signatureJson = "",
        streamUrlsJson = "{}",
        workName = "fetchly_$id",
        status = status,
        errorMessage = null,
        errorRaw = null,
        outputUri = null,
        outputName = null,
        outputPath = null,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `map splits unfinished work from finished history`() {
        val entries = listOf(
            entity("a", "RUNNING").toDomain(),
            entity("b", "COMPLETED").toDomain(),
            entity("c", "QUEUED").toDomain(),
            entity("d", "FAILED").toDomain(),
        )
        val works = mapOf("a" to DownloadWork("a", running = true, stage = "Downloading video…", bytes = 25, total = 100, speedBytesPerSecond = 0))

        val state = DownloadsViewModel.map(entries, works)

        assertEquals(listOf("a", "c"), state.active.map { it.entry.id })
        assertEquals(listOf("b", "d"), state.history.map { it.id })
    }

    @Test
    fun `active rows carry the stage and a percent only when the total is known`() {
        val running = DownloadsViewModel.map(
            listOf(entity("a", "RUNNING").toDomain()),
            mapOf("a" to DownloadWork("a", true, "Downloading video…", 1, 4, 512)),
        ).active.single()
        assertEquals("Downloading video…", running.stageText)
        assertEquals(25, running.percent)

        val unknownTotal = DownloadsViewModel.map(
            listOf(entity("b", "RUNNING").toDomain()),
            mapOf("b" to DownloadWork("b", true, "Downloading video…", 1, 0, 0)),
        ).active.single()
        assertNull(unknownTotal.percent)

        val noWork = DownloadsViewModel.map(
            listOf(entity("c", "QUEUED").toDomain()),
            emptyMap(),
        ).active.single()
        assertEquals("Waiting…", noWork.stageText)
        assertNull(noWork.percent)
    }

    @Test
    fun `the state combines history rows with live work progress`() =
        runTest(mainDispatcher.dispatcher) {
            val dao = FakeDao()
            dao.upsert(entity("a", "RUNNING"))
            val works = MutableStateFlow(
                listOf(DownloadWork("a", true, "Downloading audio…", 50, 200, 99)),
            )
            val viewModel = DownloadsViewModel(HistoryRepository(dao), FakeCommands(), FakeWorks(works))

            backgroundScope.launch { viewModel.state.collect {} }
            advanceUntilIdle()

            val active = viewModel.state.value.active.single()
            assertEquals("Downloading audio…", active.stageText)
            assertEquals(25, active.percent)

            works.value = listOf(DownloadWork("a", true, "Merging video and audio…", 200, 200, 0))
            advanceUntilIdle()
            assertEquals(100, viewModel.state.value.active.single().percent)
        }

    @Test
    fun `join prefers a live job over a finished leftover from a retry`() {
        val old = DownloadWork(
            downloadId = "a", running = false, stage = null,
            bytes = 5, total = 10, speedBytesPerSecond = 0, finished = true,
        )
        val fresh = DownloadWork(
            downloadId = "a", running = false, stage = null,
            bytes = 0, total = 0, speedBytesPerSecond = 0, finished = false,
        )
        assertEquals(fresh, DownloadsViewModel.joinWorks(listOf(old, fresh)).getValue("a"))

        val running = fresh.copy(running = true, stage = "Downloading video…")
        assertEquals(
            running,
            DownloadsViewModel.joinWorks(listOf(old, fresh, running)).getValue("a"),
        )
    }

    @Test
    fun `actions go straight to the queue`() = runTest(mainDispatcher.dispatcher) {
        val commands = FakeCommands()
        val viewModel = DownloadsViewModel(
            HistoryRepository(FakeDao()),
            commands,
            FakeWorks(MutableStateFlow(emptyList())),
        )

        viewModel.cancel("a")
        viewModel.retry("b")
        viewModel.delete("c")
        advanceUntilIdle()

        assertEquals(listOf("a"), commands.cancelled)
        assertEquals(listOf("b"), commands.retried)
        assertEquals(listOf("c" to true), commands.deleted)
    }
}
