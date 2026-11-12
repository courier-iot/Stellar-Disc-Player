package com.stellapp.disc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.stellapp.disc.ui.theme.Void
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class Star(val x: Float, val y: Float, val z: Float, val tint: Int)

internal fun makeStars(count: Int, seed: Int = 7): List<Star> {
    val r = Random(seed)
    return List(count) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextInt(10)) }
}

/** True while Battery Saver is on: animations slow down and draw at a lower frame rate. */
val LocalCalmMotion = staticCompositionLocalOf { false }

/** Watches Android's Battery Saver. */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current.applicationContext
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var on by remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                on = pm.isPowerSaveMode
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

/**
 * Seconds of animation time; stands still when [animate] is off or the app isn't on screen.
 * With Battery Saver on it moves at half speed and updates about 20 times a second.
 */
@Composable
internal fun rememberSpaceTime(animate: Boolean, speed: Float): State<Float> {
    val currentSpeed by rememberUpdatedState(speed)
    val calm = LocalCalmMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifeState by lifecycle.currentStateFlow.collectAsState()
    val visible = lifeState.isAtLeast(Lifecycle.State.STARTED)
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, visible, calm) {
        if (!animate || !visible) return@LaunchedEffect
        var last = 0L
        var pending = 0f
        var frame = 0
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    pending += (now - last) / 1_000_000_000f * currentSpeed * (if (calm) 0.5f else 1f)
                    frame++
                    if (!calm || frame % 3 == 0) {
                        time.floatValue += pending
                        pending = 0f
                    }
                }
                last = now
            }
        }
    }
    return time
}

/**
 * Drifting, twinkling starfield over slowly moving nebula clouds, with the odd shooting star.
 * With [grid] on it adds the synthwave horizon: a banded neon sun and a scrolling floor.
 */
@Composable
fun Starfield(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    grid: Boolean = false,
    speed: Float = 1f,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val stars = remember { makeStars(190) }
    val time by rememberSpaceTime(animate, speed)

    Canvas(modifier) {
        val t = time
        drawRect(Void)
        drawNebula(primary, secondary, t)
        val horizon = if (grid) size.height * 0.72f else size.height
        drawStars(stars, primary, secondary, t, horizon)
        drawShootingStar(t, primary)
        if (grid) drawSynthGrid(primary, secondary, horizon, t)
    }
}

internal fun DrawScope.drawNebula(primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    val clouds = listOf(
        Triple(Offset(0.12f, 0.18f), secondary, 0.95f),
        Triple(Offset(0.92f, 0.62f), primary, 0.85f),
        Triple(Offset(0.55f, 0.35f), Color(0xFF7B5CFF), 0.6f),
        Triple(Offset(0.25f, 0.85f), primary, 0.5f),
    )
    clouds.forEachIndexed { i, (pos, color, scale) ->
        val drift = Offset(sin(t * 0.05f + i * 1.9f) * 0.05f, cos(t * 0.04f + i * 1.3f) * 0.04f)
        val c = Offset((pos.x + drift.x) * w, (pos.y + drift.y) * h)
        val r = w * scale
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.06f), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
        )
    }
}

internal fun DrawScope.drawStars(stars: List<Star>, primary: Color, secondary: Color, t: Float, horizon: Float) {
    val w = size.width
    val h = size.height
    for (s in stars) {
        val y = ((s.y + t * 0.012f * (0.25f + s.z)) % 1f) * h
        if (y > horizon) continue
        val twinkle = 0.6f + 0.4f * sin(t * (1.2f + s.z * 2.5f) + s.x * 40f)
        val color = when (s.tint) {
            0 -> primary
            1 -> secondary
            else -> Color.White
        }
        val center = Offset(s.x * w, y)
        val radius = (0.5f + s.z * 1.6f).dp.toPx()
        val alpha = ((0.2f + 0.7f * s.z) * twinkle).coerceIn(0f, 1f)
        if (s.z > 0.9f) {
            // The brightest stars get a glow and a little cross flare.
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.45f * twinkle), Color.Transparent), center = center, radius = radius * 6),
                radius = radius * 6,
                center = center,
            )
            val flare = radius * 5 * twinkle
            drawLine(color.copy(alpha = 0.5f * twinkle), Offset(center.x - flare, center.y), Offset(center.x + flare, center.y), 1f)
            drawLine(color.copy(alpha = 0.5f * twinkle), Offset(center.x, center.y - flare), Offset(center.x, center.y + flare), 1f)
        }
        drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center)
    }
}

/** A shooting star roughly every 7 seconds of animation time. */
internal fun DrawScope.drawShootingStar(t: Float, tint: Color) {
    val period = 7f
    val cycle = (t / period).toInt()
    val p = (t % period) / 0.9f // visible for the first 0.9s of each cycle
    if (p > 1f) return
    val r = Random(cycle * 31 + 5)
    val start = Offset(size.width * (0.2f + r.nextFloat() * 0.7f), size.height * r.nextFloat() * 0.4f)
    val dir = Offset(-0.55f, 0.35f)
    val len = size.width * 0.45f
    val head = Offset(start.x + dir.x * len * p, start.y + dir.y * len * p)
    val tail = Offset(head.x - dir.x * len * 0.35f, head.y - dir.y * len * 0.35f)
    val fade = if (p < 0.8f) 1f else (1f - p) / 0.2f
    drawLine(
        brush = Brush.linearGradient(listOf(Color.Transparent, tint.copy(alpha = 0.9f * fade)), start = tail, end = head),
        start = tail,
        end = head,
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(Color.White.copy(alpha = fade), radius = 1.8f.dp.toPx(), center = head)
}

private fun DrawScope.drawSynthGrid(primary: Color, secondary: Color, horizon: Float, t: Float) {
    val w = size.width
    val h = size.height

    // Banded retro sun rising over the horizon.
    val sunR = w * 0.34f
    val sunC = Offset(w / 2f, horizon)
    drawCircle(
        brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.35f), Color.Transparent), center = sunC, radius = sunR * 1.6f),
        radius = sunR * 1.6f,
        center = sunC,
    )
    clipRect(bottom = horizon) {
        val sun = Path().apply { addOval(Rect(sunC, sunR)) }
        clipPath(sun) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFE066), secondary, primary.copy(alpha = 0.7f)), startY = horizon - sunR, endY = horizon),
                topLeft = Offset(sunC.x - sunR, horizon - sunR),
                size = Size(sunR * 2, sunR),
            )
            // Gaps that get wider towards the horizon, slowly sliding down.
            val gaps = 7
            for (i in 0 until gaps) {
                val p = ((i + (t * 0.15f) % 1f) / gaps)
                val y = horizon - sunR * 0.55f + sunR * 0.55f * p
                val gh = (1f + 5f * p).dp.toPx()
                drawRect(Void, topLeft = Offset(sunC.x - sunR, y), size = Size(sunR * 2, gh))
            }
        }
    }

    // Depth: a faint far-off skyline, then two mountain ranges sliding at different speeds,
    // with haze between them.
    val far = Random(41)
    var bx = 0f
    while (bx < w) {
        val bw = w * (0.015f + far.nextFloat() * 0.03f)
        val bh = h * (0.01f + far.nextFloat() * 0.04f)
        drawRect(primary.copy(alpha = 0.10f), Offset(bx, horizon - bh), Size(bw, bh))
        bx += bw + w * far.nextFloat() * 0.01f
    }
    drawRidge(horizon, h * 0.09f, 7, t * 4f, lerpColor(Color(0xFF1A0B3A), secondary, 0.18f), primary.copy(alpha = 0.35f), 9)
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, secondary.copy(alpha = 0.16f)), startY = horizon - h * 0.08f, endY = horizon),
        topLeft = Offset(0f, horizon - h * 0.08f),
        size = Size(w, h * 0.08f),
    )
    drawRidge(horizon, h * 0.05f, 19, t * 11f, Color(0xFF0B0518), secondary.copy(alpha = 0.55f), 7)

    // Floor.
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF0A0618), Void), startY = horizon, endY = h),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )
    // The sun's reflection rippling on the floor.
    for (k in 0 until 16) {
        val f = k / 16f
        val yy = horizon + (h - horizon) * f * f + 2f
        val rw = sunR * (1.2f - f * 0.5f) * (0.75f + 0.25f * sin(t * 2.5f + k * 1.1f))
        drawRect(secondary.copy(alpha = 0.22f * (1f - f)), Offset(sunC.x - rw / 2, yy), Size(rw, (1f + f * 3f).dp.toPx()))
    }
    val stroke = 1.dp.toPx()
    for (i in -14..14) {
        drawLine(
            color = primary.copy(alpha = 0.28f),
            start = Offset(w / 2f + i * w * 0.02f, horizon),
            end = Offset(w / 2f + i * w * 0.16f, h),
            strokeWidth = stroke,
        )
    }
    val phase = (t * 0.6f) % 1f
    val rows = 12
    for (k in 0 until rows) {
        val p = (k + phase) / rows
        val yy = horizon + (h - horizon) * p * p
        drawLine(color = primary.copy(alpha = 0.05f + 0.45f * p), start = Offset(0f, yy), end = Offset(w, yy), strokeWidth = stroke * (1f + p))
    }

    // Horizon glow.
    val glowH = 40.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, secondary.copy(alpha = 0.28f)), startY = horizon - glowH, endY = horizon),
        topLeft = Offset(0f, horizon - glowH),
        size = Size(w, glowH),
    )
    drawLine(secondary.copy(alpha = 0.35f), Offset(0f, horizon), Offset(w, horizon), 8.dp.toPx())
    drawLine(lerpWhite(secondary), Offset(0f, horizon), Offset(w, horizon), 2.dp.toPx())
}

private fun lerpWhite(c: Color) = androidx.compose.ui.graphics.lerp(c, Color.White, 0.35f)

/** A mountain range along [base], scrolling sideways by [scroll] px and wrapping seamlessly. */
private fun DrawScope.drawRidge(base: Float, amp: Float, seed: Int, scroll: Float, fill: Color, rim: Color, peaks: Int) {
    val w = size.width
    val r = Random(seed)
    val heights = FloatArray(peaks) { r.nextFloat() }
    val seg = w / (peaks / 2f) / 2f
    val period = seg * peaks * 2
    val off = scroll % period
    val ridge = Path()
    var k = 0
    var x = -off
    ridge.moveTo(x, base)
    while (x < w + seg) {
        val hv = heights[(k / 2) % peaks]
        val y = if (k % 2 == 0) base - amp * (0.35f + 0.65f * hv) else base - amp * 0.12f * hv
        ridge.lineTo(x, y)
        k++
        x = -off + k * seg
    }
    ridge.lineTo(x, base)
    ridge.close()
    drawPath(ridge, fill)
    drawPath(ridge, rim, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f.dp.toPx()))
}

private fun lerpColor(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)
