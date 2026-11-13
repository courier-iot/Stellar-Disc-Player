package com.stellapp.disc

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.stellapp.disc.data.FavStore
import androidx.media3.session.MediaSessionService
import com.stellapp.disc.data.ListenKind
import com.stellapp.disc.data.TasteStore
import com.stellapp.disc.playback.AudioFx
import com.stellapp.disc.playback.PlaybackExtras
import com.stellapp.disc.playback.LongTrackMemory
import com.stellapp.disc.playback.QueueStore
import com.stellapp.disc.playback.Rotation
import com.stellapp.disc.data.toMediaItem
import com.stellapp.disc.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps music playing with the screen off or the app closed, and provides the
 * media notification, lock-screen controls and headphone/Bluetooth buttons.
 * Also owns resume, the equalizer hookup, sleep timer, fades and widget updates.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var errorStreak = 0
    /** A long track that just started, waiting to jump back to where you left it. */
    private var resumeCandidate: String? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    private fun rememberLongPosition(p: Player) {
        val id = p.currentMediaItem?.mediaId ?: return
        val dur = p.duration
        if (dur > 0) LongTrackMemory.remember(this, id, dur, p.currentPosition)
    }

    // Listening tracker for the taste engine.
    private var tId = -1L
    private var tListened = 0L
    private var tDuration = 0L
    private var tLastTick = 0L

    /** Called when a track stops being the current one. */
    private fun finishListen(natural: Boolean) {
        val id = tId
        val listened = tListened
        val dur = tDuration
        tId = -1
        tListened = 0
        if (id < 0 || (listened < 3_000 && !natural)) return
        val frac = if (dur > 0) listened.toFloat() / dur else 0f
        val kind = when {
            natural || frac >= 0.8f -> ListenKind.COMPLETE
            listened < 30_000 || frac < 0.3f -> ListenKind.SKIP
            else -> ListenKind.PARTIAL
        }
        TasteStore.record(id, kind, listened)
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // network, so streams keep going with the screen off
            .build()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .setCustomLayout(buttons(player))
            .build()
        // Keep the ♥ in step when you favourite from the app or the widget.
        scope.launch { FavStore.ids.collect { refreshButtons() } }

        AudioFx.attach(player.audioSessionId)
        PlaybackExtras.attach(player)

        tId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // A long track that played to the end starts from the top next time.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && tId >= 0 && tDuration >= LongTrackMemory.MIN_LENGTH_MS) {
                    LongTrackMemory.remember(this@PlaybackService, tId.toString(), tDuration, tDuration)
                }
                resumeCandidate = mediaItem?.mediaId
                finishListen(natural = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                tId = mediaItem?.mediaId?.toLongOrNull() ?: -1
                tDuration = 0
                // Random rotation: trim songs well behind you and deal new ones onto the end.
                if (Rotation.active && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    scope.launch { Rotation.onTrackChanged(player) }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                // A missing or broken file: skip it instead of stopping everything.
                errorStreak++
                if (errorStreak <= 5 && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onEvents(p: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && p.isPlaying) errorStreak = 0
                // Long tracks: pick up where you left off, and remember the spot when pausing.
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && p.playbackState == Player.STATE_READY) {
                    val id = resumeCandidate
                    if (id != null && id == p.currentMediaItem?.mediaId) {
                        resumeCandidate = null
                        val saved = LongTrackMemory.recall(this@PlaybackService, id)
                        if (saved != null && p.duration >= LongTrackMemory.MIN_LENGTH_MS && p.currentPosition < 5_000) p.seekTo(saved)
                    }
                }
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !p.isPlaying) rememberLongPosition(p)
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                    QueueStore.saveQueue(this@PlaybackService, p)
                }
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED)) {
                    QueueStore.savePosition(this@PlaybackService, p)
                }
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    WidgetUpdater.push(this@PlaybackService, p)
                }
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                    refreshButtons()
                }
            }
        })

        QueueStore.restore(this, player)
        WidgetUpdater.push(this, player)
        watchBluetooth(player)

        // The random rotation keeps refilling even if playback restarts without the app being
        // opened (car or headphone buttons after the phone closed the app): load the library here.
        if (!Rotation.hasPool && Rotation.wasSaved(this)) {
            scope.launch {
                val items = try {
                    val hidden = getSharedPreferences("stellar_disc", MODE_PRIVATE).getStringSet("hidden_folders", emptySet()) ?: emptySet()
                    com.stellapp.disc.data.MusicRepository.loadSongs(this@PlaybackService, allVideos = false)
                        .filter { !it.isVideo && it.folder !in hidden }
                        .map { com.stellapp.disc.data.MetaStore.apply(it).toMediaItem() }
                } catch (e: Exception) {
                    emptyList()
                }
                if (items.isNotEmpty() && !Rotation.hasPool) Rotation.resumeIfSaved(this@PlaybackService, items)
            }
        }

        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) {
                    QueueStore.savePosition(this@PlaybackService, player)
                    rememberLongPosition(player)
                }
            }
        }
        scope.launch {
            tLastTick = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000)
                val now = android.os.SystemClock.elapsedRealtime()
                if (player.isPlaying) {
                    tListened += now - tLastTick
                    player.duration.takeIf { it > 0 }?.let { tDuration = it }
                }
                tLastTick = now
            }
        }
    }

    /** Bluetooth auto-play: when headphones or a car connect, start playing (if switched on). */
    private fun watchBluetooth(player: Player) {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val btTypes = buildSet {
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        var primed = false
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                // Android reports already-connected devices right after registering: ignore that first call.
                if (!primed) {
                    primed = true
                    return
                }
                val on = getSharedPreferences("stellar_disc", MODE_PRIVATE).getBoolean("bt_autoplay", false)
                if (!on || addedDevices.isNullOrEmpty()) return
                if (addedDevices.any { it.isSink && it.type in btTypes } && !player.isPlaying && player.mediaItemCount > 0) {
                    // Give the connection a moment to settle so the first second isn't lost.
                    scope.launch {
                        delay(1_200)
                        if (player.playbackState == Player.STATE_IDLE) player.prepare()
                        player.play()
                    }
                }
            }
        }
        am.registerAudioDeviceCallback(cb, android.os.Handler(mainLooper))
        audioDeviceCallback = cb
    }

    // ================= notification & lock-screen buttons =================

    /** The current song's library id, or null for streams and nothing playing. */
    private fun favouriteId(p: Player): Long? {
        val item = p.currentMediaItem ?: return null
        val scheme = item.localConfiguration?.uri?.scheme
        if (scheme == "http" || scheme == "https") return null
        return item.mediaId.toLongOrNull()?.takeIf { it >= 0 }
    }

    private fun buttons(p: Player): List<CommandButton> {
        val favId = favouriteId(p)
        val fav = favId != null && FavStore.isFav(favId)
        val list = mutableListOf<CommandButton>()
        if (favId != null) {
            list += CommandButton.Builder()
                .setDisplayName(if (fav) "Remove from favourites" else "Add to favourites")
                .setIconResId(if (fav) R.drawable.ic_n_fav else R.drawable.ic_n_fav_off)
                .setSessionCommand(CMD_FAV)
                .build()
        }
        list += CommandButton.Builder()
            .setDisplayName(if (p.shuffleModeEnabled) "Shuffle off" else "Shuffle on")
            .setIconResId(if (p.shuffleModeEnabled) R.drawable.ic_n_shuffle_on else R.drawable.ic_n_shuffle_off)
            .setSessionCommand(CMD_SHUFFLE)
            .build()
        return list
    }

    private fun refreshButtons() {
        val s = session ?: return
        s.setCustomLayout(buttons(s.player))
        WidgetUpdater.push(this, s.player)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(CMD_FAV)
                .add(CMD_SHUFFLE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            val p = session.player
            when (customCommand.customAction) {
                ACTION_FAV -> favouriteId(p)?.let { FavStore.toggle(it) }
                ACTION_SHUFFLE -> p.shuffleModeEnabled = !p.shuffleModeEnabled
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            refreshButtons()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Songs asked for by name: "play Bohemian Rhapsody" from Stella, Google Assistant, a car or a watch
         * arrives as an item with only a search query, and is looked up in the library here.
         * Items that already point at a file (everything the app itself plays) pass straight through.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<androidx.media3.common.MediaItem>,
        ): ListenableFuture<MutableList<androidx.media3.common.MediaItem>> {
            if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems)
            val asked = mediaItems.firstOrNull { it.localConfiguration == null } ?: return Futures.immediateFuture(mediaItems)
            val query = asked.requestMetadata.searchQuery
                ?: asked.mediaMetadata.title?.toString()
                ?: ""
            val focus = asked.requestMetadata.extras?.getString("android.intent.extra.focus")
            val result = SettableFuture.create<MutableList<androidx.media3.common.MediaItem>>()
            scope.launch {
                val hit = try {
                    com.stellapp.disc.data.VoiceSearch.find(this@PlaybackService, query, focus)
                } catch (e: Exception) {
                    null
                }
                val items = hit?.items.orEmpty().take(500).map { it.toMediaItem() }
                if (items.isEmpty()) {
                    result.setException(UnsupportedOperationException("Nothing in your library matches \"$query\""))
                } else {
                    Rotation.stop(this@PlaybackService)
                    result.set(items.toMutableList())
                }
            }
            return result
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player != null) QueueStore.savePosition(this, player)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    companion object {
        const val ACTION_FAV = "com.stellapp.disc.FAVOURITE"
        const val ACTION_SHUFFLE = "com.stellapp.disc.SHUFFLE"
        private val CMD_FAV = SessionCommand(ACTION_FAV, android.os.Bundle.EMPTY)
        private val CMD_SHUFFLE = SessionCommand(ACTION_SHUFFLE, android.os.Bundle.EMPTY)
    }

    override fun onDestroy() {
        finishListen(natural = false)
        audioDeviceCallback?.let { (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).unregisterAudioDeviceCallback(it) }
        session?.player?.let { rememberLongPosition(it) }
        session?.player?.let { QueueStore.savePosition(this, it) }
        scope.cancel()
        PlaybackExtras.detach()
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        WidgetUpdater.pushStopped(this)
        super.onDestroy()
    }
}
