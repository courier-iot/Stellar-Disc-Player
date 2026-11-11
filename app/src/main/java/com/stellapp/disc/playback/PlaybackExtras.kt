package com.stellapp.disc.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Sleep timer and smooth fades between tracks. Runs inside the playback service. */
@OptIn(UnstableApi::class)
object PlaybackExtras {
    private const val SLEEP_FADE_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var player: ExoPlayer? = null
    private var prefs: SharedPreferences? = null
    private var lastVolume = -1f

    private val _sleepEndsAt = MutableStateFlow<Long?>(null)
    /** SystemClock.elapsedRealtime() when playback stops, or null. */
    val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt.asStateFlow()

    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack.asStateFlow()

    private val _fadeSeconds = MutableStateFlow(0)
    /** 0 = off. */
    val fadeSeconds: StateFlow<Int> = _fadeSeconds.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && _sleepEndOfTrack.value) {
                cancelSleep()
            }
        }
    }

    fun init(context: Context) {
        val p = context.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE)
        prefs = p
        _fadeSeconds.value = p.getInt("fade_seconds", 0)
    }

    fun attach(p: ExoPlayer) {
        player = p
        p.addListener(listener)
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                tick()
                delay(100)
            }
        }
    }

    fun detach() {
        loop?.cancel()
        loop = null
        player?.removeListener(listener)
        player = null
    }

    fun setSleep(minutes: Int) {
        _sleepEndOfTrack.value = false
        player?.pauseAtEndOfMediaItems = false
        _sleepEndsAt.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    fun setSleepEndOfTrack() {
        _sleepEndsAt.value = null
        _sleepEndOfTrack.value = true
        player?.pauseAtEndOfMediaItems = true
    }

    fun cancelSleep() {
        _sleepEndsAt.value = null
        _sleepEndOfTrack.value = false
        player?.pauseAtEndOfMediaItems = false
    }

    fun setFade(seconds: Int) {
        _fadeSeconds.value = seconds
        prefs?.edit()?.putInt("fade_seconds", seconds)?.apply()
    }

    private fun tick() {
        val p = player ?: return
        var sleepFactor = 1f
        _sleepEndsAt.value?.let { end ->
            val left = end - SystemClock.elapsedRealtime()
            if (left <= 0) {
                p.pause()
                cancelSleep()
                setVolume(p, 1f)
                return
            } else if (left < SLEEP_FADE_MS && p.isPlaying) {
                sleepFactor = left.toFloat() / SLEEP_FADE_MS
            }
        }

        var fadeFactor = 1f
        val fade = _fadeSeconds.value * 1000L
        if (fade > 0 && p.isPlaying) {
            val dur = p.duration
            val pos = p.currentPosition
            if (dur > fade * 3) {
                if (pos < fade) fadeFactor = pos.toFloat() / fade
                val left = dur - pos
                if (left < fade && p.hasNextMediaItem()) fadeFactor = minOf(fadeFactor, left.toFloat() / fade)
            }
        }
        setVolume(p, (sleepFactor * fadeFactor).coerceIn(0f, 1f))
    }

    private fun setVolume(p: ExoPlayer, v: Float) {
        if (kotlin.math.abs(v - lastVolume) < 0.01f) return
        lastVolume = v
        p.volume = v
    }
}
