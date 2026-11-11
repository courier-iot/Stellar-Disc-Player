package com.stellapp.disc.data

import com.stellapp.disc.BuildConfig
import com.stellapp.disc.swap.RemoteTrack
import org.json.JSONArray
import org.json.JSONObject

/** Backup files (playlists + settings) and M3U playlists. */
object Backup {

    data class RestoredPlaylist(val name: String, val tracks: List<RemoteTrack>)

    data class M3uEntry(val title: String, val artist: String, val durationMs: Long, val path: String)

    fun build(
        playlists: List<Playlist>,
        byId: Map<Long, Song>,
        links: Map<Long, String>,
        settings: JSONObject,
    ): String {
        val arr = JSONArray()
        playlists.forEach { p ->
            val tracks = JSONArray()
            p.songIds.mapNotNull { byId[it] }.forEach { s ->
                tracks.put(
                    JSONObject()
                        .put("title", s.title)
                        .put("artist", s.artist)
                        .put("album", s.album)
                        .put("duration", s.durationMs)
                        .put("size", s.size)
                        .put("path", s.path)
                        .put("link", links[s.id] ?: "")
                )
            }
            arr.put(JSONObject().put("name", p.name).put("tracks", tracks))
        }
        return JSONObject()
            .put("app", "stellar-disc-backup")
            .put("v", 1)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("created", System.currentTimeMillis())
            .put("playlists", arr)
            .put("settings", settings)
            .toString(2)
    }

    fun parse(text: String): Pair<List<RestoredPlaylist>, JSONObject?> {
        val o = JSONObject(text)
        require(o.optString("app") == "stellar-disc-backup") { "Not a Stellar Disc backup" }
        val arr = o.getJSONArray("playlists")
        val lists = (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val t = p.optJSONArray("tracks") ?: JSONArray()
            RestoredPlaylist(
                name = p.optString("name", "Restored playlist"),
                tracks = (0 until t.length()).map { j ->
                    val x = t.getJSONObject(j)
                    RemoteTrack(
                        index = j,
                        title = x.optString("title"),
                        artist = x.optString("artist"),
                        album = x.optString("album"),
                        durationMs = x.optLong("duration", 0L),
                        size = 0L, // sizes can't be trusted across phones; match on tags instead
                        link = x.optString("link").ifBlank { null },
                    )
                },
            )
        }
        return lists to o.optJSONObject("settings")
    }

    fun buildM3u(songs: List<Song>): String = buildString {
        appendLine("#EXTM3U")
        songs.forEach { s ->
            appendLine("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}")
            appendLine(s.path.ifBlank { s.uri.toString() })
        }
    }

    fun parseM3u(text: String): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        var pendingTitle = ""
        var pendingArtist = ""
        var pendingDur = 0L
        text.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val info = line.substringAfter(":")
                    pendingDur = (info.substringBefore(",").trim().toLongOrNull() ?: 0L).coerceAtLeast(0L) * 1000
                    val label = info.substringAfter(",", "").trim()
                    val parts = label.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        pendingArtist = parts[0].trim()
                        pendingTitle = parts[1].trim()
                    } else {
                        pendingArtist = ""
                        pendingTitle = label
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val fileName = line.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                    out += M3uEntry(pendingTitle.ifBlank { fileName }, pendingArtist, pendingDur, line)
                    pendingTitle = ""
                    pendingArtist = ""
                    pendingDur = 0L
                }
            }
        }
        return out
    }
}
