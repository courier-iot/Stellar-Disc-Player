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
    /** The library as the files describe it (before your tag edits): used to match on another phone. */
    private val rawSongs: () -> List<Song> = allSongs,
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
                    prefs.put("library", buildExtras())
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
                var extrasNote = ""
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
                    it.optJSONArray("library")?.let { arr -> extrasNote = restoreExtras(arr) }
                }
                post("Restored ${lists.size} playlist(s) · matched $matched of $total tracks" + extrasNote)
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

    /**
     * Per-song extras that live outside playlists: favourites, pins, play counts, taste data,
     * lyrics, tag edits and source links. Saved by title/artist/length so another phone can match them.
     */
    private fun buildExtras(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        val favs = FavStore.ids.value
        val pins = PinStore.songs.value
        val stats = PlayStats.stats.value
        val metas = MetaStore.overrides.value
        val links = LinkStore.all()
        rawSongs().filter { !it.isStream }.forEach { s ->
            val fav = favs.indexOf(s.id)
            val pin = pins.indexOf(s.id)
            val st = stats[s.id]
            val taste = TasteStore.exportEntry(s.id)
            val lyrics = com.stellapp.disc.lyrics.LyricsStore.load(ctx, s.id)
            val meta = metas[s.id]
            val link = links[s.id]
            if (fav < 0 && pin < 0 && st == null && taste == null && lyrics == null && meta == null && link == null) return@forEach
            val o = org.json.JSONObject()
                .put("t", s.title).put("a", s.artist).put("al", s.album).put("d", s.durationMs).put("v", s.isVideo)
            if (link != null) o.put("l", link)
            if (fav >= 0) o.put("fav", fav)
            if (pin >= 0) o.put("pin", pin)
            if (st != null) o.put("pc", st.count).put("pt", st.lastPlayed)
            if (taste != null) o.put("taste", taste)
            if (lyrics != null) o.put("ly", lyrics)
            if (meta != null) {
                meta.title?.let { o.put("mt", it) }
                meta.artist?.let { o.put("ma", it) }
                meta.album?.let { o.put("mal", it) }
            }
            arr.put(o)
        }
        return arr
    }

    /** Matches backup extras to songs on this phone and applies them. Returns a short summary. */
    private suspend fun restoreExtras(arr: org.json.JSONArray): String {
        val entries = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        if (entries.isEmpty()) return ""
        val tracks = entries.mapIndexed { i, o ->
            RemoteTrack(
                index = i,
                title = o.optString("t"),
                artist = o.optString("a"),
                album = o.optString("al"),
                durationMs = o.optLong("d"),
                link = o.optString("l").ifBlank { null },
            )
        }
        val matches = SongMatcher.check(ctx, visibleSongs(), tracks, LinkStore.all())
        val favs = ArrayList<Pair<Int, Long>>()
        val pins = ArrayList<Pair<Int, Long>>()
        val plays = HashMap<Long, PlayStat>()
        val tastes = HashMap<Long, org.json.JSONObject>()
        var lyricsCount = 0
        var matched = 0
        withContext(Dispatchers.IO) {
            entries.forEachIndexed { i, o ->
                val m = matches.getOrNull(i) ?: return@forEachIndexed
                val id = m.local?.id ?: return@forEachIndexed
                if (m.kind == MatchKind.NEW) return@forEachIndexed
                matched++
                if (o.has("fav")) favs += o.optInt("fav") to id
                if (o.has("pin")) pins += o.optInt("pin") to id
                if (o.has("pc")) plays[id] = PlayStat(o.optInt("pc"), o.optLong("pt"))
                o.optJSONObject("taste")?.let { tastes[id] = it }
                o.optString("ly").takeIf { it.isNotBlank() }?.let { text ->
                    if (com.stellapp.disc.lyrics.LyricsStore.load(ctx, id) == null) {
                        com.stellapp.disc.lyrics.LyricsStore.save(ctx, id, text)
                        lyricsCount++
                    }
                }
                if ((o.has("mt") || o.has("ma") || o.has("mal")) && MetaStore.overrides.value[id] == null) {
                    MetaStore.set(id, MetaOverride(o.optString("mt").ifBlank { null }, o.optString("ma").ifBlank { null }, o.optString("mal").ifBlank { null }))
                }
                o.optString("l").takeIf { it.isNotBlank() && LinkStore.get(id) == null }?.let { LinkStore.put(id, it) }
            }
        }
        if (favs.isNotEmpty()) FavStore.set(favs.sortedBy { it.first }.map { it.second } + FavStore.ids.value)
        if (pins.isNotEmpty()) PinStore.addSongs(pins.sortedBy { it.first }.map { it.second })
        PlayStats.merge(plays)
        TasteStore.importEntries(tastes)
        return buildString {
            append(" · song extras for $matched track(s)")
            if (favs.isNotEmpty()) append(", ${favs.size} favourite(s)")
            if (lyricsCount > 0) append(", $lyricsCount lyric(s)")
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
