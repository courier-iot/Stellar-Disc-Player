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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.stellapp.disc.playback.AudioFx
import com.stellapp.disc.playback.PlaybackExtras
import com.stellapp.disc.playback.QueueStore
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
            .build()

        AudioFx.attach(player.audioSessionId)
        PlaybackExtras.attach(player)

        player.addListener(object : Player.Listener {
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
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                    QueueStore.saveQueue(this@PlaybackService, p)
                }
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED)) {
                    QueueStore.savePosition(this@PlaybackService, p)
                }
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    WidgetUpdater.push(this@PlaybackService, p)
                }
            }
        })

        QueueStore.restore(this, player)
        WidgetUpdater.push(this, player)

        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) QueueStore.savePosition(this@PlaybackService, player)
            }
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

    override fun onDestroy() {
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
