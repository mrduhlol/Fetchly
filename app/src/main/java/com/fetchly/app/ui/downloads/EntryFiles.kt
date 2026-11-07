package com.fetchly.app.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.util.MimeTypes
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File access errors are visible to the user, never swallowed by startActivity. */
internal object EntryFiles {
    enum class Result(val message: String?) {
        OPENED(null), MISSING("The file is no longer available. Fetch the source again to download another copy."),
        NO_APP("No installed app can open this file type. Try Share or install a compatible player."),
        FAILED("Couldn't access this file. Check the media library and try again."),
    }

    fun mimeOf(entry: HistoryEntry): String = MimeTypes.forContainer(entry.container, audio = entry.kind == QualityKind.AUDIO)

    suspend fun available(context: Context, entry: HistoryEntry): Boolean = withContext(Dispatchers.IO) {
        val uri = entry.outputUri?.let(Uri::parse) ?: return@withContext false
        try { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }
        catch (_: FileNotFoundException) { false }
        catch (_: Exception) { true } // Let Open explain an access failure rather than claim deletion.
    }

    suspend fun open(context: Context, entry: HistoryEntry): Result = launch(context, entry, share = false)
    suspend fun share(context: Context, entry: HistoryEntry): Result = launch(context, entry, share = true)

    private suspend fun launch(context: Context, entry: HistoryEntry, share: Boolean): Result {
        val uri = entry.outputUri?.let(Uri::parse) ?: return Result.MISSING
        val readable = withContext(Dispatchers.IO) {
            try { context.contentResolver.openFileDescriptor(uri, "r")?.use { Result.OPENED } ?: Result.MISSING }
            catch (_: FileNotFoundException) { Result.MISSING }
            catch (_: Exception) { Result.FAILED }
        }
        if (readable != Result.OPENED) return readable
        return withContext(Dispatchers.Main) {
            try {
                val intent = if (share) Intent(Intent.ACTION_SEND).setType(mimeOf(entry)).putExtra(Intent.EXTRA_STREAM, uri)
                    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(entry))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(if (share) Intent.createChooser(intent, entry.title) else intent)
                Result.OPENED
            } catch (_: ActivityNotFoundException) { Result.NO_APP }
            catch (_: Exception) { Result.FAILED }
        }
    }
}
