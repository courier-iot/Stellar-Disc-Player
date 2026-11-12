package com.stellapp.disc.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.ui.theme.accentGradient
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Only one video view may show the picture at a time. Two live views (Now Playing underneath
 * fullscreen) fight over the same video after a rotation and leave a frozen frame poking
 * through. The view with the highest [priority] owns the picture (fullscreen beats the
 * Now Playing preview even when rotating rebuilds the preview); ties go to the newest.
 * The others let go and hide.
 */
private object VideoSurfaces {
    class Entry(val priority: Int)
    val stack = mutableStateListOf<Entry>()

    fun owner(): Entry? {
        var best: Entry? = null
        for (e in stack) if (best == null || e.priority >= best.priority) best = e
        return best
    }
}

/** Fullscreen video. Always takes the picture over the Now Playing preview. */
const val VIDEO_PRIORITY_FULLSCREEN = 10

/** The video picture for [player]. Subtitles are drawn on top automatically. */
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, zoom: Boolean = false, priority: Int = 0) {
    val me = remember(priority) { VideoSurfaces.Entry(priority) }
    DisposableEffect(me) {
        VideoSurfaces.stack.add(me)
        onDispose { VideoSurfaces.stack.remove(me) }
    }
    val active = VideoSurfaces.owner() === me
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view ->
            // Show the view before handing it the player, so its surface exists to draw on.
            // Hiding a view also drops its surface, so nothing stale is left behind.
            if (active) view.visibility = android.view.View.VISIBLE
            val want = if (active) player else null
            if (view.player !== want) {
                view.player = null
                view.player = want
            }
            if (!active) view.visibility = android.view.View.INVISIBLE
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Fullscreen video with auto-hiding controls.
 * Gestures: tap = show/hide controls, double-tap sides = skip 10s, double-tap middle = play/pause,
 * swipe up/down on the left = brightness, on the right = volume.
 */
@Composable
fun VideoPlayerScreen(vm: PlayerViewModel, inPip: Boolean, onClose: () -> Unit, onEnterPip: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val audio = remember { context.getSystemService(AudioManager::class.java) }

    var controls by remember { mutableStateOf(true) }
    var zoom by rememberSaveable { mutableStateOf(false) }
    var label by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    BackHandler(onBack = onClose)

    DisposableEffect(Unit) {
        vm.setVideoFullscreen(true)
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            vm.setVideoFullscreen(false)
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            view.keepScreenOn = false
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    // Rotate to match the video.
    LaunchedEffect(s.videoWidth, s.videoHeight) {
        if (s.videoWidth > 0 && s.videoHeight > 0) {
            activity?.requestedOrientation = if (s.videoWidth >= s.videoHeight) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }
    LaunchedEffect(controls, s.isPlaying, dragging) {
        if (controls && s.isPlaying && dragging == null) {
            delay(3_500)
            controls = false
        }
    }
    LaunchedEffect(label) {
        if (label != null) {
            delay(900)
            label = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls },
                    onDoubleTap = { o ->
                        val third = size.width / 3f
                        when {
                            o.x < third -> {
                                vm.seekBy(-10_000)
                                label = "⟲ 10s"
                            }
                            o.x > third * 2 -> {
                                vm.seekBy(10_000)
                                label = "10s ⟳"
                            }
                            else -> vm.togglePlay()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var left = true
                var start = 0f
                var moved = 0f
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        left = o.x < size.width / 2f
                        start = if (left) currentBrightness(activity) else currentVolume(audio)
                        moved = 0f
                    },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        moved += -dy / size.height
                        val v = (start + moved * 1.3f).coerceIn(0f, 1f)
                        if (left) {
                            setBrightness(activity, v)
                            label = "☀ ${(v * 100).roundToInt()}%"
                        } else {
                            setVolume(audio, v)
                            label = "♪ ${(v * 100).roundToInt()}%"
                        }
                    },
                )
            }
    ) {
        VideoSurface(player, Modifier.fillMaxSize(), zoom, priority = VIDEO_PRIORITY_FULLSCREEN)

        if (!inPip) {
            label?.let {
                Text(
                    it,
                    color = TextHi,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Void.copy(alpha = 0.65f), SmallCut)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }

            AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Void.copy(alpha = 0.75f), Color.Transparent, Color.Transparent, Void.copy(alpha = 0.85f))
                            )
                        )
                        .safeDrawingPadding()
                ) {
                    // Top bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.current?.title ?: "", color = TextHi, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.current?.artist ?: "", color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (s.hasSubtitles) {
                            IconButton(onClick = { vm.setSubtitles(!s.subtitlesOn) }) {
                                Icon(Icons.Rounded.ClosedCaption, contentDescription = "Subtitles", tint = if (s.subtitlesOn) primary else TextLo)
                            }
                        }
                        IconButton(onClick = { zoom = !zoom }) {
                            Icon(Icons.Rounded.AspectRatio, contentDescription = "Fit or fill", tint = if (zoom) primary else TextHi)
                        }
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture in picture", tint = TextHi)
                        }
                    }

                    // Middle controls
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextHi, modifier = Modifier.size(32.dp)) }
                        IconButton(onClick = { vm.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds", tint = TextHi, modifier = Modifier.size(32.dp)) }
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(accentGradient()))
                                .pointerInput(Unit) { detectTapGestures(onTap = { vm.togglePlay() }) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (s.isPlaying) "Pause" else "Play",
                                tint = Void,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        IconButton(onClick = { vm.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds", tint = TextHi, modifier = Modifier.size(32.dp)) }
                        IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextHi, modifier = Modifier.size(32.dp)) }
                    }

                    // Seek bar
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val frac = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
                        val shown = dragging ?: frac
                        Slider(
                            value = shown,
                            onValueChange = { dragging = it },
                            onValueChangeFinished = {
                                dragging?.let { vm.seekTo((it * s.durationMs).toLong()) }
                                dragging = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = primary,
                                activeTrackColor = primary,
                                inactiveTrackColor = primary.copy(alpha = 0.25f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(formatTime((shown * s.durationMs).toLong()), color = TextHi, fontFamily = Mono, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatTime(s.durationMs), color = TextHi, fontFamily = Mono, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun currentBrightness(activity: Activity?): Float {
    val attr = activity?.window?.attributes?.screenBrightness ?: -1f
    if (attr >= 0f) return attr
    return try {
        Settings.System.getInt(activity?.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

private fun setBrightness(activity: Activity?, v: Float) {
    val w = activity?.window ?: return
    val lp = w.attributes
    lp.screenBrightness = v.coerceAtLeast(0.01f)
    w.attributes = lp
}

private fun currentVolume(audio: AudioManager?): Float {
    val a = audio ?: return 0.5f
    val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun setVolume(audio: AudioManager?, v: Float) {
    val a = audio ?: return
    val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    a.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
}
