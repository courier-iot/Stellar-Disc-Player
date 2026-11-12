package com.stellapp.disc.uplink

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File

/**
 * Feeds yt-dlp's --download-archive so it skips things you already downloaded.
 * The archive is rebuilt before each download from files that still exist, so a song
 * you deleted can be downloaded again. Audio and video are tracked separately, so
 * having the MP3 doesn't block downloading the video.
 */
object ArchiveStore {
    private const val FILE_NAME = "archive_index.json"
    private val lock = Any()

    /** song id -> (archive key like "youtube dQw4w9WgXcQ", isVideo) */
    private fun load(context: Context): MutableMap<Long, Pair<String, Boolean>> = synchronized(lock) {
        val f = File(context.filesDir, FILE_NAME)
        val out = HashMap<Long, Pair<String, Boolean>>()
        try {
            if (f.exists()) {
                val o = JSONObject(f.readText())
                o.keys().forEach { k ->
                    val id = k.toLongOrNull() ?: return@forEach
                    val x = o.getJSONObject(k)
                    out[id] = x.getString("k") to x.optBoolean("v", false)
                }
            }
        } catch (_: Exception) {
        }
        out
    }

    private fun save(context: Context, map: Map<Long, Pair<String, Boolean>>) = synchronized(lock) {
        try {
            val o = JSONObject()
            map.forEach { (id, p) -> o.put(id.toString(), JSONObject().put("k", p.first).put("v", p.second)) }
            File(context.filesDir, FILE_NAME).writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    fun put(context: Context, songId: Long, key: String, isVideo: Boolean) {
        val map = load(context)
        map[songId] = key to isVideo
        save(context, map)
    }

    fun archiveFile(context: Context, isVideo: Boolean): File =
        File(context.filesDir, if (isVideo) "archive_video.txt" else "archive_audio.txt")

    /** Rewrites the archive file from songs that are still on the phone. Returns its path. */
    fun prepare(context: Context, isVideo: Boolean): String {
        val map = load(context)
        val alive = map.filterKeys { exists(context, it, map[it]?.second == true) }
        if (alive.size != map.size) save(context, alive)
        val lines = alive.values.filter { it.second == isVideo }.map { it.first }.distinct()
        val f = archiveFile(context, isVideo)
        f.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"))
        return f.absolutePath
    }

    private fun exists(context: Context, id: Long, isVideo: Boolean): Boolean = try {
        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(ContentUris.withAppendedId(base, id), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        true // can't tell: keep it rather than re-download
    }
}
