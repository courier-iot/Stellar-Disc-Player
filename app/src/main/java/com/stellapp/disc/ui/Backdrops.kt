package com.stellapp.disc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.stellapp.disc.ui.theme.Void
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Scenes for the now-playing screen. All drawn in code, so they ship inside the app. */
enum class Backdrop(val label: String) {
    SYNTHWAVE("Synthwave"),
    NEBULA("Nebula"),
    WARP("Warp drive"),
    AURORA("Aurora"),
    BLACK_HOLE("Black hole"),
    RINGWORLD("Ringworld"),
    NEON_CITY("Neon city"),
    VOID("Deep void"),
}

@Composable
fun PlayerBackdrop(kind: Backdrop, animate: Boolean, playing: Boolean, modifier: Modifier = Modifier) {
    if (kind == Backdrop.SYNTHWAVE) {
        Starfield(modifier, animate = animate, grid = true, speed = if (playing) 1.6f else 0.3f)
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val stars = remember { makeStars(220, seed = 11) }
    val city = remember { makeSkyline() }
    val time by rememberSpaceTime(animate, if (playing) 1.4f else 0.35f)

    Canvas(modifier) {
        val t = time
        drawRect(Void)
        when (kind) {
            Backdrop.NEBULA -> drawNebulaScene(stars, primary, secondary, t)
            Backdrop.WARP -> drawWarp(stars, primary, secondary, t)
            Backdrop.AURORA -> drawAurora(stars, primary, secondary, t)
            Backdrop.BLACK_HOLE -> drawBlackHole(stars, primary, secondary, t)
            Backdrop.RINGWORLD -> drawRingworld(stars, primary, secondary, t)
            Backdrop.NEON_CITY -> drawNeonCity(stars, city, primary, secondary, t)
            Backdrop.VOID -> {
                drawStars(stars.take(90), primary, secondary, t * 0.3f, size.height)
                drawShootingStar(t, primary)
            }
            Backdrop.SYNTHWAVE -> Unit
        }
    }
}

// ---------- Nebula: layered glowing gas, dense stars ----------

private fun DrawScope.drawNebulaScene(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    val purple = Color(0xFF7B5CFF)
    val pink = Color(0xFFFF3EA5)
    val palette = listOf(primary, secondary, purple, pink, primary, secondary, purple)
    for (i in palette.indices) {
        val a = t * 0.03f * (if (i % 2 == 0) 1 else -1) + i * 0.9f
        val c = Offset(w * (0.5f + 0.32f * cos(a)), h * (0.42f + 0.26f * sin(a * 1.3f)))
        val r = w * (0.45f + 0.12f * sin(t * 0.07f + i))
        drawCircle(
            brush = Brush.radialGradient(listOf(palette[i].copy(alpha = 0.22f), palette[i].copy(alpha = 0.05f), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
        )
    }
    // Dark dust lane across the middle.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Void.copy(alpha = 0.55f), Color.Transparent),
            startY = h * 0.38f, endY = h * 0.58f,
        ),
    )
    drawStars(stars, primary, secondary, t * 0.4f, h)
    drawShootingStar(t, secondary)
}

// ---------- Warp: stars streak outwards from the centre ----------

private fun DrawScope.drawWarp(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val c = Offset(size.width / 2f, size.height * 0.4f)
    val maxR = size.maxDimension * 0.8f
    drawCircle(
        brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.35f), secondary.copy(alpha = 0.08f), Color.Transparent), center = c, radius = maxR * 0.5f),
        radius = maxR * 0.5f,
        center = c,
    )
    stars.forEachIndexed { i, s ->
        val angle = s.x * 2 * PI.toFloat()
        val p = ((s.y + t * (0.12f + s.z * 0.25f)) % 1f)
        val d = p * p * maxR
        val len = (8f + 70f * p * p) * (0.5f + s.z)
        val dir = Offset(cos(angle), sin(angle))
        val head = c + dir * d
        val tail = c + dir * (d - len).coerceAtLeast(0f)
        val color = if (i % 3 == 0) secondary else if (i % 3 == 1) primary else Color.White
        drawLine(color.copy(alpha = (0.15f + 0.85f * p).coerceIn(0f, 1f)), tail, head, (0.6f + 2f * p).dp.toPx(), StrokeCap.Round)
    }
    // Tunnel rings rushing past.
    for (k in 0 until 5) {
        val p = ((k / 5f + t * 0.2f) % 1f)
        drawCircle(primary.copy(alpha = 0.18f * p), radius = p * p * maxR, center = c, style = Stroke(1.5f.dp.toPx()))
    }
}

// ---------- Aurora: flowing curtains over a dark ridge ----------

private fun DrawScope.drawAurora(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    drawStars(stars.take(140), primary, secondary, t * 0.2f, h * 0.75f)
    val curtains = listOf(primary, Color(0xFF39FF9A), secondary)
    curtains.forEachIndexed { ci, color ->
        val baseY = h * (0.28f + ci * 0.09f)
        val steps = 48
        for (j in 0..steps) {
            val x = w * j / steps
            val wave = sin(x / w * 6f + t * 0.5f + ci * 1.7f) * h * 0.05f + sin(x / w * 13f - t * 0.8f) * h * 0.015f
            val top = baseY + wave
            val shimmer = 0.5f + 0.5f * sin(t * 1.5f + j * 0.7f + ci)
            drawLine(
                brush = Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.28f * shimmer), color.copy(alpha = 0.04f)), startY = top - h * 0.12f, endY = top + h * 0.18f),
                start = Offset(x, top - h * 0.12f),
                end = Offset(x, top + h * 0.18f),
                strokeWidth = w / steps + 1f,
            )
        }
    }
    // Mountain ridge.
    val ridge = Path().apply {
        moveTo(0f, h)
        val r = Random(3)
        var x = 0f
        lineTo(0f, h * 0.8f)
        while (x < w) {
            x += w * (0.06f + r.nextFloat() * 0.08f)
            lineTo(x, h * (0.72f + r.nextFloat() * 0.1f))
        }
        lineTo(w, h)
        close()
    }
    drawPath(ridge, Color(0xFF05030C))
    drawPath(ridge, primary.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
}

// ---------- Black hole: lensed accretion disk ----------

private fun DrawScope.drawBlackHole(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val c = Offset(w / 2f, size.height * 0.38f)
    val r = w * 0.16f
    // Stars get pulled into a slow swirl around the hole.
    stars.forEach { s ->
        val baseA = s.x * 2 * PI.toFloat()
        val dist = r * 1.6f + s.y * size.maxDimension * 0.7f
        val a = baseA + t * 0.15f * (r * 3 / dist)
        val pos = c + Offset(cos(a), sin(a)) * dist
        val alpha = ((0.15f + 0.6f * s.z) * (0.6f + 0.4f * sin(t * 2 + s.x * 30))).coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = alpha), (0.5f + s.z * 1.3f).dp.toPx(), pos)
    }
    val hot = Color(0xFFFFC46B)
    drawCircle(
        brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.4f), primary.copy(alpha = 0.1f), Color.Transparent), center = c, radius = r * 4.5f),
        radius = r * 4.5f,
        center = c,
    )
    // Back half of the disk (lensed up over the top).
    rotate(-12f, c) {
        drawArc(
            brush = Brush.sweepGradient(listOf(hot, secondary, primary, hot), center = c),
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = c - Offset(r * 2.6f, r * 0.7f), size = Size(r * 5.2f, r * 1.4f),
            style = Stroke(r * 0.35f), alpha = 0.7f,
        )
    }
    // Photon ring + lensed halo arc.
    drawCircle(hot.copy(alpha = 0.9f), r * 1.08f, c, style = Stroke(2.dp.toPx()))
    drawCircle(secondary.copy(alpha = 0.45f), r * 1.25f, c, style = Stroke(r * 0.18f))
    drawCircle(Color.Black, r, c)
    // Front half of the disk crossing in front.
    rotate(-12f, c) {
        val spin = (t * 40f) % 360f
        for (band in 0 until 3) {
            val k = 1f + band * 0.18f
            drawArc(
                brush = Brush.sweepGradient(listOf(hot, primary, secondary, hot), center = c),
                startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = c - Offset(r * 2.2f * k, r * 0.5f * k), size = Size(r * 4.4f * k, r * 1.0f * k),
                style = Stroke(r * (0.22f - band * 0.05f)), alpha = 0.85f - band * 0.2f,
            )
        }
        // Bright clumps orbiting in the disk.
        for (i in 0 until 6) {
            val a = (spin + i * 60f) * PI.toFloat() / 180f
            val p = c + Offset(cos(a) * r * 2.3f, sin(a) * r * 0.55f)
            if (sin(a) > 0) drawCircle(Color.White.copy(alpha = 0.8f), 2.dp.toPx(), p)
        }
    }
}

// ---------- Ringworld: a big ringed planet with orbiting moons ----------

private fun DrawScope.drawRingworld(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    drawNebula(primary, secondary, t)
    drawStars(stars, primary, secondary, t * 0.25f, h)
    val c = Offset(w * 0.55f, h * 0.36f)
    val r = w * 0.26f
    val moons = listOf(Triple(1.9f, 0.35f, secondary), Triple(2.5f, -0.22f, Color(0xFF7B5CFF)), Triple(3.1f, 0.15f, primary))
    // Moons behind the planet first (sin < 0), then planet, then moons in front.
    fun moonPos(i: Int): Pair<Offset, Boolean> {
        val (orbit, speed, _) = moons[i]
        val a = t * speed + i * 2.1f
        return Pair(c + Offset(cos(a) * r * orbit, sin(a) * r * orbit * 0.32f), sin(a) > 0)
    }
    for (i in moons.indices) {
        val (p, front) = moonPos(i)
        if (!front) drawMoon(p, r * 0.07f * (1 + i * 0.3f), moons[i].third)
    }
    drawNeonPlanet(
        center = c, r = r,
        body = lerp(Color(0xFF1B0F3A), secondary, 0.35f),
        accent = primary, tilt = -16f, seed = 42L, phase = t * 0.08f,
    )
    for (i in moons.indices) {
        val (p, front) = moonPos(i)
        if (front) drawMoon(p, r * 0.07f * (1 + i * 0.3f), moons[i].third)
    }
    // A distant small planet.
    drawNeonPlanet(Offset(w * 0.16f, h * 0.7f), w * 0.06f, Color(0xFF0E2A3A), secondary, 20f, 9L, t * 0.12f, ring = false)
}

private fun DrawScope.drawMoon(p: Offset, r: Float, glow: Color) {
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.4f), Color.Transparent), center = p, radius = r * 3), r * 3, p)
    drawCircle(Brush.radialGradient(listOf(Color.White, glow, Color(0xFF120A24)), center = p - Offset(r * 0.4f, r * 0.4f), radius = r * 1.6f), r, p)
}

// ---------- Neon city: skyline with flickering windows and rain ----------

internal data class Tower(val x: Float, val w: Float, val h: Float, val seed: Int, val layer: Int)

internal fun makeSkyline(): List<Tower> {
    val r = Random(21)
    val out = mutableListOf<Tower>()
    for (layer in 0..1) {
        var x = -0.02f
        while (x < 1f) {
            val tw = 0.05f + r.nextFloat() * 0.08f
            val th = if (layer == 0) 0.18f + r.nextFloat() * 0.2f else 0.1f + r.nextFloat() * 0.22f
            out += Tower(x, tw, th, r.nextInt(), layer)
            x += tw + r.nextFloat() * 0.01f
        }
    }
    return out
}

private fun DrawScope.drawNeonCity(stars: List<Star>, city: List<Tower>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    val ground = h * 0.86f
    drawRect(Brush.verticalGradient(listOf(Void, Color(0xFF1A0730), secondary.copy(alpha = 0.35f)), startY = 0f, endY = ground))
    drawStars(stars.take(80), primary, secondary, t * 0.15f, h * 0.45f)
    // Moon with a neon halo.
    val moon = Offset(w * 0.78f, h * 0.18f)
    drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha = 0.35f), Color.Transparent), center = moon, radius = w * 0.2f), w * 0.2f, moon)
    drawCircle(Color(0xFFFFE6F4), w * 0.06f, moon)

    for (tower in city) {
        val left = tower.x * w
        val tw = tower.w * w
        val top = ground - tower.h * h * (if (tower.layer == 0) 1.6f else 1f)
        val body = if (tower.layer == 0) Color(0xFF120A26) else Color(0xFF07040F)
        drawRect(body, Offset(left, top), Size(tw, ground - top))
        val edge = if (tower.seed % 2 == 0) primary else secondary
        drawLine(edge.copy(alpha = if (tower.layer == 0) 0.25f else 0.6f), Offset(left, top), Offset(left + tw, top), 1.5f.dp.toPx())
        if (tower.layer == 1) {
            // Windows.
            val r = Random(tower.seed)
            val cell = 7.dp.toPx()
            var y = top + cell
            while (y < ground - cell) {
                var x = left + cell * 0.6f
                while (x < left + tw - cell * 0.6f) {
                    val on = r.nextFloat() < 0.35f
                    val flicker = sin(t * (0.5f + r.nextFloat() * 2f) + r.nextFloat() * 10f) > -0.6f
                    if (on && flicker) {
                        val wc = if (r.nextBoolean()) primary else Color(0xFFFFD27A)
                        drawRect(wc.copy(alpha = 0.75f), Offset(x, y), Size(cell * 0.45f, cell * 0.55f))
                    }
                    x += cell
                }
                y += cell
            }
            // Antenna beacon on the tall ones.
            if (tower.h > 0.24f) {
                val bx = left + tw / 2
                drawLine(edge, Offset(bx, top), Offset(bx, top - 14.dp.toPx()), 1.dp.toPx())
                val blink = (sin(t * 3f + tower.seed) + 1) / 2
                drawCircle(Color(0xFFFF4060).copy(alpha = blink), 2.5f.dp.toPx(), Offset(bx, top - 14.dp.toPx()))
            }
        }
    }
    // Wet street reflecting the glow.
    drawRect(Brush.verticalGradient(listOf(secondary.copy(alpha = 0.4f), Void), startY = ground, endY = h), Offset(0f, ground), Size(w, h - ground))
    drawLine(primary, Offset(0f, ground), Offset(w, ground), 2.dp.toPx())
    // Rain.
    val rain = Random(77)
    repeat(70) {
        val x = rain.nextFloat() * w
        val y = ((rain.nextFloat() + t * (0.5f + rain.nextFloat() * 0.4f)) % 1f) * h
        drawLine(primary.copy(alpha = 0.22f), Offset(x, y), Offset(x - 3.dp.toPx(), y + 14.dp.toPx()), 1f)
    }
}
