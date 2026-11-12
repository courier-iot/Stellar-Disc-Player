package com.stellapp.disc.uplink

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.stellapp.disc.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Copies a finished download into Music/Stellar Disc or Movies/Stellar Disc and returns its library id. */
object MediaSaver {

    suspend fun save(context: Context, file: File, isVideo: Boolean): Long? = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallbackMime(ext, isVideo)
        val dirName = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveModern(context, file, mime, "$dirName/${AppInfo.FOLDER}", isVideo)
            } else {
                saveLegacy(context, file, mime, dirName)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveModern(context: Context, file: File, mime: String, relativePath: String, isVideo: Boolean): Long? {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName(file))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("no output stream")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            ContentUris.parseId(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(context: Context, file: File, mime: String, dirName: String): Long? {
        val base = Environment.getExternalStoragePublicDirectory(dirName)
        val destDir = File(base, AppInfo.FOLDER).apply { mkdirs() }
        var dest = File(destDir, cleanName(file))
        var n = 1
        while (dest.exists()) {
            dest = File(destDir, "${file.nameWithoutExtension} ($n).${file.extension}")
            n++
        }
        file.copyTo(dest)
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime)) { _, uri ->
                if (cont.isActive) cont.resume(uri?.let { ContentUris.parseId(it) })
            }
        }
    }

    /** Drops the playlist position yt-dlp put in front ("3 - Song.mp3" -> "Song.mp3"). */
    private fun cleanName(file: File): String =
        file.name.replace(Regex("""^\d{1,4} - """), "").ifBlank { file.name }

    private fun fallbackMime(ext: String, isVideo: Boolean): String = when (ext) {
        "opus", "ogg", "oga" -> "audio/ogg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/x-wav"
        "mka" -> "audio/x-matroska"
        "mkv" -> "video/x-matroska"
        "webm" -> if (isVideo) "video/webm" else "audio/webm"
        "mp4", "m4v" -> "video/mp4"
        else -> if (isVideo) "video/*" else "audio/*"
    }
}
