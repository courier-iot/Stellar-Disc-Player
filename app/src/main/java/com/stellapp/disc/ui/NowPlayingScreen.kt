package com.stellapp.disc.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.stellapp.disc.PlayerState
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.Song
import com.stellapp.disc.playback.AudioFx
import com.stellapp.disc.playback.PlaybackExtras
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void
import kotlinx.coroutines.delay
import kotlin.math.sin

private enum class NpSheet { NONE, QUEUE, SLEEP, SPEED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    animate: Boolean,
    onClose: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val sleepAt by PlaybackExtras.sleepEndsAt.collectAsStateWithLifecycle()
    val sleepEnd by PlaybackExtras.sleepEndOfTrack.collectAsStateWithLifecycle()
    val audioOnly by vm.audioOnly.collectAsStateWithLifecycle()
    val backdrop by vm.backdrop.collectAsStateWithLifecycle()
    val song = s.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var sheet by remember { mutableStateOf(NpSheet.NONE) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    // Countdown text for the sleep chip.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sleepAt) {
        while (sleepAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val sleepLabel = when {
        sleepEnd -> "⏾ END OF TRACK"
        sleepAt != null -> "⏾ " + formatTime(((sleepAt ?: 0L) - now).coerceAtLeast(0L))
        else -> "⏾ SLEEP"
    }

    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        PlayerBackdrop(backdrop, animate = animate, playing = s.isPlaying, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = TextHi, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (song?.isStream == true) "STREAMING" else "NOW PLAYING",
                        color = primary,
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val pos = s.queue.indexOfFirst { it.index == s.currentIndex } + 1
                    if (s.queue.isNotEmpty()) {
                        Text("TRACK $pos / ${s.queue.size}", color = TextLo, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                }
                IconButton(onClick = { sheet = NpSheet.QUEUE }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", tint = TextHi)
                }
            }

            Spacer(Modifier.weight(1f))
            when {
                song != null && showLyrics -> LyricsPanel(vm, song, s.positionMs, Modifier.fillMaxWidth().aspectRatio(0.9f))
                song?.isVideo == true && !audioOnly -> Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .neonPanel(primary, glow = 18.dp, fill = Color.Black)
                        .clickable(onClick = onOpenVideo)
                ) {
                    VideoSurface(player, Modifier.fillMaxSize())
                    Text(
                        "⤢ TAP FOR FULLSCREEN",
                        color = TextHi,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Void.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                else -> OrbitArt(song, s.isPlaying, Modifier.fillMaxWidth(0.82f).aspectRatio(1f))
            }
            Spacer(Modifier.weight(1f))

            Text(
                song?.title ?: "NO SIGNAL",
                color = TextHi,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song?.let { "${it.artist}  //  ${it.album}" } ?: "",
                color = secondary,
                fontFamily = Mono,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Extras
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NeonChip("✦ ${backdrop.label.uppercase()}", false) { vm.cycleBackdrop() }
                NeonChip("LYRICS", showLyrics) { showLyrics = !showLyrics }
                NeonChip(sleepLabel, sleepAt != null || sleepEnd) { sheet = NpSheet.SLEEP }
                NeonChip("${s.speed}×", s.speed != 1f) { sheet = NpSheet.SPEED }
                if (song?.isVideo == true) NeonChip("♪ AUDIO ONLY", audioOnly) { vm.setAudioOnly(!audioOnly) }
                if (song?.isVideo == true && !audioOnly) NeonChip("⤢ FULLSCREEN", false, onOpenVideo)
                if (song?.isStream == true) NeonChip("⇣ SAVE", false) { vm.saveStream(song) }
            }

            Spacer(Modifier.height(12.dp))
            Visualizer(s.isPlaying, Modifier.fillMaxWidth().height(34.dp))
            Spacer(Modifier.height(6.dp))

            // Seek bar
            var dragging by remember { mutableStateOf<Float?>(null) }
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
                    inactiveTrackColor = primary.copy(alpha = 0.18f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime((shown * s.durationMs).toLong()), color = TextLo, fontFamily = Mono, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(s.durationMs), color = TextLo, fontFamily = Mono, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            Controls(vm, s, primary, secondary)
            Spacer(Modifier.height(16.dp))
        }
    }

    when (sheet) {
        NpSheet.QUEUE -> QueueSheet(vm, s) { sheet = NpSheet.NONE }
        NpSheet.SLEEP -> OptionSheet("SLEEP TIMER", onDismiss = { sheet = NpSheet.NONE }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    NeonChip("$m MIN", false) {
                        vm.setSleep(m)
                        sheet = NpSheet.NONE
                    }
                }
                NeonChip("END OF TRACK", sleepEnd) {
                    vm.setSleepEndOfTrack()
                    sheet = NpSheet.NONE
                }
                if (sleepAt != null || sleepEnd) {
                    NeonChip("CANCEL TIMER", false) {
                        vm.cancelSleep()
                        sheet = NpSheet.NONE
                    }
                }
            }
            Text("Music fades out over the last 15 seconds.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        NpSheet.SPEED -> OptionSheet("PLAYBACK SPEED", onDismiss = { sheet = NpSheet.NONE }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                    NeonChip("${sp}×", s.speed == sp) { vm.setSpeed(sp) }
                }
            }
            Text("Handy for podcasts and audiobooks. Pitch stays natural.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        NpSheet.NONE -> Unit
    }
}

@Composable
private fun Controls(vm: PlayerViewModel, s: PlayerState, primary: Color, secondary: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::toggleShuffle) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (s.shuffle) primary else TextLo)
        }
        IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextHi, modifier = Modifier.size(38.dp))
        }
        Box(
            Modifier
                .size(80.dp)
                .shadow(28.dp, CircleShape, ambientColor = primary, spotColor = primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, secondary)))
                .clickable { vm.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (s.isPlaying) "Pause" else "Play",
                tint = Void,
                modifier = Modifier.size(42.dp),
            )
        }
        IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextHi, modifier = Modifier.size(38.dp))
        }
        IconButton(onClick = vm::cycleRepeat) {
            Icon(
                if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (s.repeatMode != Player.REPEAT_MODE_OFF) primary else TextLo,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelSolid, contentColor = TextHi) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: PlayerViewModel, s: PlayerState, onDismiss: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelSolid, contentColor = TextHi) {
        Text(
            "QUEUE // ${s.queue.size} TRACKS",
            color = primary,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            if (s.shuffle) "Turn shuffle off to reorder." else "Use the arrows to reorder, ✕ to remove.",
            color = TextLo,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val start = (s.queue.indexOfFirst { it.index == s.currentIndex } - 2).coerceAtLeast(0)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(s.queue, key = { _, e -> e.index }) { pos, e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(
                            song = e.song,
                            active = e.index == s.currentIndex,
                            playing = s.isPlaying,
                            onClick = { vm.playQueueIndex(e.index) },
                        )
                    }
                    if (!s.shuffle) {
                        if (pos > 0) {
                            IconButton(onClick = { vm.moveQueueItem(e.index, e.index - 1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = TextLo)
                            }
                        }
                        if (pos < s.queue.lastIndex) {
                            IconButton(onClick = { vm.moveQueueItem(e.index, e.index + 1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = TextLo)
                            }
                        }
                    }
                    if (e.index != s.currentIndex) {
                        IconButton(onClick = { vm.removeQueueItem(e.index) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TextLo)
                        }
                    }
                }
            }
        }
    }
}

/** Album art inside spinning neon orbit rings with a moon circling it. Spins only while playing. */
@Composable
private fun OrbitArt(song: Song?, playing: Boolean, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(16_000, easing = LinearEasing))
            }
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p",
    )
    val glow = if (playing) pulse else 0.6f

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(primary.copy(alpha = 0.38f * glow), secondary.copy(alpha = 0.14f * glow), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
            rotate(rotation.value, pivot = center) {
                drawCircle(
                    color = primary.copy(alpha = 0.8f),
                    radius = r * 0.86f,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f))),
                )
                drawCircle(secondary, radius = 5.dp.toPx(), center = Offset(center.x + r * 0.86f, center.y))
                drawCircle(secondary.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(center.x + r * 0.86f, center.y))
            }
            rotate(-rotation.value * 0.6f, pivot = center) {
                val arcTopLeft = Offset(center.x - r * 0.95f, center.y - r * 0.95f)
                val arcSize = Size(r * 1.9f, r * 1.9f)
                val arcStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                drawArc(secondary.copy(alpha = 0.75f), 0f, 70f, false, arcTopLeft, arcSize, style = arcStroke)
                drawArc(primary.copy(alpha = 0.6f), 180f, 40f, false, arcTopLeft, arcSize, style = arcStroke)
            }
        }
        AlbumArt(
            song,
            Modifier
                .fillMaxSize(0.70f)
                .border(2.dp, Brush.sweepGradient(listOf(primary, secondary, primary)), CircleShape),
            shape = CircleShape,
        )
    }
}

/** Real spectrum when the visualizer is switched on in Settings, animated bars otherwise. */
@Composable
private fun Visualizer(playing: Boolean, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val spectrum by AudioFx.spectrum.collectAsStateWithLifecycle()
    val level by animateFloatAsState(if (playing) 1f else 0.06f, tween(600), label = "lvl")
    var t by remember { mutableFloatStateOf(0f) }
    val real = spectrum
    LaunchedEffect(playing, real == null) {
        if (!playing || real != null) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Canvas(modifier) {
        val bars = real?.size ?: 36
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val n = if (real != null) {
                real[i]
            } else {
                (sin(t * 3.1f + i * 0.7f) * 0.5f + 0.5f) * (sin(t * 1.7f + i * 0.23f) * 0.5f + 0.5f) +
                    0.25f * (sin(t * 7.3f + i * 1.9f) * 0.5f + 0.5f)
            }
            val h = size.height * (0.08f + 0.92f * n.coerceIn(0f, 1f) * level)
            drawRect(
                brush = Brush.verticalGradient(listOf(secondary, primary), startY = size.height - h, endY = size.height),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
            )
        }
    }
}
