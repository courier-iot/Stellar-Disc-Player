package com.stellapp.disc.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.stellapp.disc.playback.PlaybackExtras
import com.stellapp.disc.swap.MatchKind
import com.stellapp.disc.swap.RemoteTrack
import com.stellapp.disc.swap.SongMatcher
import com.stellapp.disc.ui.CustomBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backups (playlists + settings + your backdrop photo) and M3U playlist files.
 * [allSongs] includes hidden folders (used for backups); [visibleSongs] is what the library shows.
 */
class LibraryTransfer(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val allSongs: () -> List<Song>,
    private val visibleSongs: () -> List<Song>,
    private val songById: (Long) -> Song?,
    private val setFade: (Int) -> Unit,
    private val post: (String) -> Unit,
) {
    private val playlists get() = PlaylistStore.playlists.value

    fun exportBackup(uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val byId = allSongs().associateBy { it.id }
                    val prefs = settings.toJson().put("fade", PlaybackExtras.fadeSeconds.value)
                    // Your own backdrop photo travels with the backup.
                    val photo = CustomBackdrop.file(ctx)
                    if (photo.exists() && photo.length() in 1L..6_000_000L) {
                        prefs.put("customBackdrop", Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP))
                    }
                    val text = Backup.build(playlists, byId, LinkStore.all(), prefs)
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            post(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }

    fun importBackup(uri: Uri) {
        scope.launch {
            try {
                val text = readText(uri) ?: throw IllegalStateException()
                val (lists, prefs) = Backup.parse(text)
                var matched = 0
                var total = 0
                lists.forEach { pl ->
                    val ids = matchTracks(pl.tracks)
                    total += pl.tracks.size
                    matched += ids.size
                    val id = PlaylistStore.create(uniquePlaylistName(pl.name))
                    PlaylistStore.addSongs(id, ids)
                }
                prefs?.let {
                    it.optString("customBackdrop").takeIf { b -> b.isNotBlank() }?.let { b64 ->
                        withContext(Dispatchers.IO) {
                            runCatching { CustomBackdrop.file(ctx).writeBytes(Base64.decode(b64, Base64.NO_WRAP)) }
                        }
                        CustomBackdrop.refresh(ctx)
                    }
                    settings.restore(it)
                    if (it.has("fade")) setFade(it.optInt("fade", 0))
                }
                post("Restored ${lists.size} playlist(s) · matched $matched of $total tracks")
            } catch (e: Exception) {
                post("That file isn't a Stellar Disc backup")
            }
        }
    }

    fun exportM3u(playlistId: String, uri: Uri) {
        scope.launch {
            val p = playlists.firstOrNull { it.id == playlistId } ?: return@launch
            val ok = withContext(Dispatchers.IO) {
                try {
                    val songs = p.songIds.mapNotNull { songById(it) }
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.buildM3u(songs).toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            post(if (ok) "Playlist exported" else "Couldn't export the playlist")
        }
    }

    fun importM3u(uri: Uri, fileName: String?) {
        scope.launch {
            val text = readText(uri)
            if (text == null) {
                post("Couldn't read that file")
                return@launch
            }
            val entries = Backup.parseM3u(text)
            if (entries.isEmpty()) {
                post("No tracks in that playlist file")
                return@launch
            }
            val library = visibleSongs()
            val byPath = library.filter { it.path.isNotBlank() }.associateBy { it.path }
            val byFile = library.filter { it.path.isNotBlank() }.associateBy { it.path.substringAfterLast('/') }
            val ids = ArrayList<Long>()
            val unmatched = ArrayList<RemoteTrack>()
            entries.forEachIndexed { i, e ->
                val hit = byPath[e.path] ?: byFile[e.path.substringAfterLast('/').substringAfterLast('\\')]
                if (hit != null) ids += hit.id else unmatched += RemoteTrack(i, e.title, e.artist, durationMs = e.durationMs)
            }
            ids += matchTracks(unmatched)
            val name = fileName?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported playlist"
            val id = PlaylistStore.create(uniquePlaylistName(name))
            PlaylistStore.addSongs(id, ids)
            post("Imported \"$name\" · matched ${ids.size} of ${entries.size} tracks")
        }
    }

    private suspend fun matchTracks(tracks: List<RemoteTrack>): List<Long> {
        if (tracks.isEmpty()) return emptyList()
        val matches = SongMatcher.check(ctx, visibleSongs(), tracks, LinkStore.all())
        return matches.mapNotNull { if (it.kind != MatchKind.NEW) it.local?.id else null }
    }

    private fun uniquePlaylistName(name: String): String {
        val existing = playlists.map { it.name }.toSet()
        if (name !in existing) return name
        var n = 2
        while ("$name ($n)" in existing) n++
        return "$name ($n)"
    }

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
