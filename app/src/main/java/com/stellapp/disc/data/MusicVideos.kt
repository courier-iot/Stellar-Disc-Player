package com.stellapp.disc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which videos count as music: they join shuffle mixes and the random rotation like songs do.
 * Videos Uplink saved (Movies/Stellar Disc) always count. Any other video counts once you mark it,
 * or mark its whole folder, from the long-press menu.
 */
object MusicVideos {
    private const val PREFS = "stellar_disc"
    private const val KEY_IDS = "music_video_ids"
    private const val KEY_FOLDERS = "music_video_folders"

    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    private val _folders = MutableStateFlow<Set<String>>(emptySet())
    val folders: StateFlow<Set<String>> = _folders.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _ids.value = (p.getStringSet(KEY_IDS, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        _folders.value = (p.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()).toSet()
        loaded = true
    }

    /** True when [song] should play like music. Audio always does. */
    fun counts(song: Song): Boolean =
        !song.isVideo || song.inAppFolder || song.id in _ids.value || (song.folder.isNotBlank() && song.folder in _folders.value)

    /** Marked by hand (not just because Uplink saved it). */
    fun markedOne(song: Song): Boolean = song.id in _ids.value
    fun markedFolder(song: Song): Boolean = song.folder.isNotBlank() && song.folder in _folders.value

    fun setOne(context: Context, id: Long, on: Boolean) {
        load(context)
        _ids.value = if (on) _ids.value + id else _ids.value - id
        save(context)
    }

    fun setFolder(context: Context, folder: String, on: Boolean) {
        if (folder.isBlank()) return
        load(context)
        _folders.value = if (on) _folders.value + folder else _folders.value - folder
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_IDS, _ids.value.map { it.toString() }.toSet())
            .putStringSet(KEY_FOLDERS, _folders.value)
            .apply()
    }
}
