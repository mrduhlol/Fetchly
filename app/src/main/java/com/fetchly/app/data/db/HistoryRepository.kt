package com.fetchly.app.data.db

import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.StreamRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Domain-facing history: database rows in, [HistoryEntry] out.
 *
 * The signature and its last-known stream URLs are stored separately because
 * they age at different rates — a retry keeps the signature for matching and
 * can refresh the URLs after re-resolving.
 */
class HistoryRepository(private val dao: HistoryDao) {

    fun observeAll(): Flow<List<HistoryEntry>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getById(id: String): HistoryEntry? = dao.getById(id)?.toDomain()

    suspend fun upsert(entry: HistoryEntry) = dao.upsert(entry.toEntity())

    suspend fun setStatus(id: String, status: DownloadStatus, message: String?, raw: String?, at: Long) =
        dao.setStatus(id, status.name, message, raw, at)

    suspend fun setCompleted(id: String, uri: String, name: String, path: String, size: Long?, at: Long) =
        dao.setCompleted(id, uri, name, path, size, at)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clear() = dao.clear()

    companion object {
        fun workNameOf(id: String) = "fetchly_$id"
    }
}

internal fun DownloadEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    title = title,
    uploader = uploader,
    sourceUrl = sourceUrl,
    platform = runCatching { Platform.valueOf(platform) }.getOrDefault(Platform.OTHER),
    mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.VIDEO),
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    optionLabel = optionLabel,
    kind = runCatching { QualityKind.valueOf(kind) }.getOrDefault(QualityKind.VIDEO),
    container = container,
    sizeBytes = sizeBytes,
    signature = Signatures.decode(signatureJson, streamUrlsJson),
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    errorMessage = errorMessage,
    errorRaw = errorRaw,
    outputUri = outputUri,
    outputName = outputName,
    outputPath = outputPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun HistoryEntry.toEntity(): DownloadEntity = DownloadEntity(
    id = id,
    title = title,
    uploader = uploader,
    sourceUrl = sourceUrl,
    platform = platform.name,
    mediaType = mediaType.name,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    optionLabel = optionLabel,
    kind = kind.name,
    container = container,
    sizeBytes = sizeBytes,
    signatureJson = Signatures.encodeSignature(signature),
    streamUrlsJson = Signatures.encodeUrls(signature),
    workName = HistoryRepository.workNameOf(id),
    status = status.name,
    errorMessage = errorMessage,
    errorRaw = errorRaw,
    outputUri = outputUri,
    outputName = outputName,
    outputPath = outputPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * org.json codec for the stored stream signature and its last-known URLs.
 *
 * JSON nulls are handled explicitly: `optString` can return the literal
 * "null" for them, and that must never leak into a [StreamRef].
 */
internal object Signatures {

    /** Structural part used for matching — everything but the URL. */
    fun encodeSignature(streams: List<StreamRef>): String {
        val array = JSONArray()
        streams.forEach { stream ->
            array.put(
                JSONObject().apply {
                    put("formatId", stream.formatId)
                    put("ext", stream.ext)
                    put("vcodec", stream.vcodec)
                    put("acodec", stream.acodec)
                    putOpt("height", stream.height)
                    putOpt("abr", stream.abr)
                    putOpt("sizeBytes", stream.sizeBytes)
                    putOpt("protocol", stream.protocol)
                }
            )
        }
        return array.toString()
    }

    /** Last-known URLs by format id — a cache that a retry may refresh. */
    fun encodeUrls(streams: List<StreamRef>): String {
        val urls = JSONObject()
        streams.forEach { urls.put(it.formatId, it.url) }
        return urls.toString()
    }

    fun decode(signatureJson: String?, urlsJson: String?): List<StreamRef> {
        val raw = signatureJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        val urls = urlsJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val formatId = entry.optString("formatId", "")
                StreamRef(
                    formatId = formatId,
                    url = (urls?.opt(formatId) as? String).orEmpty(),
                    ext = entry.optString("ext", ""),
                    vcodec = entry.optString("vcodec", "none"),
                    acodec = entry.optString("acodec", "none"),
                    height = entry.intOrNull("height"),
                    abr = entry.intOrNull("abr"),
                    sizeBytes = entry.longOrNull("sizeBytes"),
                    protocol = entry.stringOrNull("protocol"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
}
