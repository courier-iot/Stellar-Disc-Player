package com.stellapp.disc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val created: Long,
)

/** Playlists saved as a small JSON file in the app's private storage. */
object PlaylistStore {
    private const val FILE_NAME = "playlists.json"
    private val lock = Any()
    private var file: File? = null

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            _playlists.value = try {
                if (f.exists()) parse(f.readText()) else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun create(name: String): String {
        val p = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Untitled orbit" },
            songIds = emptyList(),
            created = System.currentTimeMillis(),
        )
        mutate { it + p }
        return p.id
    }

    fun rename(id: String, name: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
    }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun addSongs(id: String, songIds: List<Long>) = mutate { list ->
        list.map { p ->
            if (p.id == id) p.copy(songIds = (p.songIds + songIds).distinct()) else p
        }
    }

    /** Replaces a playlist's tracks (used to undo a removal exactly). */
    fun setSongs(id: String, songIds: List<Long>) = mutate { list ->
        list.map { p -> if (p.id == id) p.copy(songIds = songIds) else p }
    }

    /** Puts a deleted playlist back exactly as it was. */
    fun restore(playlist: Playlist) = mutate { list -> if (list.any { it.id == playlist.id }) list else list + playlist }

    fun removeSong(id: String, songId: Long) = mutate { list ->
        list.map { p -> if (p.id == id) p.copy(songIds = p.songIds - songId) else p }
    }

    private fun mutate(change: (List<Playlist>) -> List<Playlist>) {
        synchronized(lock) {
            val next = change(_playlists.value)
            _playlists.value = next
            save(next)
        }
    }

    private fun save(list: List<Playlist>) {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(toJson(list))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun toJson(list: List<Playlist>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("created", p.created)
                    .put("songs", JSONArray().apply { p.songIds.forEach { put(it) } })
            )
        }
        return arr.toString()
    }

    private fun parse(text: String): List<Playlist> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val songs = o.optJSONArray("songs") ?: JSONArray()
            Playlist(
                id = o.getString("id"),
                name = o.optString("name", "Untitled orbit"),
                songIds = (0 until songs.length()).map { songs.getLong(it) },
                created = o.optLong("created", 0L),
            )
        }
    }
}
