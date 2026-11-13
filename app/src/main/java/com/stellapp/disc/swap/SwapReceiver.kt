package com.stellapp.disc.swap

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.PlaylistStore
import com.stellapp.disc.data.Song
import com.stellapp.disc.uplink.DownloadOptions
import com.stellapp.disc.uplink.DownloadService
import com.stellapp.disc.uplink.FinishedBatch
import com.stellapp.disc.uplink.MediaSaver
import com.stellapp.disc.uplink.PlaylistTarget
import com.stellapp.disc.uplink.UplinkManager
import com.stellapp.disc.uplink.UplinkPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Receiving side of Swap: connect, run the song checker, then transfer or queue downloads. */
object SwapReceiver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var base: String? = null
    private var token: String? = null
    /** Transfer key from the QR code (null for shares from older versions). */
    private var swapKey: ByteArray? = null

    private val _session = MutableStateFlow<SwapSession?>(null)
    val session: StateFlow<SwapSession?> = _session.asStateFlow()

    private val _backup = MutableStateFlow<String?>(null)
    /** After a move: the old phone's backup, waiting for the app to restore it. */
    val backup: StateFlow<String?> = _backup.asStateFlow()

    fun consumeBackup() { _backup.value = null }

    // ---------- starting ----------

    fun startNearby(context: Context, code: String, library: List<Song>) {
        val app = context.applicationContext
        val p = Pairing.parse(code)
        if (p == null) {
            _session.value = SwapSession(SwapSource.NEARBY, SwapPhase.ERROR, "Swap", message = "That QR code isn't a Stellar Disc share code.")
            return
        }
        job?.cancel()
        _session.value = SwapSession(SwapSource.NEARBY, SwapPhase.CONNECTING, p.name, message = "Reaching the other phone…")
        job = scope.launch {
            val conn = SwapClient.connect(app, p)
            if (conn == null) {
                val vpn = SwapClient.vpnOn(app)
                fail(
                    (if (vpn) "A VPN is on. Pause it, or allow local network access in its settings, then try again. Check the other phone too.\n\n" else "") +
                        "Couldn't reach the other phone. Check you're both on the same Wi-Fi (or you've joined their hotspot), " +
                        "and that their share screen is still open. Guest and public Wi-Fi often keep phones apart; a hotspot always works." +
                        (SwapClient.lastProblem?.let { "\n\nDetails:\n$it" } ?: ""),
                )
                return@launch
            }
            base = conn.first
            token = p.token
            swapKey = p.keyBytes()
            runChecker(app, conn.second, SwapSource.NEARBY, library)
        }
    }

    fun startLinks(context: Context, text: String, library: List<Song>) {
        val app = context.applicationContext
        val manifest = ShareText.parse(text)
        if (manifest == null) {
            _session.value = SwapSession(SwapSource.LINKS, SwapPhase.ERROR, "Swap", message = "No links found in that text.")
            return
        }
        job?.cancel()
        _session.value = SwapSession(SwapSource.LINKS, SwapPhase.CHECKING, manifest.name, message = "Checking your library…")
        job = scope.launch { runChecker(app, manifest, SwapSource.LINKS, library) }
    }

    private suspend fun runChecker(context: Context, manifest: RemoteManifest, source: SwapSource, library: List<Song>) {
        _session.value = SwapSession(source, SwapPhase.CHECKING, manifest.name, message = "Checking ${manifest.tracks.size} tracks against your library…")
        LinkStore.init(context)
        PlaylistStore.init(context)
        val matches = SongMatcher.check(context, library, manifest.tracks, LinkStore.all())
        val items = manifest.tracks.zip(matches) { t, m ->
            SwapItem(track = t, match = m.kind, local = m.local, selected = m.kind == MatchKind.NEW)
        }
        _session.value = SwapSession(
            source,
            SwapPhase.REVIEW,
            manifest.name,
            items = items,
            playlistName = manifest.name,
            // A move brings its own playlists in the backup, so no extra "shared" playlist.
            makePlaylist = !manifest.isMove,
            move = manifest.isMove,
            groups = manifest.groups,
        )
        if (manifest.isMove && source == SwapSource.NEARBY) keepAlive()
    }

    private var keepAliveJob: Job? = null

    /** While you pick what to move, check in with the old phone every minute so it stays on. */
    private fun keepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                val s = _session.value ?: break
                if (!s.move || s.phase != SwapPhase.REVIEW) break
                val b = base ?: break
                val t = token ?: break
                SwapClient.ping(b, t)
            }
        }
    }

    // ---------- review controls ----------

    fun toggle(index: Int) = edit { s ->
        s.copy(items = s.items.mapIndexed { i, it -> if (i == index && it.match != MatchKind.HAVE) it.copy(selected = !it.selected) else it })
    }

    fun selectAllNew() = edit { s -> s.copy(items = s.items.map { it.copy(selected = it.match == MatchKind.NEW) }) }

    fun selectNone() = edit { s -> s.copy(items = s.items.map { it.copy(selected = false) }) }

    fun setMakePlaylist(on: Boolean) = edit { it.copy(makePlaylist = on) }

    // ---------- moving to a new phone ----------

    /** Ticks or unticks [indices] (things already on this phone stay unticked). */
    fun setSelected(indices: Collection<Int>, on: Boolean) = edit { s ->
        val set = indices.toHashSet()
        s.copy(items = s.items.mapIndexed { i, it -> if (i in set && it.match != MatchKind.HAVE) it.copy(selected = on) else it })
    }

    /** Quick picks: ticks exactly the new files that pass [keep]. */
    fun pick(keep: (RemoteTrack) -> Boolean) = edit { s ->
        s.copy(items = s.items.map { it.copy(selected = it.match == MatchKind.NEW && keep(it.track)) })
    }

    /** Ticks new files in [priority] order until [budgetBytes] is used up. */
    fun fitToSpace(budgetBytes: Long, priority: FitPriority) = edit { s ->
        val order = s.items.indices
            .filter { s.items[it].match == MatchKind.NEW }
            .sortedWith(fitOrder(s.items, priority))
        var left = budgetBytes
        val chosen = HashSet<Int>()
        for (i in order) {
            val size = s.items[i].track.size.coerceAtLeast(0L)
            if (size <= left) {
                chosen += i
                left -= size
            }
        }
        s.copy(items = s.items.mapIndexed { i, it -> it.copy(selected = i in chosen) })
    }

    fun setIncludeBackup(on: Boolean) = edit { it.copy(includeBackup = on) }

    fun setPlaylistName(name: String) = edit { it.copy(playlistName = name) }

    private fun edit(change: (SwapSession) -> SwapSession) {
        _session.update { s -> if (s != null && s.phase == SwapPhase.REVIEW) change(s) else s }
    }

    // ---------- go ----------

    fun receive(context: Context, uplink: UplinkPrefs) {
        val s = _session.value ?: return
        if (s.phase != SwapPhase.REVIEW) return
        val app = context.applicationContext
        when (s.source) {
            SwapSource.NEARBY -> transferNearby(app, s)
            SwapSource.LINKS -> queueLinks(app, s, uplink)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Clears the session. A running transfer keeps going until it finishes or is cancelled. */
    fun close() {
        if (_session.value?.phase == SwapPhase.TRANSFERRING) return
        job?.cancel()
        _session.value = null
    }

    private fun transferNearby(context: Context, s: SwapSession) {
        val b = base ?: return fail("Lost the connection. Scan the code again.")
        val t = token ?: return fail("Lost the connection. Scan the code again.")
        _session.value = s.copy(
            phase = SwapPhase.TRANSFERRING,
            message = null,
            items = s.items.map { if (it.selected && it.match != MatchKind.HAVE) it else it.copy(state = ItemState.SKIPPED) },
        )
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))

        job = scope.launch {
            val saved = HashMap<Int, Long>()
            val dir = File(context.cacheDir, "swap").apply {
                deleteRecursively()
                mkdirs()
            }
            var cancelled = false
            try {
                s.items.forEachIndexed { i, item ->
                    if (!item.selected || item.match == MatchKind.HAVE) return@forEachIndexed
                    setItem(i) { it.copy(state = ItemState.RECEIVING, progress = 0f) }
                    _session.update { it?.copy(current = i) }
                    val dest = File(dir, "${i}_" + safeName(item.track))
                    try {
                        SwapClient.fetchFile(b, t, item.track, dest, swapKey) { p -> setItem(i) { it.copy(progress = p) } }
                        if (item.track.hash != null && QuickHash.ofFile(dest) != item.track.hash) {
                            throw IOException("File got corrupted on the way")
                        }
                        val id = MediaSaver.save(context, dest, item.track.isVideo) ?: throw IOException("Couldn't save")
                        item.track.link?.let { LinkStore.put(id, it) }
                        saved[i] = id
                        setItem(i) { it.copy(state = ItemState.SAVED, progress = 1f) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        setItem(i) { it.copy(state = ItemState.FAILED) }
                    } finally {
                        dest.delete()
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                withContext(NonCancellable) {
                    dir.deleteRecursively()
                    var backupNote: String? = null
                    if (s.move && s.includeBackup && !cancelled) {
                        backupNote = try {
                            _session.update { it?.copy(message = "Bringing over playlists and settings…") }
                            _backup.value = SwapClient.fetchBackup(b, t, swapKey)
                            "restoring playlists & settings"
                        } catch (e: Exception) {
                            "couldn't fetch playlists & settings"
                        }
                    }
                    val current = _session.value ?: s
                    val playlistNote = finishPlaylist(current, saved)
                    if (saved.isNotEmpty()) UplinkManager.announce(FinishedBatch(saved.values.toList(), queue = false))
                    val failed = current.items.count { it.state == ItemState.FAILED }
                    val msg = buildString {
                        append(if (cancelled) "Stopped. " else "Done. ")
                        append("Received ${saved.size}")
                        if (failed > 0) append(" · $failed failed")
                        playlistNote?.let { append(" · $it") }
                        backupNote?.let { append(" · $it") }
                    }
                    _session.update { it?.copy(phase = SwapPhase.DONE, message = msg, current = -1) }
                }
            }
        }
    }

    private fun queueLinks(context: Context, s: SwapSession, prefs: UplinkPrefs) {
        val playlistId = if (s.makePlaylist) PlaylistStore.create(s.playlistName.ifBlank { s.name }) else null
        if (playlistId != null) {
            val have = s.items.filter { !it.selected && it.local != null }.mapNotNull { it.local?.id }
            if (have.isNotEmpty()) PlaylistStore.addSongs(playlistId, have)
        }
        var queued = 0
        val items = s.items.map { item ->
            val link = item.track.link
            if (item.selected && item.match != MatchKind.HAVE && link != null) {
                UplinkManager.enqueue(
                    context,
                    DownloadOptions(
                        url = link,
                        format = prefs.format,
                        audioKbps = prefs.audioKbps,
                        maxHeight = prefs.maxHeight,
                        wholePlaylist = false,
                        embed = prefs.embed,
                        target = playlistId?.let { PlaylistTarget.Existing(it) } ?: PlaylistTarget.None,
                        queueWhenDone = false,
                        extraArgs = "",
                    ),
                )
                queued++
                item.copy(state = ItemState.QUEUED)
            } else {
                item.copy(state = ItemState.SKIPPED)
            }
        }
        val note = if (playlistId != null) " into \"${s.playlistName.ifBlank { s.name }}\"" else ""
        _session.value = s.copy(
            phase = SwapPhase.DONE,
            items = items,
            message = "Queued $queued download(s) in Uplink$note.",
        )
    }

    /** Builds the playlist in the original order, using your own copies where you already had the song. */
    private fun finishPlaylist(s: SwapSession, saved: Map<Int, Long>): String? {
        if (!s.makePlaylist) return null
        val ids = s.items.mapIndexedNotNull { i, item ->
            saved[i] ?: if (item.match != MatchKind.NEW) item.local?.id else null
        }
        if (ids.isEmpty()) return null
        val name = s.playlistName.ifBlank { s.name }
        val id = PlaylistStore.create(name)
        PlaylistStore.addSongs(id, ids)
        return "playlist \"$name\" ready"
    }

    private fun setItem(index: Int, change: (SwapItem) -> SwapItem) {
        _session.update { s -> s?.copy(items = s.items.mapIndexed { i, it -> if (i == index) change(it) else it }) }
    }

    private fun fail(message: String) {
        _session.update { (it ?: SwapSession(SwapSource.NEARBY, SwapPhase.ERROR, "Swap")).copy(phase = SwapPhase.ERROR, message = message) }
    }

    private fun safeName(t: RemoteTrack): String {
        val base = (if (t.artist.isNotBlank() && !t.artist.equals("Unknown artist", true)) "${t.artist} - ${t.title}" else t.title)
            .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_")
            .take(120)
            .ifBlank { "track" }
        val ext = t.ext.ifBlank { if (t.isVideo) "mp4" else "mp3" }
        return "$base.$ext"
    }
}

/** What Fit to space fills first. */
enum class FitPriority(val label: String) {
    MUSIC("MUSIC FIRST"),
    VIDEOS("VIDEOS FIRST"),
    FAVOURITES("FAVOURITES FIRST"),
}

/** Order for Fit to space: the chosen kind first, then most played, then smallest (more fits). */
internal fun fitOrder(items: List<SwapItem>, priority: FitPriority): Comparator<Int> =
    compareBy<Int> { i ->
        val t = items[i].track
        when (priority) {
            FitPriority.MUSIC -> if (t.isVideo) 1 else 0
            FitPriority.VIDEOS -> if (t.isVideo) 0 else 1
            FitPriority.FAVOURITES -> if (t.fav) 0 else 1
        }
    }.thenByDescending { items[it].track.plays }.thenBy { items[it].track.size }
