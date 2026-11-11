package com.stellapp.disc.swap

import android.util.Base64
import com.stellapp.disc.data.Song
import org.json.JSONArray
import org.json.JSONObject

/** A track offered by the other phone (or listed in a shared link list). */
data class RemoteTrack(
    val index: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val size: Long = 0L,
    /** Quick fingerprint of the file, see [QuickHash]. */
    val hash: String? = null,
    val ext: String = "",
    val isVideo: Boolean = false,
    val link: String? = null,
)

data class RemoteManifest(val name: String, val tracks: List<RemoteTrack>)

fun RemoteManifest.toJson(): String {
    val arr = JSONArray()
    tracks.forEach { t ->
        arr.put(
            JSONObject()
                .put("i", t.index)
                .put("title", t.title)
                .put("artist", t.artist)
                .put("album", t.album)
                .put("duration", t.durationMs)
                .put("size", t.size)
                .put("hash", t.hash ?: "")
                .put("ext", t.ext)
                .put("video", t.isVideo)
                .put("link", t.link ?: "")
        )
    }
    return JSONObject()
        .put("app", "stellar-disc")
        .put("v", 1)
        .put("name", name)
        .put("tracks", arr)
        .toString()
}

fun parseManifest(text: String): RemoteManifest {
    val o = JSONObject(text)
    require(o.optString("app") == "stellar-disc") { "Not a Stellar Disc share" }
    val arr = o.getJSONArray("tracks")
    val tracks = (0 until arr.length()).map { i ->
        val t = arr.getJSONObject(i)
        RemoteTrack(
            index = t.getInt("i"),
            title = t.optString("title", "Unknown signal"),
            artist = t.optString("artist", ""),
            album = t.optString("album", ""),
            durationMs = t.optLong("duration", 0L),
            size = t.optLong("size", 0L),
            hash = t.optString("hash", "").ifBlank { null },
            ext = t.optString("ext", ""),
            isVideo = t.optBoolean("video", false),
            link = t.optString("link", "").ifBlank { null },
        )
    }
    return RemoteManifest(o.optString("name", "Shared playlist"), tracks)
}

/** What the QR code holds: how to reach the sharing phone. */
data class Pairing(val port: Int, val token: String, val ips: List<String>, val name: String) {
    fun encode(): String {
        val n = Base64.encodeToString(name.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$PREFIX$port|$token|${ips.joinToString(",")}|$n"
    }

    companion object {
        const val PREFIX = "SDLAN1|"

        fun parse(text: String): Pairing? {
            val t = text.trim()
            if (!t.startsWith(PREFIX)) return null
            val p = t.removePrefix(PREFIX).split("|")
            if (p.size < 4) return null
            val port = p[0].toIntOrNull() ?: return null
            val ips = p[2].split(",").filter { it.isNotBlank() }
            if (ips.isEmpty() || p[1].isBlank()) return null
            val name = try {
                String(Base64.decode(p[3], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            } catch (e: Exception) {
                "Shared playlist"
            }
            return Pairing(port, p[1], ips, name)
        }
    }
}

enum class MatchKind { HAVE, PROBABLY, NEW }

data class TrackMatch(val kind: MatchKind, val local: Song?)

enum class SwapSource { NEARBY, LINKS }

enum class SwapPhase { CONNECTING, CHECKING, REVIEW, TRANSFERRING, DONE, ERROR }

enum class ItemState { WAITING, RECEIVING, SAVED, SKIPPED, FAILED, QUEUED }

data class SwapItem(
    val track: RemoteTrack,
    val match: MatchKind,
    val local: Song?,
    val selected: Boolean,
    val state: ItemState = ItemState.WAITING,
    val progress: Float = 0f,
)

data class SwapSession(
    val source: SwapSource,
    val phase: SwapPhase,
    val name: String,
    val items: List<SwapItem> = emptyList(),
    val message: String? = null,
    val makePlaylist: Boolean = true,
    val playlistName: String = name,
    val current: Int = -1,
) {
    val selectedCount: Int get() = items.count { it.selected && it.match != MatchKind.HAVE }
    val doneCount: Int get() = items.count { it.state == ItemState.SAVED || it.state == ItemState.FAILED || it.state == ItemState.QUEUED }
}
