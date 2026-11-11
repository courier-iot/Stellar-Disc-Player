package com.stellapp.disc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.stellapp.disc.ui.theme.Void
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val z: Float, val tint: Int)

/**
 * Drifting, twinkling starfield over two nebula clouds.
 * With [grid] on it adds a synthwave horizon: a glowing planet and a scrolling neon floor.
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
    val stars = remember {
        val r = Random(7)
        List(170) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextInt(10)) }
    }
    val currentSpeed by rememberUpdatedState(speed)
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time += (now - last) / 1_000_000_000f * currentSpeed
                last = now
            }
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val t = time

        drawRect(Void)
        val n1 = Offset(w * 0.1f, h * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.20f), Color.Transparent), center = n1, radius = w * 0.95f),
            radius = w * 0.95f,
            center = n1,
        )
        val n2 = Offset(w * 0.95f, h * 0.62f)
        drawCircle(
            brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.14f), Color.Transparent), center = n2, radius = w * 0.85f),
            radius = w * 0.85f,
            center = n2,
        )

        val horizon = if (grid) h * 0.72f else h
        for (s in stars) {
            val y = ((s.y + t * 0.012f * (0.25f + s.z)) % 1f) * h
            if (y > horizon) continue
            val twinkle = 0.65f + 0.35f * sin(t * (1.2f + s.z * 2.5f) + s.x * 40f)
            val color = when (s.tint) {
                0 -> primary
                1 -> secondary
                else -> Color.White
            }
            drawCircle(
                color = color.copy(alpha = ((0.2f + 0.7f * s.z) * twinkle).coerceIn(0f, 1f)),
                radius = (0.5f + s.z * 1.6f).dp.toPx(),
                center = Offset(s.x * w, y),
            )
        }

        if (grid) drawSynthGrid(primary, secondary, horizon, t)
    }
}

private fun DrawScope.drawSynthGrid(primary: Color, secondary: Color, horizon: Float, t: Float) {
    val w = size.width
    val h = size.height

    // Planet rising over the horizon.
    val sunR = w * 0.34f
    clipRect(bottom = horizon) {
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(secondary.copy(alpha = 0.55f), primary.copy(alpha = 0.25f)),
                startY = horizon - sunR,
                endY = horizon,
            ),
            radius = sunR,
            center = Offset(w / 2f, horizon),
        )
    }

    // Floor.
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF0A0618), Void), startY = horizon, endY = h),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

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
        drawLine(
            color = primary.copy(alpha = 0.05f + 0.45f * p),
            start = Offset(0f, yy),
            end = Offset(w, yy),
            strokeWidth = stroke,
        )
    }

    // Horizon glow.
    val glowH = 40.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, secondary.copy(alpha = 0.28f)), startY = horizon - glowH, endY = horizon),
        topLeft = Offset(0f, horizon - glowH),
        size = Size(w, glowH),
    )
    drawLine(secondary.copy(alpha = 0.9f), Offset(0f, horizon), Offset(w, horizon), 2.dp.toPx())
}
