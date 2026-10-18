package com.fetchly.app.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.fetchly.app.domain.model.MediaType
import java.io.File
import java.io.IOException

sealed interface PublishResult {
    data class Ok(val uri: String) : PublishResult
    data class Failed(val raw: String) : PublishResult
}

/**
 * Copies a finished download into the shared media library, one folder per
 * kind: `Movies/Fetchly`, `Music/Fetchly`, `Pictures/Fetchly`.
 *
 * Written with the IS_PENDING handshake (API 29+): the row is invisible until
 * the bytes are fully in place, and a failed copy removes its own pending row
 * instead of leaving a zero-byte ghost in the user's gallery.
 */
object MediaStorePublisher {

    fun publish(
        context: Context,
        file: File,
        displayName: String,
        mime: String,
        mediaType: MediaType,
    ): PublishResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir(mediaType))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection(mediaType), values) }.getOrNull()
            ?: return PublishResult.Failed("the media library refused to create the file")
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IOException("the media library refused to open the file for writing")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            PublishResult.Ok(uri.toString())
        } catch (exc: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            PublishResult.Failed(exc.message ?: "the media library rejected the file")
        }
    }

    /** Removes a previously published file; false when it was already gone. */
    fun delete(context: Context, uri: String): Boolean =
        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) > 0 }
            .getOrDefault(false)

    private fun collection(mediaType: MediaType): Uri = when (mediaType) {
        MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        MediaType.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    private fun relativeDir(mediaType: MediaType): String = when (mediaType) {
        MediaType.VIDEO -> "Movies/Fetchly"
        MediaType.AUDIO -> "Music/Fetchly"
        MediaType.IMAGE -> "Pictures/Fetchly"
    }
}
