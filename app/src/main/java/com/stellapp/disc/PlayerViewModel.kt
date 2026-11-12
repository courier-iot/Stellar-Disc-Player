package com.stellapp.disc

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.stellapp.disc.data.Backup
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.MetaOverride
import com.stellapp.disc.data.MetaStore
import com.stellapp.disc.data.MusicRepository
import com.stellapp.disc.data.PlayStat
import com.stellapp.disc.data.PinStore
import com.stellapp.disc.data.PlayStats
import com.stellapp.disc.data.Taste
import com.stellapp.disc.data.TasteStore
import com.stellapp.disc.data.Playlist
import com.stellapp.disc.data.PlaylistStore
import com.stellapp.disc.data.Song
import com.stellapp.disc.data.toMediaItem
import com.stellapp.disc.data.toSong
import com.stellapp.disc.lyrics.Id3Lyrics
import com.stellapp.disc.lyrics.LrcLib
import com.stellapp.disc.lyrics.LrcParser
import com.stellapp.disc.lyrics.Lyrics
import com.stellapp.disc.lyrics.LyricsStore
import com.stellapp.disc.playback.PlaybackExtras
import com.stellapp.disc.swap.MatchKind
import com.stellapp.disc.swap.RemoteTrack
import com.stellapp.disc.swap.ShareText
import com.stellapp.disc.swap.SongMatcher
import com.stellapp.disc.uplink.DlFormat
import com.stellapp.disc.uplink.DownloadOptions
import com.stellapp.disc.uplink.PlaylistTarget
import com.stellapp.disc.uplink.StreamInfo
import com.stellapp.disc.uplink.UplinkManager
import com.stellapp.disc.uplink.UplinkPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.random.Random

/** One entry in the play queue. [index] is the position in the player's list. */
data class QueueEntry(val index: Int, val song: Song)

data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Queue in the order it will actually play (follows shuffle). */
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasSubtitles: Boolean = false,
    val subtitlesOn: Boolean = true,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE)
    private val ctx: Context get() = getApplication<Application>()

    // ---------- library ----------
    private var rawSongs: List<Song> = emptyList()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /** Visible library (custom tags applied, hidden folders removed). Includes videos. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private var loaded = false
    private var reloadPending = false
    private var pendingQueueIds: List<Long> = emptyList()

    private val _libraryReady = MutableStateFlow(false)
    val libraryReady: StateFlow<Boolean> = _libraryReady.asStateFlow()

    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet())
    val hiddenFolders: StateFlow<Set<String>> = _hidden.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = PlaylistStore.playlists
    val stats: StateFlow<Map<Long, PlayStat>> = PlayStats.stats
    val tastes: StateFlow<Map<Long, Taste>> = TasteStore.tastes
    val pinnedSongs: StateFlow<List<Long>> = PinStore.songs
    val pinnedPlaylists: StateFlow<List<String>> = PinStore.playlists

    private val _modernHome = MutableStateFlow(prefs.getBoolean(KEY_MODERN_HOME, true))
    /** true = the new home overview, false = the "under the hood" library view. */
    val modernHome: StateFlow<Boolean> = _modernHome.asStateFlow()

    private val _audioOnly = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_ONLY, false))
    /** Play videos without the picture (saves battery). */
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    private val _backdrop = MutableStateFlow(
        runCatching { com.stellapp.disc.ui.Backdrop.valueOf(prefs.getString(KEY_BACKDROP, null) ?: "SYNTHWAVE") }
            .getOrDefault(com.stellapp.disc.ui.Backdrop.SYNTHWAVE)
    )
    /** Scene drawn behind the now-playing screen. */
    val backdrop: StateFlow<com.stellapp.disc.ui.Backdrop> = _backdrop.asStateFlow()

    private val _clearView = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_VIEW, false))
    /** Now Playing with the centre disc/video faded out, so the backdrop shows in full. */
    val clearView: StateFlow<Boolean> = _clearView.asStateFlow()

    private val _heroMode = MutableStateFlow(
        runCatching { HeroMode.valueOf(prefs.getString(KEY_HERO_MODE, null) ?: "TOP_PICK") }.getOrDefault(HeroMode.TOP_PICK)
    )
    /** What the big hero card on Home shows. */
    val heroMode: StateFlow<HeroMode> = _heroMode.asStateFlow()

    private val _ytSignedIn = MutableStateFlow(UplinkManager.hasCookies(app))
    val ytSignedIn: StateFlow<Boolean> = _ytSignedIn.asStateFlow()

    // ---------- player ----------
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _videoFullscreen = MutableStateFlow(false)
    val videoFullscreen: StateFlow<Boolean> = _videoFullscreen.asStateFlow()

    // ---------- settings ----------
    private val _accent = MutableStateFlow(prefs.getInt(KEY_ACCENT, 0))
    val accentIndex: StateFlow<Int> = _accent.asStateFlow()

    private val _animate = MutableStateFlow(prefs.getBoolean(KEY_ANIMATE, true))
    val animateBackground: StateFlow<Boolean> = _animate.asStateFlow()

    private val _lyricsAuto = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_AUTO, false))
    val lyricsAuto: StateFlow<Boolean> = _lyricsAuto.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _lyricsVersion = MutableStateFlow(0)
    val lyricsVersion: StateFlow<Int> = _lyricsVersion.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** A short note to show at the bottom of the screen. */
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---------- sharing ----------
    private val _sharedSwap = MutableStateFlow<String?>(null)
    val sharedSwap: StateFlow<String?> = _sharedSwap.asStateFlow()

    private val _sharedUrl = MutableStateFlow<String?>(null)
    val sharedUrl: StateFlow<String?> = _sharedUrl.asStateFlow()

    private var controller: MediaController? = null
    private var songById: Map<String, Song> = emptyMap()
    private val streams = HashMap<String, Song>()
    private var nextStreamId = -2_000_000L
    private var cachedQueue: List<QueueEntry> = emptyList()

    init {
        viewModelScope.launch {
            UplinkManager.finished.collect { batch ->
                if (batch.queue) pendingQueueIds = pendingQueueIds + batch.songIds
                loadLibrary(force = true)
            }
        }
        viewModelScope.launch {
            MetaStore.overrides.collect { if (loaded) publish() }
        }

        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = try {
                MediaController.Builder(app, token).buildAsync().await()
            } catch (e: Exception) {
                return@launch
            }
            controller = c
            _player.value = c
            applyAudioOnly(c)
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && player.playWhenReady) {
                        player.currentMediaItem?.mediaId?.toLongOrNull()?.let { PlayStats.record(it) }
                    }
                    sync(
                        rebuildQueue = events.containsAny(
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        )
                    )
                }
            })
            sync(rebuildQueue = true)

            while (isActive) {
                if (c.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        )
                    }
                }
                delay(250)
            }
        }
    }

    // ================= library =================

    fun hasVideoAccess(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun loadLibrary(force: Boolean = false) {
        if (loaded && !force) return
        if (_loading.value) {
            if (force) reloadPending = true
            return
        }
        viewModelScope.launch {
            _loading.value = true
            rawSongs = try {
                MusicRepository.loadSongs(ctx, allVideos = hasVideoAccess())
            } catch (e: Exception) {
                emptyList()
            }
            loaded = true
            publish()
            _loading.value = false
            _libraryReady.value = true

            if (pendingQueueIds.isNotEmpty()) {
                val ids = pendingQueueIds
                pendingQueueIds = emptyList()
                enqueueSongs(ids)
            }
            if (reloadPending) {
                reloadPending = false
                loadLibrary(force = true)
            }
        }
    }

    private fun publish() {
        val hidden = _hidden.value
        val all = rawSongs.map { MetaStore.apply(it) }
        songById = all.associateBy { it.id.toString() }
        _songs.value = all.filter { it.folder !in hidden }
        sync(rebuildQueue = true)
    }

    fun hideFolder(folder: String) {
        val next = _hidden.value + folder
        _hidden.value = next
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
        publish()
        post("Folder hidden. Unhide it in Settings.")
    }

    fun unhideFolder(folder: String) {
        val next = _hidden.value - folder
        _hidden.value = next
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
        publish()
    }

    /** After the system deleted files: drop them from playlists and rescan. */
    fun afterDelete(ids: List<Long>) {
        playlists.value.forEach { p -> ids.forEach { id -> if (id in p.songIds) PlaylistStore.removeSong(p.id, id) } }
        loadLibrary(force = true)
        post("Deleted ${ids.size} file(s)")
    }

    // ================= playback =================

    /** Plays [list] starting at [index]. Pass [shuffle] to force shuffle on/off; null keeps the current setting. */
    fun playList(list: List<Song>, index: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        shuffle?.let { c.shuffleModeEnabled = it }
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    /**
     * A single song picked on its own (not "play playlist"): play it, then keep going with a
     * random rotation from the rest of the music library. No rules, just a random shuffle.
     */
    fun playSong(song: Song) {
        if (song.isVideo || song.isStream) {
            playList(listOf(song), 0)
            return
        }
        val rest = _songs.value.filter { !it.isVideo && it.id != song.id }.shuffled()
        playList(listOf(song) + rest, 0, shuffle = false)
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        playList(list, Random.nextInt(list.size), shuffle = true)
    }

    fun enqueueSongs(ids: List<Long>) {
        val c = controller ?: return
        val list = ids.mapNotNull { songById[it.toString()] }
        if (list.isEmpty()) return
        if (c.mediaItemCount == 0) playList(list, 0) else c.addMediaItems(list.map { it.toMediaItem() })
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playList(listOf(song), 0)
        } else {
            c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), song.toMediaItem())
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }

    fun previous() { controller?.seekToPrevious() }

    /** Swipe on Now Playing: jump a whole track. Returns false when there's nothing that way. */
    fun swipeTrack(forward: Boolean): Boolean {
        val c = controller ?: return false
        return if (forward) {
            if (!c.hasNextMediaItem()) return false
            c.seekToNextMediaItem()
            true
        } else {
            if (!c.hasPreviousMediaItem()) {
                c.seekTo(0L)
                return false
            }
            c.seekToPreviousMediaItem()
            true
        }
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val dur = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        seekTo((c.currentPosition + deltaMs).coerceIn(0L, dur))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.removeMediaItem(index)
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setSubtitles(on: Boolean) {
        val c = controller ?: return
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
            .build()
        _state.update { it.copy(subtitlesOn = on) }
    }

    fun setAudioOnly(on: Boolean) {
        _audioOnly.value = on
        prefs.edit().putBoolean(KEY_AUDIO_ONLY, on).apply()
        controller?.let { applyAudioOnly(it) }
    }

    private fun applyAudioOnly(c: MediaController) {
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, _audioOnly.value)
            .build()
    }

    fun setModernHome(on: Boolean) {
        _modernHome.value = on
        prefs.edit().putBoolean(KEY_MODERN_HOME, on).apply()
    }

    fun setBackdrop(b: com.stellapp.disc.ui.Backdrop) {
        _backdrop.value = b
        prefs.edit().putString(KEY_BACKDROP, b.name).apply()
    }

    fun setClearView(on: Boolean) {
        _clearView.value = on
        prefs.edit().putBoolean(KEY_CLEAR_VIEW, on).apply()
    }

    fun setHeroMode(m: HeroMode) {
        _heroMode.value = m
        prefs.edit().putString(KEY_HERO_MODE, m.name).apply()
    }

    fun togglePinSong(id: Long) = PinStore.toggleSong(id)
    fun togglePinPlaylist(id: String) = PinStore.togglePlaylist(id)

    fun importYtCookies(uri: Uri) {
        viewModelScope.launch {
            val error = UplinkManager.importCookies(ctx, uri)
            _ytSignedIn.value = UplinkManager.hasCookies(ctx)
            post(error ?: "YouTube sign-in saved. Uplink will use it.")
        }
    }

    fun clearYtCookies() {
        UplinkManager.clearCookies(ctx)
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        } catch (_: Exception) {
        }
        _ytSignedIn.value = false
        post("Signed out of YouTube")
    }

    /** From the in-app sign-in screen: the browser's YouTube cookies. */
    fun saveYtSignIn(rawCookies: String) {
        if (rawCookies.isBlank()) {
            post("No YouTube sign-in found")
            return
        }
        UplinkManager.saveCookies(ctx, com.stellapp.disc.ui.toNetscapeCookies(rawCookies))
        _ytSignedIn.value = true
        post("Signed in. Uplink will use your YouTube account.")
    }

    // ----- yt-dlp engine -----
    val hasJsRuntime: Boolean get() = UplinkManager.jsRuntimePath(ctx) != null

    private val _nightly = MutableStateFlow(UplinkManager.useNightly(app))
    val nightly: StateFlow<Boolean> = _nightly.asStateFlow()

    fun setNightly(on: Boolean) {
        UplinkManager.setNightly(ctx, on)
        _nightly.value = on
    }

    fun ytdlpGlobalOptions(): String = UplinkManager.globalOptions(ctx)

    fun setYtdlpGlobalOptions(text: String) {
        UplinkManager.setGlobalOptions(ctx, text.trim())
        post("Saved. These options now apply to every download.")
    }

    fun setVideoFullscreen(on: Boolean) {
        _videoFullscreen.value = on
    }

    // sleep timer & fades live in the playback service
    fun setSleep(minutes: Int) = PlaybackExtras.setSleep(minutes)
    fun setSleepEndOfTrack() = PlaybackExtras.setSleepEndOfTrack()
    fun cancelSleep() = PlaybackExtras.cancelSleep()
    fun setFade(seconds: Int) = PlaybackExtras.setFade(seconds)

    // ================= streams =================

    fun playStream(info: StreamInfo) {
        val id = nextStreamId--
        val s = Song(
            id = id,
            uri = Uri.parse(info.url),
            title = info.title,
            artist = info.artist,
            album = "Stream",
            albumId = -1L,
            durationMs = info.durationMs,
            track = 0,
            dateAdded = 0L,
            isVideo = info.isVideo,
            streamPage = info.page,
            artUrl = info.thumbnail,
            streamMime = info.mime,
        )
        streams[id.toString()] = s
        playList(listOf(s), 0)
    }

    /** Downloads the stream that's playing, using the saved Uplink format of the right kind. */
    fun saveStream(song: Song) {
        val page = song.streamPage ?: return
        val p = loadUplinkPrefs()
        val format = when {
            song.isVideo && p.format.isVideo -> p.format
            song.isVideo -> DlFormat.MP4
            !p.format.isVideo -> p.format
            else -> DlFormat.MP3
        }
        startDownload(
            DownloadOptions(
                url = page,
                format = format,
                audioKbps = p.audioKbps,
                maxHeight = p.maxHeight,
                wholePlaylist = false,
                embed = p.embed,
                target = PlaylistTarget.None,
                queueWhenDone = false,
                extraArgs = "",
                subtitles = p.subtitles,
                subLangs = p.subLangs,
            ),
            remember = false,
        )
        post("Saving \"${song.title}\" with Uplink…")
    }

    // ================= playlists =================

    fun createPlaylist(name: String): String = PlaylistStore.create(name)
    fun renamePlaylist(id: String, name: String) = PlaylistStore.rename(id, name)
    fun deletePlaylist(id: String) = PlaylistStore.delete(id)
    fun addToPlaylist(id: String, songIds: List<Long>) = PlaylistStore.addSongs(id, songIds)
    fun removeFromPlaylist(id: String, songId: Long) = PlaylistStore.removeSong(id, songId)

    // ================= tag editor =================

    fun setMeta(song: Song, title: String, artist: String, album: String) {
        val raw = rawSongs.firstOrNull { it.id == song.id } ?: song
        MetaStore.set(
            song.id,
            MetaOverride(
                title = title.trim().takeIf { it.isNotBlank() && it != raw.title },
                artist = artist.trim().takeIf { it.isNotBlank() && it != raw.artist },
                album = album.trim().takeIf { it.isNotBlank() && it != raw.album },
            ),
        )
        post("Info saved")
    }

    fun resetMeta(song: Song) {
        MetaStore.clear(song.id)
        MetaStore.clearCover(song.artKey)
        post("Back to the file's own info")
    }

    fun setCover(song: Song, uri: Uri) {
        viewModelScope.launch {
            val ok = MetaStore.setCover(ctx, song.artKey, uri)
            post(if (ok) "Cover updated" else "Couldn't use that image")
        }
    }

    // ================= lyrics =================

    suspend fun loadLyrics(song: Song, allowOnline: Boolean): Lyrics? = withContext(Dispatchers.IO) {
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

    fun findLyricsOnline(song: Song) {
        viewModelScope.launch {
            post("Searching lrclib.net…")
            val text = LrcLib.find(song.title, song.artist, song.album, song.durationMs)
            if (text != null) {
                withContext(Dispatchers.IO) { LyricsStore.save(ctx, song.id, text) }
                _lyricsVersion.update { it + 1 }
                post("Lyrics found")
            } else {
                post("No lyrics found for this track")
            }
        }
    }

    fun saveLyrics(song: Song, text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LyricsStore.save(ctx, song.id, text) }
            _lyricsVersion.update { it + 1 }
        }
    }

    fun importLyrics(song: Song, uri: Uri) {
        viewModelScope.launch {
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
                _lyricsVersion.update { it + 1 }
                post("Lyrics imported")
            }
        }
    }

    fun removeLyrics(song: Song) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LyricsStore.delete(ctx, song.id) }
            _lyricsVersion.update { it + 1 }
        }
    }

    // ================= backup & M3U =================

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val byId = rawSongs.map { MetaStore.apply(it) }.associateBy { it.id }
                    val settings = JSONObject()
                        .put("accent", _accent.value)
                        .put("animate", _animate.value)
                        .put("fade", PlaybackExtras.fadeSeconds.value)
                        .put("lyricsAuto", _lyricsAuto.value)
                    val text = Backup.build(playlists.value, byId, LinkStore.all(), settings)
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            post(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = readText(uri) ?: throw IllegalStateException()
                val (lists, settings) = Backup.parse(text)
                var matched = 0
                var total = 0
                lists.forEach { pl ->
                    val ids = matchTracks(pl.tracks)
                    total += pl.tracks.size
                    matched += ids.size
                    val id = PlaylistStore.create(uniquePlaylistName(pl.name))
                    PlaylistStore.addSongs(id, ids)
                }
                settings?.let {
                    if (it.has("accent")) setAccent(it.optInt("accent", 0))
                    if (it.has("animate")) setAnimateBackground(it.optBoolean("animate", true))
                    if (it.has("fade")) setFade(it.optInt("fade", 0))
                    if (it.has("lyricsAuto")) setLyricsAuto(it.optBoolean("lyricsAuto", false))
                }
                post("Restored ${lists.size} playlist(s) · matched $matched of $total tracks")
            } catch (e: Exception) {
                post("That file isn't a Stellar Disc backup")
            }
        }
    }

    fun exportM3u(playlistId: String, uri: Uri) {
        viewModelScope.launch {
            val p = playlists.value.firstOrNull { it.id == playlistId } ?: return@launch
            val ok = withContext(Dispatchers.IO) {
                try {
                    val songs = p.songIds.mapNotNull { songById[it.toString()] }
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.buildM3u(songs).toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            post(if (ok) "Playlist exported" else "Couldn't export the playlist")
        }
    }

    fun importM3u(uri: Uri, fileName: String?) {
        viewModelScope.launch {
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
            val library = _songs.value
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
        val matches = SongMatcher.check(ctx, _songs.value, tracks, LinkStore.all())
        return matches.mapNotNull { if (it.kind != MatchKind.NEW) it.local?.id else null }
    }

    private fun uniquePlaylistName(name: String): String {
        val existing = playlists.value.map { it.name }.toSet()
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

    // ================= uplink & sharing =================

    fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        if (ShareText.looksLikeList(text)) {
            _sharedSwap.value = text
            return
        }
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        _sharedUrl.value = url
    }

    fun consumeSharedSwap() { _sharedSwap.value = null }

    fun consumeSharedUrl() { _sharedUrl.value = null }

    fun loadUplinkPrefs(): UplinkPrefs = UplinkPrefs(
        format = runCatching { DlFormat.valueOf(prefs.getString(KEY_DL_FORMAT, DlFormat.MP3.name)!!) }.getOrDefault(DlFormat.MP3),
        audioKbps = prefs.getInt(KEY_DL_KBPS, 320),
        maxHeight = prefs.getInt(KEY_DL_HEIGHT, 1080),
        wholePlaylist = prefs.getBoolean(KEY_DL_PLAYLIST, true),
        embed = prefs.getBoolean(KEY_DL_EMBED, true),
        queueWhenDone = prefs.getBoolean(KEY_DL_QUEUE, false),
        subtitles = prefs.getBoolean(KEY_DL_SUBS, true),
        subLangs = prefs.getString(KEY_DL_SUBLANGS, "en.*") ?: "en.*",
        skipExisting = prefs.getBoolean(KEY_DL_SKIP, true),
    )

    fun startDownload(options: DownloadOptions, remember: Boolean = true) {
        if (remember) {
            prefs.edit()
                .putString(KEY_DL_FORMAT, options.format.name)
                .putInt(KEY_DL_KBPS, options.audioKbps)
                .putInt(KEY_DL_HEIGHT, options.maxHeight)
                .putBoolean(KEY_DL_PLAYLIST, options.wholePlaylist)
                .putBoolean(KEY_DL_EMBED, options.embed)
                .putBoolean(KEY_DL_QUEUE, options.queueWhenDone)
                .putBoolean(KEY_DL_SUBS, options.subtitles)
                .putString(KEY_DL_SUBLANGS, options.subLangs)
                .putBoolean(KEY_DL_SKIP, options.skipExisting)
                .apply()
        }
        UplinkManager.enqueue(ctx, options)
    }

    // ================= settings =================

    fun setAccent(index: Int) {
        _accent.value = index
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    fun setAnimateBackground(on: Boolean) {
        _animate.value = on
        prefs.edit().putBoolean(KEY_ANIMATE, on).apply()
    }

    fun setLyricsAuto(on: Boolean) {
        _lyricsAuto.value = on
        prefs.edit().putBoolean(KEY_LYRICS_AUTO, on).apply()
    }

    fun setOnboarded() {
        _onboarded.value = true
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun post(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ================= internals =================

    private fun lookup(mediaId: String): Song? = songById[mediaId] ?: streams[mediaId]

    private fun sync(rebuildQueue: Boolean) {
        val c = controller ?: return
        if (rebuildQueue) cachedQueue = buildQueue(c)
        val current = c.currentMediaItem?.let { lookup(it.mediaId) ?: it.toSong() }
        val size = c.videoSize
        _state.value = PlayerState(
            current = current,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.takeIf { it > 0 } ?: (current?.durationMs ?: 0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = cachedQueue,
            currentIndex = c.currentMediaItemIndex,
            speed = c.playbackParameters.speed,
            videoWidth = size.width,
            videoHeight = size.height,
            hasSubtitles = c.currentTracks.containsType(C.TRACK_TYPE_TEXT),
            subtitlesOn = C.TRACK_TYPE_TEXT !in c.trackSelectionParameters.disabledTrackTypes,
        )
    }

    private fun buildQueue(c: MediaController): List<QueueEntry> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<QueueEntry>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            if (i < c.mediaItemCount) {
                val item = c.getMediaItemAt(i)
                out += QueueEntry(i, lookup(item.mediaId) ?: item.toSong())
            }
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    override fun onCleared() {
        _player.value = null
        controller?.release()
        controller = null
        super.onCleared()
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_ANIMATE = "animate_bg"
        const val KEY_LYRICS_AUTO = "lyrics_auto"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_MODERN_HOME = "modern_home"
        const val KEY_AUDIO_ONLY = "audio_only"
        const val KEY_BACKDROP = "player_backdrop"
        const val KEY_HERO_MODE = "hero_mode"
        const val KEY_CLEAR_VIEW = "clear_view"
        const val KEY_HIDDEN = "hidden_folders"
        const val KEY_DL_FORMAT = "dl_format"
        const val KEY_DL_KBPS = "dl_kbps"
        const val KEY_DL_HEIGHT = "dl_height"
        const val KEY_DL_PLAYLIST = "dl_playlist"
        const val KEY_DL_EMBED = "dl_embed"
        const val KEY_DL_QUEUE = "dl_queue"
        const val KEY_DL_SUBS = "dl_subs"
        const val KEY_DL_SUBLANGS = "dl_sublangs"
        const val KEY_DL_SKIP = "dl_skip_existing"
    }
}

/** What the Home hero card represents. */
enum class HeroMode(val label: String, val blurb: String) {
    TOP_PICK("Top pick", "Learned from when and how you listen"),
    MOST_PLAYED("Most played", "Your most played song of all time"),
    PLAYLIST("Playlist", "A pinned or favourite playlist"),
    RARE("Rare listen", "Something you've played only once or twice"),
    FORGOTTEN("Forgotten gem", "Loved once, not heard in a while"),
    FRESH("Fresh arrival", "Newest addition to your library"),
    RANDOM("Random", "A surprise every time you open Home"),
}
