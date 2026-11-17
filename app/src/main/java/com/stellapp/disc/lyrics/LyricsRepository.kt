package com.stellapp.disc.lyrics

import android.content.Context
import android.net.Uri
import com.stellapp.disc.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Finding, saving and importing lyrics. [post] shows a short note on screen. */
class LyricsRepository(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val post: (String) -> Unit,
) {
    private val _version = MutableStateFlow(0)
    /** Bumps whenever saved lyrics change, so open screens reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    suspend fun load(song: Song, allowOnline: Boolean): Lyrics? = withContext(Dispatchers.IO) {
        LyricsStore.load(ctx, song.id)?.let { return@withContext LrcParser.parse(it, "saved") }
        if (!song.isVideo && !song.isStream) {
            Id3Lyrics.read(ctx, song.uri)?.let { return@withContext LrcParser.parse(it, "in the file") }
        }
        if (allowOnline && !song.isVideo) {
            LrcLib.find(song.title, song.artist, song.album, song.durationMs)?.let {
                LyricsStore.save(ctx, song.id, it)
                return@withContext LrcParser.parse(it, "lrclib.net")
            }
            LyricsOvh.find(song.title, song.artist)?.let {
                LyricsStore.save(ctx, song.id, it)
                return@withContext LrcParser.parse(it, "lyrics.ovh")
            }
        }
        null
    }

    fun findOnline(song: Song) {
        scope.launch {
            post("Searching lrclib.net…")
            var text = LrcLib.find(song.title, song.artist, song.album, song.durationMs)
            var from = "lrclib.net"
            if (text == null) {
                text = LyricsOvh.find(song.title, song.artist)
                from = "lyrics.ovh"
            }
            if (text != null) {
                withContext(Dispatchers.IO) { LyricsStore.save(ctx, song.id, text) }
                _version.update { it + 1 }
                post("Lyrics found on $from")
            } else {
                post("No lyrics found. Try SEARCH to look it up by hand.")
            }
        }
    }

    /** The manual search: lrclib.net results plus a lyrics.ovh match, if either has one. */
    suspend fun searchManual(title: String, artist: String): List<LyricsHit> {
        val hits = LrcLib.search(title, artist).toMutableList()
        LyricsOvh.find(title, artist)?.let { text ->
            hits += LyricsHit(title, artist, "", 0, synced = false, text = text, source = "lyrics.ovh")
        }
        return hits
    }

    fun save(song: Song, text: String) {
        scope.launch {
            withContext(Dispatchers.IO) { LyricsStore.save(ctx, song.id, text) }
            _version.update { it + 1 }
        }
    }

    fun import(song: Song, uri: Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
            }
            if (text.isNullOrBlank()) {
                post("Couldn't read that file")
            } else {
                withContext(Dispatchers.IO) { LyricsStore.save(ctx, song.id, text) }
                _version.update { it + 1 }
                post("Lyrics imported")
            }
        }
    }

    fun remove(song: Song) {
        scope.launch {
            withContext(Dispatchers.IO) { LyricsStore.delete(ctx, song.id) }
            _version.update { it + 1 }
        }
    }
}
