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
    /** Move-to-a-new-phone only: whether it's a favourite, and how often it was played. */
    val fav: Boolean = false,
    val plays: Int = 0,
)

/** A named set of tracks (a playlist on the old phone), by [RemoteTrack.index]. */
data class RemoteGroup(val name: String, val indices: List<Int>)

/** [kind] is "playlist" for a normal share, or "move" for a whole library going to a new phone. */
data class RemoteManifest(
    val name: String,
    val tracks: List<RemoteTrack>,
    val kind: String = KIND_PLAYLIST,
    val groups: List<RemoteGroup> = emptyList(),
) {
    val isMove: Boolean get() = kind == KIND_MOVE

    companion object {
        const val KIND_PLAYLIST = "playlist"
        const val KIND_MOVE = "move"
    }
}

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
                .put("fav", t.fav)
                .put("plays", t.plays)
        )
    }
    val groupArr = JSONArray()
    groups.forEach { g ->
        groupArr.put(JSONObject().put("name", g.name).put("i", JSONArray(g.indices)))
    }
    return JSONObject()
        .put("app", "stellar-disc")
        .put("v", 1)
        .put("kind", kind)
        .put("name", name)
        .put("tracks", arr)
        .put("groups", groupArr)
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
            fav = t.optBoolean("fav", false),
            plays = t.optInt("plays", 0),
        )
    }
    val groups = o.optJSONArray("groups")?.let { ga ->
        (0 until ga.length()).mapNotNull { i ->
            val g = ga.optJSONObject(i) ?: return@mapNotNull null
            val idx = g.optJSONArray("i") ?: return@mapNotNull null
            RemoteGroup(g.optString("name", "Playlist"), (0 until idx.length()).map { idx.getInt(it) })
        }
    }.orEmpty()
    return RemoteManifest(
        o.optString("name", "Shared playlist"),
        tracks,
        kind = o.optString("kind", RemoteManifest.KIND_PLAYLIST),
        groups = groups,
    )
}

/** What the QR code holds: how to reach the sharing phone. */
/**
 * What the QR code carries. [key] (base64) encrypts the transfer; it's only in the QR code, never
 * on the network. Shares from Stellar Disc before v2.9 have no key and transfer unencrypted.
 */
data class Pairing(val port: Int, val token: String, val ips: List<String>, val name: String, val key: String? = null) {
    fun encode(): String {
        val n = Base64.encodeToString(name.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$PREFIX$port|$token|${ips.joinToString(",")}|$n" + (key?.let { "|$it" } ?: "")
    }

    fun keyBytes(): ByteArray? = key?.let {
        try {
            Base64.decode(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            null
        }
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
            return Pairing(port, p[1], ips, name, p.getOrNull(4)?.ifBlank { null })
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
    /** Moving a whole library from an old phone. */
    val move: Boolean = false,
    /** The old phone's playlists, for picking by playlist. */
    val groups: List<RemoteGroup> = emptyList(),
    /** Also bring playlists, favourites, play counts and settings (the backup file). */
    val includeBackup: Boolean = true,
) {
    /** Bytes of everything ticked that isn't already on this phone. */
    val selectedBytes: Long get() = items.sumOf { if (it.selected && it.match != MatchKind.HAVE) it.track.size.coerceAtLeast(0L) else 0L }

    val selectedCount: Int get() = items.count { it.selected && it.match != MatchKind.HAVE }
    val doneCount: Int get() = items.count { it.state == ItemState.SAVED || it.state == ItemState.FAILED || it.state == ItemState.QUEUED }
}
