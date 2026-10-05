package com.fetchly.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalyzeRequest(val url: String)

@Serializable
data class AnalyzeResponse(
    val success: Boolean,
    val platform: String = "unknown",
    val title: String = "",
    val author: String? = null,
    val thumbnail: String? = null,
    @SerialName("mediaType") val mediaType: String = "unknown",
    val duration: Long? = null,
    val formats: List<FormatDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class FormatDto(
    val id: String,
    val quality: String,
    val container: String,
    val size: Long? = null,
    @SerialName("downloadUrl") val downloadUrl: String = "",
    @SerialName("isAudioOnly") val isAudioOnly: Boolean = false,
    // Optional metadata — only present when the source genuinely provides it.
    @SerialName("bitrateKbps") val bitrateKbps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("hasAudio") val hasAudio: Boolean? = null,
)
