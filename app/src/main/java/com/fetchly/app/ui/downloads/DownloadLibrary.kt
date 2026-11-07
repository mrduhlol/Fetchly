package com.fetchly.app.ui.downloads

import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.platform.PlatformDetector

enum class LibraryFilter(val label: String) { ALL("All"), VIDEO("Video"), AUDIO("Audio"), IMAGES("Images"), FAILED("Failed") }

object DownloadLibrary {
    fun filter(entries: List<HistoryEntry>, query: String, filter: LibraryFilter): List<HistoryEntry> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.filter { entry ->
            val text = listOfNotNull(entry.title, entry.outputName, entry.optionLabel,
                PlatformDetector.label(entry.platform)).joinToString(" ")
            words.all { text.contains(it, ignoreCase = true) } && when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.VIDEO -> entry.mediaType == MediaType.VIDEO
                LibraryFilter.AUDIO -> entry.mediaType == MediaType.AUDIO
                LibraryFilter.IMAGES -> entry.mediaType == MediaType.IMAGE
                LibraryFilter.FAILED -> entry.status == DownloadStatus.FAILED
            }
        }.sortedByDescending { it.updatedAt }
    }
}
