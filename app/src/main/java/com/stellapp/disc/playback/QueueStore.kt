package com.stellapp.disc.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Saves the queue and position so playback picks up where you left off. */
object QueueStore {
    private const val FILE_NAME = "queue.json"
    private val io = Executors.newSingleThreadExecutor()

    private fun prefs(context: Context) = context.getSharedPreferences("queue_state", Context.MODE_PRIVATE)

    /** Call when the queue itself changes. Streams are skipped (their links expire). */
    fun saveQueue(context: Context, p: Player) {
        val arr = JSONArray()
        val current = p.currentMediaItemIndex
        var kept = 0
        var newIndex = 0
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri ?: continue
            if (item.mediaId.startsWith("-")) continue
            if (i == current) newIndex = kept
            val m = item.mediaMetadata
            arr.put(
                JSONObject()
                    .put("id", item.mediaId)
                    .put("uri", uri.toString())
                    .put("title", m.title?.toString() ?: "")
                    .put("artist", m.artist?.toString() ?: "")
                    .put("album", m.albumTitle?.toString() ?: "")
                    .put("art", m.artworkUri?.toString() ?: "")
            )
            kept++
        }
        val text = JSONObject()
            .put("items", arr)
            .put("index", newIndex)
            .put("shuffle", p.shuffleModeEnabled)
            .put("repeat", p.repeatMode)
            .toString()
        val f = File(context.filesDir, FILE_NAME)
        io.execute {
            try {
                f.writeText(text)
            } catch (_: Exception) {
            }
        }
    }

    fun savePosition(context: Context, p: Player) {
        val id = p.currentMediaItem?.mediaId ?: return
        if (id.startsWith("-")) return
        prefs(context).edit()
            .putString("pos_id", id)
            .putLong("pos_ms", p.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    /** Loads the saved queue into an empty player, paused. */
    fun restore(context: Context, p: Player) {
        if (p.mediaItemCount > 0) return
        try {
            val f = File(context.filesDir, FILE_NAME)
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("items")
            if (arr.length() == 0) return
            val items = (0 until arr.length()).map { i ->
                val x = arr.getJSONObject(i)
                val meta = MediaMetadata.Builder()
                    .setTitle(x.optString("title"))
                    .setArtist(x.optString("artist"))
                    .setAlbumTitle(x.optString("album"))
                    .apply { x.optString("art").ifBlank { null }?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
                MediaItem.Builder()
                    .setMediaId(x.getString("id"))
                    .setUri(Uri.parse(x.getString("uri")))
                    .setMediaMetadata(meta)
                    .build()
            }
            val prefs = prefs(context)
            val posId = prefs.getString("pos_id", null)
            val byId = items.indexOfFirst { it.mediaId == posId }
            val index = if (byId >= 0) byId else o.optInt("index", 0).coerceIn(0, items.lastIndex)
            val pos = if (byId >= 0) prefs.getLong("pos_ms", 0L) else 0L
            p.shuffleModeEnabled = o.optBoolean("shuffle", false)
            p.repeatMode = o.optInt("repeat", Player.REPEAT_MODE_OFF)
            p.setMediaItems(items, index, pos)
            p.prepare()
        } catch (_: Exception) {
        }
    }
}
