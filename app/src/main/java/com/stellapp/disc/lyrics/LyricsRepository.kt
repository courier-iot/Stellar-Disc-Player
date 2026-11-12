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
        }
        null
    }

    fun findOnline(song: Song) {
        scope.launch {
            post("Searching lrclib.net…")
            val text = LrcLib.find(song.title, song.artist, song.album, song.durationMs)
            if (text != null) {
                withContext(Dispatchers.IO) { LyricsStore.save(ctx, song.id, text) }
                _version.update { it + 1 }
                post("Lyrics found")
            } else {
                post("No lyrics found for this track")
            }
        }
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
