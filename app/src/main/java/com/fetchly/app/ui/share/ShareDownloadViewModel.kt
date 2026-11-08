package com.fetchly.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.download.EnqueueReceipt
import com.fetchly.app.data.repository.ResolveErrors
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.domain.batch.BatchLinks
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import com.fetchly.app.ui.result.ResultViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ShareDownloadState(
    val urls: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val media: MediaInfo? = null,
    val kind: QualityKind? = null,
    val selectedId: String? = null,
    val videoContainer: String = "mp4",
    val checking: Boolean = false,
    val stage: String = "Checking the link…",
    val adding: Boolean = false,
    val error: String? = null,
    val silentConfirmed: Boolean = false,
    val receipt: EnqueueReceipt? = null,
) {
    val options: List<QualityOption> get() {
        val all = media?.options.orEmpty().filter { it.kind == kind }
        return if (kind == QualityKind.VIDEO) all.filter { it.container == videoContainer }.ifEmpty { all } else all
    }
    val selected: QualityOption? get() = options.firstOrNull { it.id == selectedId }
    val canDownload: Boolean get() = !checking && !adding && receipt == null && selected != null &&
        (selected?.silentVideo != true || silentConfirmed)
}

/** Independent from the full app's ResolveSession: sharing never changes its current screen. */
class ShareDownloadViewModel(
    text: String,
    private val resolver: ResolveUrlUseCase,
    private val commands: DownloadCommands,
) : ViewModel() {
    private val mutable = MutableStateFlow(ShareDownloadState())
    val state = mutable.asStateFlow()
    private var checkJob: Job? = null
    private var generation = 0

    init {
        val parsed = BatchLinks.parse(text)
        mutable.value = ShareDownloadState(urls = parsed.urls, error = when {
            parsed.overLimit -> "This share contains too much text. Share one post link instead."
            parsed.urls.isEmpty() -> "No public media link was shared. Use the post's Share link or Copy link option."
            else -> null
        })
        if (mutable.value.error == null) check(parsed.urls.first())
    }

    fun retry() { state.value.sourceUrl?.let(::check) }

    fun check(url: String) {
        if (url !in state.value.urls || state.value.adding || state.value.receipt != null) return
        checkJob?.cancel()
        val token = ++generation
        mutable.update { it.copy(sourceUrl = url, media = null, kind = null, selectedId = null,
            checking = true, error = null, silentConfirmed = false, stage = "Checking the link…") }
        checkJob = viewModelScope.launch {
            val result = try {
                resolver(url) { stage -> if (token == generation) mutable.update { it.copy(stage = stage) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { ResolveOutcome.Failed(ResolveErrors.messageFor(error.message), error.message) }
            if (token != generation) return@launch
            when (result) {
                is ResolveOutcome.Failed -> mutable.update { it.copy(checking = false, error = result.message) }
                is ResolveOutcome.Success -> {
                    val media = result.info
                    val kind = media.options.firstOrNull { it.kind == QualityKind.VIDEO && !it.silentVideo }?.kind
                        ?: media.options.firstOrNull { it.kind == QualityKind.AUDIO }?.kind
                        ?: media.options.firstOrNull()?.kind
                    val options = media.options.filter { it.kind == kind }.let { all ->
                        if (kind == QualityKind.VIDEO) all.filter { it.container == "mp4" }.ifEmpty { all } else all
                    }
                    mutable.update { it.copy(checking = false, media = media, kind = kind,
                        selectedId = ResultViewModel.preselect(options, DefaultQuality.BEST),
                        error = if (options.isEmpty()) "The source returned no downloadable formats." else null) }
                }
            }
        }
    }

    fun selectKind(kind: QualityKind) {
        val current = state.value
        if (current.adding || current.receipt != null) return
        val options = current.media?.options.orEmpty().filter { it.kind == kind }.let { all ->
            if (kind == QualityKind.VIDEO) all.filter { it.container == "mp4" }.ifEmpty { all } else all
        }
        if (options.isEmpty()) return
        mutable.update { it.copy(kind = kind, videoContainer = "mp4", selectedId = ResultViewModel.preselect(options, DefaultQuality.BEST), silentConfirmed = false, error = null) }
    }
    fun selectContainer(container: String) {
        val current = state.value
        if (current.adding || current.receipt != null || container !in listOf("mp4", "mkv")) return
        val options = current.media?.options.orEmpty().filter { it.kind == QualityKind.VIDEO && it.container == container }
        if (options.isEmpty()) return
        val selected = options.firstOrNull { it.label == current.selected?.label } ?: options.first()
        mutable.update { it.copy(videoContainer = container, selectedId = selected.id, silentConfirmed = false) }
    }

    fun select(id: String) {
        if (state.value.adding || state.value.receipt != null || state.value.options.none { it.id == id }) return
        mutable.update { it.copy(selectedId = id, silentConfirmed = false, error = null) }
    }
    fun confirmSilent(value: Boolean) { if (!state.value.adding) mutable.update { it.copy(silentConfirmed = value) } }
    fun cancelCheck() { generation++; checkJob?.cancel(); mutable.update { it.copy(checking = false) } }

    fun download() {
        val snapshot = state.value
        if (!snapshot.canDownload) return
        val media = snapshot.media ?: return
        val option = snapshot.selected ?: return
        mutable.update { it.copy(adding = true, error = null) }
        viewModelScope.launch {
            try {
                val receipt = commands.enqueueWithReceipt(media, option)
                mutable.update { it.copy(adding = false, receipt = receipt) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(adding = false, error = "Couldn't queue the download. Check storage and try again.") } }
        }
    }
}
