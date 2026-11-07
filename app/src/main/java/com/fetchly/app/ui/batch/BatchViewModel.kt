package com.fetchly.app.ui.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.domain.batch.*
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import com.fetchly.app.domain.session.ResolveSession
import com.fetchly.app.domain.util.Filenames
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BatchRow(val url: String, val media: MediaInfo? = null, val option: QualityOption? = null,
    val name: String = "", val selected: Boolean = true, val message: String = "Waiting to check", val added: Boolean = false)
data class BatchState(val input: String = "", val rows: List<BatchRow> = emptyList(),
    val quality: BatchQuality = BatchQuality.BEST, val checking: Boolean = false,
    val adding: Boolean = false, val message: String? = null)
class BatchViewModel(private val resolver: ResolveUrlUseCase, session: ResolveSession,
    private val commands: DownloadCommands) : ViewModel() {
    private val mutable = MutableStateFlow(BatchState(input = session.batchInput.value.orEmpty()))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    init { session.consumeBatchInput() }
    fun input(value: String) { if (!state.value.checking && !state.value.adding) mutable.update { it.copy(input = value.take(BatchLinks.MAX_INPUT + 1)) } }
    private fun row(url: String, change: (BatchRow) -> BatchRow) { mutable.update { it.copy(rows = it.rows.map { r -> if (r.url == url) change(r) else r }) } }
    fun select(url: String, value: Boolean) { if (!state.value.adding) row(url) { it.copy(selected = value) } }
    fun name(url: String, value: String) { if (!state.value.adding) row(url) { it.copy(name = value.take(240)) } }
    fun option(url: String, id: String) { if (!state.value.adding) row(url) { r -> r.copy(option = r.media?.options?.firstOrNull { it.id == id && !it.silentVideo }) } }
    fun quality(value: BatchQuality) { if (!state.value.adding) mutable.update { s -> s.copy(quality = value, rows = s.rows.map { r -> if (r.added) r else r.copy(option = r.media?.let(value::choose)) }) } }
    fun cancel() { generation++; job?.cancel(); mutable.update { it.copy(checking = false) } }
    fun check() {
        if (state.value.checking || state.value.adding) return
        val parsed = BatchLinks.parse(state.value.input)
        if (parsed.overLimit || parsed.urls.isEmpty()) {
            mutable.update { it.copy(message = if (parsed.overLimit) "Use at most 50 links per batch." else "Paste public HTTP or HTTPS links to continue.") }; return
        }
        val old = state.value.rows.associateBy { it.url }
        mutable.update { it.copy(checking = true, rows = parsed.urls.map { url -> old[url] ?: BatchRow(url) },
            message = "${parsed.duplicates} duplicate links skipped · ${parsed.rejected} unsupported links skipped") }
        val token = ++generation
        job = viewModelScope.launch {
            try {
                for (url in parsed.urls) {
                    if (state.value.rows.first { it.url == url }.media != null) continue
                    row(url) { it.copy(message = "Checking link…") }
                    val result = try { resolver(url) { stage -> if (token == generation) row(url) { it.copy(message = stage) } } }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { ResolveOutcome.Failed("Couldn't check this link. Try again.", null) }
                    if (token != generation) return@launch
                    row(url) { r -> when (result) {
                        is ResolveOutcome.Success -> r.copy(media = result.info, name = result.info.title,
                            option = state.value.quality.choose(result.info), message = "Ready")
                        is ResolveOutcome.Failed -> r.copy(message = result.message)
                    } }
                }
            } finally { if (token == generation) mutable.update { it.copy(checking = false) } }
        }
    }
    fun download() {
        val current = state.value
        if (current.adding || current.checking) return
        val ready = current.rows.filter { it.selected && !it.added && it.media != null && it.option != null }
        if (ready.isEmpty()) return
        mutable.update { it.copy(adding = true) }
        viewModelScope.launch {
            try { for (r in ready) {
                try {
                    val receipt = commands.enqueueWithReceipt(r.media!!.copy(title = Filenames.sanitize(r.name.ifBlank { r.media.title })), r.option!!)
                    row(r.url) { it.copy(added = true, selected = false, message = receipt.disposition.message) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { row(r.url) { it.copy(message = "Couldn't add this download. Try again.") } }
            } } finally { mutable.update { it.copy(adding = false) } }
        }
    }
}
