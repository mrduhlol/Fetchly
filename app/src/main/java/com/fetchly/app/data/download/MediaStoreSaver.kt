package com.fetchly.app.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.security.UrlSecurity
import java.io.File

/** MediaStore-first storage on Android 10+. No broad storage permissions needed. */
object MediaStoreSaver {

    fun buildFileName(title: String, quality: String, container: String): String {
        val ext = container.lowercase().take(5).ifBlank { "mp4" }
        val base = UrlSecurity.sanitizeFileName("${title}_${quality}")
        return "$base.$ext"
    }

    fun collectionFor(type: MediaType, container: String): Triple<Uri, String, String> {
        return when {
            type == MediaType.AUDIO || container.equals("mp3", true) || container.equals("m4a", true) ->
                Triple(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    Environment.DIRECTORY_MUSIC + "/Fetchly",
                    "audio/*",
                )
            type == MediaType.IMAGE || container.equals("jpg", true) || container.equals("png", true) ->
                Triple(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    Environment.DIRECTORY_PICTURES + "/Fetchly",
                    "image/*",
                )
            else -> Triple(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                Environment.DIRECTORY_MOVIES + "/Fetchly",
                "video/*",
            )
        }
    }

    fun createEntry(
        context: Context,
        type: MediaType,
        container: String,
        fileName: String,
    ): Uri? {
        val (collection, relativePath, mime) = collectionFor(type, container)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values)
    }

    fun markComplete(context: Context, uri: Uri) {
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null, null,
        )
    }

    fun tempFile(context: Context, fileName: String): File =
        File(context.cacheDir, "fetchly_$fileName")
}
