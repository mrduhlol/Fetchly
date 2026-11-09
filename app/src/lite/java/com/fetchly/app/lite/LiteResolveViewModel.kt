package com.fetchly.app.lite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.DownloadCommands
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.domain.batch.BatchLinks
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lite displays only MP3 for audio, retaining original streams for conversion. */
object LiteFormats {
    fun options(media: MediaInfo): List<QualityOption> {
        val options = media.options.filter { it.container != "zip" &&
            (it.kind != QualityKind.AUDIO || it.container == "mp3") &&
            !(media.mediaType == MediaType.AUDIO && it.kind == QualityKind.FILE) }
        if (options.any { it.kind == QualityKind.AUDIO }) return options
        val audio = media.options.firstOrNull { it.kind == QualityKind.AUDIO }
            ?: media.options.firstOrNull { media.mediaType == MediaType.AUDIO }
            ?: return options
        return options + listOf(128, 192, 320).map { rate ->
            audio.copy(id = "lite-mp3-$rate", label = "$rate kb/s", kind = QualityKind.AUDIO,
                container = "mp3", needsMerge = true,
                sizeBytes = media.durationSeconds?.let { it * rate * 125L })
        }
    }
}

data class LiteResolveState(
    val input: String = "", val loading: Boolean = false, val stage: String = "",
    val items: List<MediaInfo> = emptyList(), val selected: Set<Int> = emptySet(),
    val choices: Map<Int, String> = emptyMap(), val focused: Int = 0,
    val error: String? = null, val adding: Boolean = false,
    val silentConfirmed: Boolean = false, val complete: Boolean = false,
    val notice: String? = null,
) {
    fun option(index: Int): QualityOption? = items.getOrNull(index)?.options?.firstOrNull { it.id == choices[index] }
    val silent: Boolean get() = selected.any { option(it)?.silentVideo == true }
    val canDownload: Boolean get() = selected.isNotEmpty() && selected.all { option(it) != null } &&
        !loading && !adding && !complete && (!silent || silentConfirmed)
}

/** One resolve flow is used both in Home and the floating Android share activity. */
class LiteResolveViewModel(
    private val resolver: ResolveUrlUseCase,
    private val commands: DownloadCommands,
    private val quality: Flow<DefaultQuality> = flowOf(DefaultQuality.BEST),
) : ViewModel() {
    private val mutable = MutableStateFlow(LiteResolveState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    fun input(text: String) {
        if (state.value.adding) return
        generation++
        job?.cancel()
        mutable.value = LiteResolveState(input = text.take(128 * 1024 + 1))
    }

    fun resolve(text: String = state.value.input) {
        if (state.value.adding) return
        job?.cancel()
        val token = ++generation
        val parsed = BatchLinks.parse(text)
        if (parsed.overLimit || parsed.urls.size != 1) {
            mutable.value = LiteResolveState(input = text, error = "Paste one public post or media link. A post can contain multiple items.")
            return
        }
        mutable.value = LiteResolveState(input = text, loading = true, stage = "Checking the link…")
        job = viewModelScope.launch {
            try {
                when (val result = resolver(parsed.urls.single()) { stage ->
                    if (token == generation) mutable.update { it.copy(stage = stage) }
                }) {
                    is ResolveOutcome.Failed -> if (token == generation) mutable.update { it.copy(loading = false, error = result.message) }
                    is ResolveOutcome.Success -> if (token == generation) {
                        val source = result.info.items.ifEmpty { listOf(result.info) }
                        val items = source.map { it.copy(options = LiteFormats.options(it)) }.filter { it.options.isNotEmpty() }
                        val best = quality.first() == DefaultQuality.BEST
                        mutable.update { it.copy(loading = false, items = items,
                            selected = items.indices.toSet(), choices = items.mapIndexed { index, info ->
                                index to (info.options.firstOrNull { option -> !option.silentVideo } ?: info.options.first()).id
                            }.toMap().takeIf { best }.orEmpty(), error = if (items.isEmpty()) "No downloadable media was returned by this source." else null,
                            notice = if (items.size < source.size) "${items.size} of ${source.size} returned items are downloadable." else null) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) mutable.update { it.copy(loading = false, error = "Couldn't check the link. Check your connection and retry.") } }
        }
    }

    fun focus(index: Int) { if (!state.value.adding) mutable.update { it.copy(focused = index) } }
    fun toggle(index: Int) {
        if (state.value.adding || index !in state.value.items.indices) return
        mutable.update { it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index, silentConfirmed = false) }
    }
    fun select(id: String) {
        val s = state.value
        if (s.adding || s.items.getOrNull(s.focused)?.options?.none { it.id == id } != false) return
        mutable.update { it.copy(choices = it.choices + (it.focused to id), silentConfirmed = false) }
    }
    fun confirmSilent(confirmed: Boolean) { mutable.update { it.copy(silentConfirmed = confirmed) } }
    fun dismiss() { if (!state.value.adding) { generation++; job?.cancel(); mutable.value = LiteResolveState(input = state.value.input) } }

    fun download() {
        val snapshot = state.value
        if (!snapshot.canDownload) return
        mutable.update { it.copy(adding = true, error = null) }
        viewModelScope.launch {
            val remaining = snapshot.selected.toMutableSet()
            try {
                for (index in snapshot.selected.sorted()) {
                    commands.enqueueWithReceipt(snapshot.items[index], requireNotNull(snapshot.option(index)))
                    remaining.remove(index)
                    mutable.update { it.copy(selected = remaining.toSet()) }
                }
                mutable.update { it.copy(adding = false, complete = true, notice = "Added to downloads.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(adding = false, error = "Couldn't queue ${remaining.size} item(s). Retry to add the remaining items.") } }
        }
    }
}
