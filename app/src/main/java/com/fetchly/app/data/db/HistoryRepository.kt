package com.fetchly.app.data.db

import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.StreamRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Domain-facing history: rows map to HistoryEntry; signatures round-trip via JSON. */
class HistoryRepository(private val dao: HistoryDao) {

    fun observeAll(): Flow<List<HistoryEntry>> =
        dao.observeAll().map { rows -> rows.map(HistoryMapper::toEntry) }

    suspend fun get(id: String): HistoryEntry? = dao.getById(id)?.let(HistoryMapper::toEntry)

    suspend fun insert(entry: HistoryEntry) = dao.upsert(HistoryMapper.toEntity(entry))

    suspend fun setStatus(
        id: String,
        status: DownloadStatus,
        message: String?,
        raw: String?,
        updatedAt: Long,
    ) = dao.setStatus(id, status.name, message, raw, updatedAt)

    suspend fun setCompleted(
        id: String,
        uri: String?,
        name: String?,
        path: String?,
        size: Long?,
        updatedAt: Long,
    ) = dao.setCompleted(id, uri, name, path, size, updatedAt)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clear() = dao.clear()
}

/** DownloadEntity ↔ HistoryEntry mapping, kept pure so it is unit-testable. */
internal object HistoryMapper {

    fun toEntity(entry: HistoryEntry): DownloadEntity = DownloadEntity(
        id = entry.id,
        title = entry.title,
        uploader = entry.uploader,
        sourceUrl = entry.sourceUrl,
        platform = entry.platform.name,
        mediaType = entry.mediaType.name,
        thumbnailUrl = entry.thumbnailUrl,
        durationSeconds = entry.durationSeconds,
        optionLabel = entry.optionLabel,
        container = entry.container,
        sizeBytes = entry.sizeBytes,
        signatureJson = Signatures.encodeSignature(entry.signature),
        streamUrlsJson = Signatures.encodeUrls(entry.signature),
        workName = "fetchly_${entry.id}",
        status = entry.status.name,
        errorMessage = entry.errorMessage,
        errorRaw = entry.errorRaw,
        outputUri = entry.outputUri,
        outputName = entry.outputName,
        outputPath = entry.outputPath,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
    )

    fun toEntry(entity: DownloadEntity): HistoryEntry = HistoryEntry(
        id = entity.id,
        title = entity.title,
        uploader = entity.uploader,
        sourceUrl = entity.sourceUrl,
        platform = entity.platform.asEnum(Platform.OTHER),
        mediaType = entity.mediaType.asEnum(MediaType.VIDEO),
        thumbnailUrl = entity.thumbnailUrl,
        durationSeconds = entity.durationSeconds,
        optionLabel = entity.optionLabel,
        container = entity.container,
        sizeBytes = entity.sizeBytes,
        signature = Signatures.decode(entity.signatureJson, entity.streamUrlsJson),
        status = entity.status.asEnum(DownloadStatus.FAILED),
        errorMessage = entity.errorMessage,
        errorRaw = entity.errorRaw,
        outputUri = entity.outputUri,
        outputName = entity.outputName,
        outputPath = entity.outputPath,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /** Unknown names (a future enum rename over an old DB) map to a safe default. */
    private inline fun <reified T : Enum<T>> String.asEnum(fallback: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: fallback
}

/**
 * JSON codec for stream signatures. Two columns on purpose: the signature is
 * what OptionMatcher compares (formatId, ext, codecs, height, abr, sizes — no
 * URLs); the URLs live in their own column because retry may replace them
 * after a fresh resolve.
 */
internal object Signatures {

    fun encodeSignature(streams: List<StreamRef>): String {
        val array = JSONArray()
        for (s in streams) {
            array.put(
                JSONObject()
                    .put("formatId", s.formatId)
                    .put("ext", s.ext)
                    .put("vcodec", s.vcodec)
                    .put("acodec", s.acodec)
                    .putOpt("height", s.height)
                    .putOpt("abr", s.abr)
                    .putOpt("sizeBytes", s.sizeBytes)
                    .putOpt("protocol", s.protocol),
            )
        }
        return array.toString()
    }

    fun encodeUrls(streams: List<StreamRef>): String {
        val obj = JSONObject()
        for (s in streams) obj.putOpt(s.formatId, s.url)
        return obj.toString()
    }

    fun decode(signatureJson: String?, urlsJson: String?): List<StreamRef> {
        val array = runCatching { JSONArray(signatureJson ?: "") }.getOrNull() ?: return emptyList()
        val urls = runCatching { JSONObject(urlsJson ?: "") }.getOrNull() ?: JSONObject()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val formatId = o.optString("formatId", "")
                add(
                    StreamRef(
                        formatId = formatId,
                        url = urls.optString(formatId, ""),
                        ext = o.optString("ext", ""),
                        vcodec = o.optString("vcodec", ""),
                        acodec = o.optString("acodec", ""),
                        height = o.intOrNull("height"),
                        abr = o.intOrNull("abr"),
                        sizeBytes = o.longOrNull("sizeBytes"),
                        protocol = o.stringOrNull("protocol"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key)
}
