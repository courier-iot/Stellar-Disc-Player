package com.stellapp.disc.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellapp.disc.PlayerState
import com.stellapp.disc.data.Song
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.Panel
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void

val NeonCut = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
val SmallCut = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)

/** Dark glass panel with a neon edge and a coloured glow (glow shows on Android 9+). */
fun Modifier.neonPanel(
    color: Color,
    shape: Shape = NeonCut,
    glow: Dp = 10.dp,
    fill: Color = Panel,
): Modifier = this
    .shadow(glow, shape, ambientColor = color, spotColor = color)
    .clip(shape)
    .background(fill)
    .border(1.dp, Brush.linearGradient(listOf(color, color.copy(alpha = 0.2f))), shape)

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, shape: Shape = SmallCut) {
    val art = rememberAlbumArt(song)
    Box(modifier.clip(shape)) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlanetPlaceholder(seed = song?.artKey ?: 0L, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A little generated planet for tracks with no cover art. Each album gets its own colour. */
@Composable
fun PlanetPlaceholder(seed: Long, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val mix = remember(seed) { (((seed * 2654435761L) ushr 8) and 0xFF).toInt() / 255f }
    val tilt = remember(seed) { -35f + 50f * mix }
    val body = lerp(primary, secondary, mix)
    Canvas(modifier.background(Brush.linearGradient(listOf(Color(0xFF1A1238), Void)))) {
        // A few stars, placed per album so every cover is a little different.
        for (i in 0 until 14) {
            val h = (seed * 7919 + i * 104729).let { if (it < 0) -it else it }
            val x = (h % 1000) / 1000f * size.width
            val y = ((h / 1000) % 1000) / 1000f * size.height
            val big = i % 5 == 0
            drawCircle(
                color = (if (i % 3 == 0) secondary else Color.White).copy(alpha = if (big) 0.85f else 0.45f),
                radius = size.minDimension * if (big) 0.012f else 0.006f,
                center = Offset(x, y),
            )
        }
        drawNeonPlanet(
            center = center,
            r = size.minDimension * 0.24f,
            body = body,
            accent = lerp(secondary, primary, mix),
            tilt = tilt,
            seed = seed,
        )
    }
}

@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val heights = if (playing) listOf(a, b, c) else listOf(0.3f, 0.5f, 0.35f)
        val w = size.width / 5f
        heights.forEachIndexed { i, hFrac ->
            drawRect(
                color = color,
                topLeft = Offset(w * (i * 2), size.height * (1f - hFrac)),
                size = Size(w, size.height * hFrac),
            )
        }
    }
}

@Composable
fun NeonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        modifier = Modifier
            .clip(SmallCut)
            .then(
                if (selected) Modifier.background(primary)
                else Modifier.border(1.dp, primary.copy(alpha = 0.5f), SmallCut)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) Void else primary,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (active) primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            AlbumArt(song, Modifier.fillMaxSize())
            if (active) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(SmallCut)
                        .background(Void.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(playing, primary, Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (active) primary else TextHi,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = TextLo,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (song.isVideo) {
            Text(
                "VID",
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.secondary, SmallCut)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(formatTime(song.durationMs), color = TextLo, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
fun NeonTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .neonPanel(primary.copy(alpha = 0.6f), SmallCut, glow = 0.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextLo, fontFamily = Mono, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = TextHi, fontFamily = Mono, fontSize = 14.sp),
                cursorBrush = SolidColor(primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
        color = TextLo,
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = TextLo, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Void,
                checkedTrackColor = primary,
                uncheckedThumbColor = TextLo,
                uncheckedTrackColor = PanelSolid,
            ),
        )
    }
}

/** A tappable row in a bottom sheet. */
@Composable
fun SheetAction(label: String, trailing: String? = null, color: Color = TextHi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, color = TextLo, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
fun MiniPlayer(
    s: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val song = s.current ?: return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val progress = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .neonPanel(primary, glow = 14.dp, fill = PanelSolid.copy(alpha = 0.94f))
            .clickable(onClick = onOpen)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(primary.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(secondary, primary)))
            )
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(song, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (s.isPlaying) "Pause" else "Play",
                    tint = primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextHi)
            }
        }
    }
}
