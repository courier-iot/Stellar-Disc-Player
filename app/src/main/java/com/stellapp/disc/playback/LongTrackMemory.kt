package com.stellapp.disc.playback

import android.content.Context
import org.json.JSONObject

/**
 * Remembers where you stopped in long tracks (podcasts, mixes, audiobooks: anything over
 * [MIN_LENGTH_MS]) so they pick up there next time, even after other songs played in between.
 */
object LongTrackMemory {
    const val MIN_LENGTH_MS = 20 * 60 * 1000L
    private const val MARGIN_MS = 30_000L
    private const val MAX_ENTRIES = 100
    private const val KEY = "long_positions"

    /** Should a position be kept for a track this long at this point? (Not at the very start or end.) */
    fun worthKeeping(durationMs: Long, positionMs: Long): Boolean =
        durationMs >= MIN_LENGTH_MS && positionMs > MARGIN_MS && positionMs < durationMs - MARGIN_MS

    private fun read(context: Context): JSONObject = try {
        JSONObject(context.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private fun write(context: Context, o: JSONObject) {
        context.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun remember(context: Context, mediaId: String, durationMs: Long, positionMs: Long) {
        val o = read(context)
        if (worthKeeping(durationMs, positionMs)) {
            o.remove(mediaId)
            o.put(mediaId, positionMs)
            // Keep the newest entries only.
            while (o.length() > MAX_ENTRIES) o.remove(o.keys().next())
        } else if (durationMs >= MIN_LENGTH_MS) {
            o.remove(mediaId)
        } else {
            return
        }
        write(context, o)
    }

    fun recall(context: Context, mediaId: String): Long? =
        read(context).optLong(mediaId, -1L).takeIf { it > 0 }
}
