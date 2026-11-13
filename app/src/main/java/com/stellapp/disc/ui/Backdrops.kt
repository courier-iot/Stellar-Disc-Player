package com.stellapp.disc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stellapp.disc.R
import com.stellapp.disc.ui.theme.Void
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Scenes for the now-playing screen. Drawn ones are code; photo ones ship as images in the app. */
enum class Backdrop(val label: String, val photo: PhotoSpec? = null) {
    SYNTHWAVE("Synthwave"),
    MOONWAVE("Moonwave"),
    CARINA("Carina nebula", PhotoSpec(R.drawable.bd_nebula, R.drawable.bd_nebula_thumb, drift = 1f, fx = PhotoFx.SPACE)),
    GALAXY("Spiral galaxy", PhotoSpec(R.drawable.bd_galaxy, R.drawable.bd_galaxy_thumb, drift = 1f, fx = PhotoFx.SPACE)),
    DEEP_FIELD("Deep field", PhotoSpec(R.drawable.bd_deepfield, R.drawable.bd_deepfield_thumb, drift = 0.8f, fx = PhotoFx.SPACE)),
    RINGWORLD("Saturn", PhotoSpec(R.drawable.bd_saturn, R.drawable.bd_saturn_thumb, fit = PhotoFit.FIT_WIDTH, fx = PhotoFx.SPACE, bg = Color(0xFF04050A))),
    SATURN_CLOSE("Saturn close-up", PhotoSpec(R.drawable.bd_saturn_close, R.drawable.bd_saturn_close_thumb, drift = 1f, fx = PhotoFx.SPACE, bg = Color(0xFF04050A))),
    ECLIPSE(
        "Eclipse",
        PhotoSpec(
            R.drawable.bd_eclipse, R.drawable.bd_eclipse_thumb, fit = PhotoFit.ANCHOR, fx = PhotoFx.CORONA,
            bg = Color(0xFF01040D), featureX = 348f, featureY = 487f, featureR = 127f,
        ),
    ),
    PRIMORDIAL("Primordial black holes", PhotoSpec(R.drawable.bd_pbh, R.drawable.bd_pbh_thumb, drift = 0.8f)),
    BLACK_HOLE("Black hole"),
    PULSAR("Pulsar"),
    NEBULA("Nebula"),
    AURORA("Aurora"),
    WARP("Warp drive"),
    WARP_PHOTO("Warp drive (photo)", PhotoSpec(R.drawable.bd_warp_photo, R.drawable.bd_warp_photo_thumb, fx = PhotoFx.ZOOM)),
    ISS_WINDOW("ISS window", PhotoSpec(R.drawable.bd_iss, R.drawable.bd_iss_thumb, fit = PhotoFit.FIT_WIDTH, fx = PhotoFx.GLARE)),
    COCKPIT("Fighter cockpit"),
    NEON_CITY("Neon city", PhotoSpec(R.drawable.bd_neoncity, R.drawable.bd_neoncity_thumb, fx = PhotoFx.RAIN)),
    KABUKICHO("Kabukicho", PhotoSpec(R.drawable.bd_kabukicho, R.drawable.bd_kabukicho_thumb, fx = PhotoFx.NEON)),
    NIGHT_TRAIN("Night train", PhotoSpec(R.drawable.bd_night_train, R.drawable.bd_night_train_thumb, fx = PhotoFx.GRAIN)),
    TOKYO_ABOVE("Tokyo from above", PhotoSpec(R.drawable.bd_tokyo_above, R.drawable.bd_tokyo_above_thumb, drift = 0.6f)),
    CUSTOM("Your photo"),
}

private val CustomSpec = PhotoSpec(0, 0, drift = 0.6f, fx = PhotoFx.SPACE)

/**
 * Draws [kind] full screen. [anchor] is where the centre disc sits (photo scenes line up with it).
 * [thumbnail] uses small preview images, for the Settings picker.
 */
@Composable
fun PlayerBackdrop(
    kind: Backdrop,
    animate: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
    anchor: DiscAnchor? = null,
    thumbnail: Boolean = false,
) {
    if (com.stellapp.disc.ui.theme.ThemeState.ink && !thumbnail) {
        InkPaper(modifier)
        return
    }
    if (kind == Backdrop.SYNTHWAVE) {
        Starfield(modifier, animate = animate, grid = true, speed = if (playing) 1.6f else 0.3f)
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val stars = remember { makeStars(220, seed = 11) }
    val city = remember { makeSkyline() }
    val time by rememberSpaceTime(animate, if (playing) 1.4f else 0.35f)

    if (kind.photo != null || kind == Backdrop.CUSTOM) {
        val spec = kind.photo ?: CustomSpec
        val customVersion by CustomBackdrop.version.collectAsState()
        val context = LocalContext.current
        LaunchedEffect(Unit) { if (CustomBackdrop.version.value < 0) CustomBackdrop.refresh(context) }
        val key = if (kind == Backdrop.CUSTOM) "custom:$customVersion:$thumbnail" else "res:${if (thumbnail) spec.thumb else spec.full}"
        val img = rememberPhoto(key, thumbnail) { ctx ->
            when {
                kind == Backdrop.CUSTOM && customVersion <= 0L -> null
                kind == Backdrop.CUSTOM && thumbnail -> CustomBackdrop.decodeThumb(ctx)
                kind == Backdrop.CUSTOM -> CustomBackdrop.decodeFull(ctx)
                else -> decodeResource(ctx, if (thumbnail) spec.thumb else spec.full)
            }
        }
        Canvas(modifier) {
            val t = time
            if (img != null) {
                drawPhotoBackdrop(img, spec, anchor, t, thumbnail, stars)
            } else {
                // Loading (or no photo picked yet): a quiet starfield.
                drawRect(Void)
                drawStars(stars.take(90), primary, secondary, t * 0.3f, size.height)
                if (kind == Backdrop.CUSTOM && customVersion <= 0L) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val arm = size.minDimension * 0.12f
                    drawLine(primary, c - Offset(arm, 0f), c + Offset(arm, 0f), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(primary, c - Offset(0f, arm), c + Offset(0f, arm), 3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        return
    }

    Canvas(modifier) {
        val t = time
        drawRect(Void)
        when (kind) {
            Backdrop.MOONWAVE -> drawMoonwave(stars, city, primary, secondary, t)
            Backdrop.NEBULA -> drawNebulaScene(stars, primary, secondary, t)
            Backdrop.WARP -> drawWarp(stars, primary, secondary, t)
            Backdrop.AURORA -> drawAurora(stars, primary, secondary, t)
            Backdrop.BLACK_HOLE -> drawBlackHole(stars, primary, secondary, t)
            Backdrop.PULSAR -> drawPulsar(stars, primary, secondary, t)
            Backdrop.COCKPIT -> drawFighterCockpit(stars, primary, secondary, t)
            else -> Unit
        }
    }
}

// ---------- Moonwave: the synthwave world at midnight ----------

private fun DrawScope.drawMoonwave(stars: List<Star>, city: List<Tower>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.70f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF02030A), Color(0xFF0A0D24), Color(0xFF1A1238)), startY = 0f, endY = horizon), Offset.Zero, Size(w, horizon))
    drawStars(stars.take(160), primary, secondary, t * 0.15f, horizon * 0.95f)

    // A big pale moon with a cold halo.
    val moonC = Offset(w * 0.68f, h * 0.19f)
    val moonR = w * 0.17f
    val pale = Color(0xFFDDE6FF)
    drawCircle(Brush.radialGradient(listOf(pale.copy(alpha = 0.22f), primary.copy(alpha = 0.06f), Color.Transparent), center = moonC, radius = moonR * 3.4f), moonR * 3.4f, moonC)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFF6F8FF), Color(0xFFCBD2EA), Color(0xFF8A92B6)), center = moonC - Offset(moonR * 0.35f, moonR * 0.35f), radius = moonR * 1.5f),
        moonR,
        moonC,
    )
    val craters = listOf(Triple(-0.3f, -0.1f, 0.18f), Triple(0.25f, 0.3f, 0.12f), Triple(0.1f, -0.38f, 0.09f), Triple(-0.05f, 0.45f, 0.07f), Triple(0.42f, -0.05f, 0.1f))
    craters.forEach { (dx, dy, r) ->
        drawCircle(Color(0xFF6E7799).copy(alpha = 0.28f), moonR * r, moonC + Offset(dx * moonR, dy * moonR))
    }
    // Night clouds drifting across it.
    for (i in 0 until 4) {
        val x = (((t * 0.008f * (1f + i * 0.35f)) + i * 0.31f) % 1.6f - 0.3f) * w
        val y = h * (0.13f + i * 0.045f)
        val cw = w * (0.45f + i * 0.08f)
        val ch = h * 0.022f
        drawOval(Color(0xFF0C0E2A).copy(alpha = 0.78f), Offset(x - cw / 2, y - ch / 2), Size(cw, ch))
        drawOval(pale.copy(alpha = 0.07f), Offset(x - cw / 2, y - ch / 2), Size(cw, ch * 0.45f))
    }
    // Sleeping city under the horizon.
    for (tower in city) {
        val left = tower.x * w
        val tw = tower.w * w
        val top = horizon - tower.h * h * (if (tower.layer == 0) 0.75f else 0.5f)
        drawRect(if (tower.layer == 0) Color(0xFF0B0B22) else Color(0xFF05060F), Offset(left, top), Size(tw, horizon - top))
        if (tower.layer == 1) {
            val r = Random(tower.seed)
            val cell = 6.dp.toPx()
            var y = top + cell
            while (y < horizon - cell) {
                var x = left + cell * 0.6f
                while (x < left + tw - cell * 0.6f) {
                    if (r.nextFloat() < 0.12f) {
                        val glow = 0.25f + 0.2f * sin(t * (0.3f + r.nextFloat()) + r.nextFloat() * 9f)
                        drawRect((if (r.nextBoolean()) Color(0xFFFFC879) else primary).copy(alpha = glow), Offset(x, y), Size(cell * 0.4f, cell * 0.5f))
                    }
                    x += cell
                }
                y += cell
            }
            if (tower.h > 0.26f) {
                val blink = (sin(t * 2f + tower.seed) + 1) / 2
                drawCircle(Color(0xFFFF4060).copy(alpha = 0.3f + 0.6f * blink), 2.dp.toPx(), Offset(left + tw / 2, top - 3.dp.toPx()))
            }
        }
    }
    // Dark glassy floor with a slow grid.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0A22), Void), startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))
    val gridColor = Color(0xFF5560C8)
    val stroke = 1.dp.toPx()
    for (i in -14..14) {
        drawLine(gridColor.copy(alpha = 0.14f), Offset(w / 2f + i * w * 0.02f, horizon), Offset(w / 2f + i * w * 0.16f, h), stroke)
    }
    val phase = (t * 0.25f) % 1f
    for (k in 0 until 12) {
        val p = (k + phase) / 12
        val yy = horizon + (h - horizon) * p * p
        drawLine(gridColor.copy(alpha = 0.03f + 0.22f * p), Offset(0f, yy), Offset(w, yy), stroke * (1f + p))
    }
    // The moon's reflection shimmering on the floor.
    for (k in 0 until 20) {
        val f = k / 20f
        val yy = horizon + (h - horizon) * f.let { it * it } + 2f
        val rw = w * 0.025f * (1f + k * 0.18f) * (0.6f + 0.4f * sin(t * 2.2f + k * 1.3f))
        drawRect(pale.copy(alpha = 0.28f * (1f - f)), Offset(moonC.x - rw / 2, yy), Size(rw, (1.5f + f * 3f).dp.toPx()))
    }
    drawLine(secondary.copy(alpha = 0.18f), Offset(0f, horizon), Offset(w, horizon), 4.dp.toPx())
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

/**
 * One continuous accretion disk: every band is a single ellipse split into a far half
 * (drawn before the hole) and a near half (drawn after), so the two halves always meet.
 */
private fun DrawScope.drawBlackHole(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val c = Offset(w / 2f, size.height * 0.40f)
    val r = w * 0.15f
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
        brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.35f), primary.copy(alpha = 0.08f), Color.Transparent), center = c, radius = r * 4.5f),
        radius = r * 4.5f,
        center = c,
    )
    val tilt = -10f
    // Bands from the inner edge outwards: (x radius, y radius, width, alpha), all in units of r.
    val bands = listOf(
        floatArrayOf(1.7f, 0.42f, 0.30f, 0.95f),
        floatArrayOf(2.15f, 0.53f, 0.24f, 0.75f),
        floatArrayOf(2.6f, 0.64f, 0.16f, 0.5f),
        floatArrayOf(3.0f, 0.74f, 0.08f, 0.3f),
    )
    val diskBrush = Brush.horizontalGradient(listOf(primary, hot, Color.White, hot, secondary), startX = c.x - r * 3f, endX = c.x + r * 3f)
    fun DrawScope.half(back: Boolean) = rotate(tilt, c) {
        bands.forEach { b ->
            val rx = r * b[0]
            val ry = r * b[1]
            drawArc(
                brush = diskBrush,
                startAngle = if (back) 180f else 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2),
                style = Stroke(r * b[2]), alpha = b[3] * (if (back) 0.8f else 1f),
            )
        }
    }
    half(back = true)
    // The far side of the disk, bent up and over the hole by gravity (the "lensed" halo).
    drawArc(
        brush = Brush.horizontalGradient(listOf(secondary, hot, primary), startX = c.x - r * 1.5f, endX = c.x + r * 1.5f),
        startAngle = 190f, sweepAngle = 160f, useCenter = false,
        topLeft = Offset(c.x - r * 1.45f, c.y - r * 1.45f), size = Size(r * 2.9f, r * 2.9f),
        style = Stroke(r * 0.14f, cap = StrokeCap.Round), alpha = 0.55f,
    )
    // Photon ring, then the event horizon.
    drawCircle(secondary.copy(alpha = 0.35f), r * 1.22f, c, style = Stroke(r * 0.16f))
    drawCircle(hot.copy(alpha = 0.9f), r * 1.06f, c, style = Stroke(2.dp.toPx()))
    drawCircle(Color.Black, r, c)
    half(back = false)
    // Bright clumps riding the disk, only while they're on the near side.
    rotate(tilt, c) {
        val spin = (t * 40f) % 360f
        for (i in 0 until 7) {
            val a = (spin + i * (360f / 7)) * PI.toFloat() / 180f
            val k = 1.75f + (i % 3) * 0.45f
            val p = c + Offset(cos(a) * r * k, sin(a) * r * k * 0.247f)
            if (sin(a) > 0) drawCircle(Color.White.copy(alpha = 0.85f), 2.dp.toPx(), p)
        }
    }
}

// ---------- Pulsar: a spinning neutron star sweeping two beams ----------

private fun DrawScope.drawPulsar(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    drawNebula(primary, secondary, t * 0.5f)
    drawStars(stars, primary, secondary, t * 0.25f, h)
    val c = Offset(w / 2f, h * 0.40f)
    val len = size.maxDimension
    val angle = t * 0.9f
    // Expanding shock rings, one per half turn.
    for (k in 0 until 4) {
        val p = ((angle / PI.toFloat() + k / 4f) % 1f)
        drawCircle(secondary.copy(alpha = 0.25f * (1f - p)), radius = w * 0.05f + p * w * 0.7f, center = c, style = Stroke((2.5f * (1f - p) + 0.5f).dp.toPx()))
    }
    // Magnetic field loops.
    rotate(angle * 57.3f * 0.15f, c) {
        for (i in 1..3) {
            val rx = w * 0.06f * i
            val ry = w * 0.16f * i
            drawOval(primary.copy(alpha = 0.22f / i), Offset(c.x - rx * 2, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.dp.toPx()))
            drawOval(primary.copy(alpha = 0.22f / i), Offset(c.x, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.dp.toPx()))
        }
    }
    // Two opposite lighthouse beams.
    for (side in 0..1) {
        val a = angle + side * PI.toFloat()
        val spread = 0.11f
        val beam = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + cos(a - spread) * len, c.y + sin(a - spread) * len)
            lineTo(c.x + cos(a + spread) * len, c.y + sin(a + spread) * len)
            close()
        }
        val col = if (side == 0) primary else secondary
        drawPath(beam, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), col.copy(alpha = 0.3f), Color.Transparent), center = c, radius = len * 0.75f))
        val core = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + cos(a - spread * 0.3f) * len, c.y + sin(a - spread * 0.3f) * len)
            lineTo(c.x + cos(a + spread * 0.3f) * len, c.y + sin(a + spread * 0.3f) * len)
            close()
        }
        drawPath(core, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), center = c, radius = len * 0.5f))
    }
    // The star itself, flashing each time a beam faces you.
    val flash = 0.5f + 0.5f * cos(angle * 2f).let { it * it }
    drawCircle(Brush.radialGradient(listOf(Color.White, primary.copy(alpha = 0.6f * flash), Color.Transparent), center = c, radius = w * 0.12f), w * 0.12f, c)
    drawCircle(Color.White, w * 0.018f, c)
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

// ---------- Cockpits: looking out through a cluttered canopy ----------

private val Hull = Color(0xFF0B0A15)
private val HullLit = Color(0xFF17142B)
private val Amber = Color(0xFFFFB020)
private val Mint = Color(0xFF39FF9A)
private val Warn = Color(0xFFFF4060)

/** A cable hanging between two fixed points, sagging (and breathing a little with [sag]). */
private fun DrawScope.hangingCable(a: Offset, b: Offset, sag: Float, color: Color, width: Float) {
    val low = max(a.y, b.y) + sag
    val p = Path().apply {
        moveTo(a.x, a.y)
        cubicTo(a.x + (b.x - a.x) * 0.25f, low, a.x + (b.x - a.x) * 0.75f, low, b.x, b.y)
    }
    drawPath(p, Color.Black.copy(alpha = 0.5f), style = Stroke(width * 1.6f, cap = StrokeCap.Round))
    drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round))
}

/** A loose cable hanging from [top], swinging. Returns where its plug ends. */
private fun DrawScope.danglingCable(top: Offset, len: Float, phase: Float, color: Color, tip: Color): Offset {
    val ang = sin(phase) * 0.22f
    val end = Offset(top.x + sin(ang) * len, top.y + cos(ang) * len)
    val p = Path().apply {
        moveTo(top.x, top.y)
        cubicTo(top.x, top.y + len * 0.4f, end.x - sin(ang) * len * 0.25f, end.y - len * 0.3f, end.x, end.y)
    }
    drawPath(p, color, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round))
    val pw = 6.dp.toPx()
    drawRect(Color(0xFF2B2640), Offset(end.x - pw / 2, end.y), Size(pw, pw * 1.5f))
    drawCircle(tip, 1.8f.dp.toPx(), Offset(end.x, end.y + pw * 1.7f))
    return Offset(end.x, end.y + pw * 1.7f)
}

/** A panel crammed with blinking LEDs, keys, toggle switches and little meters. */
private fun DrawScope.buttonPanel(left: Float, top: Float, w: Float, h: Float, seed: Int, t: Float, primary: Color, secondary: Color) {
    drawRect(HullLit, Offset(left, top), Size(w, h))
    drawRect(primary.copy(alpha = 0.25f), Offset(left, top), Size(w, h), style = Stroke(1.dp.toPx()))
    val r = Random(seed)
    val cell = 9.dp.toPx()
    var y = top + cell * 0.7f
    while (y < top + h - cell * 0.5f) {
        var x = left + cell * 0.6f
        while (x < left + w - cell * 0.5f) {
            val kind = r.nextInt(5)
            val rate = 0.4f + r.nextFloat() * 3f
            val ph = r.nextFloat() * 10f
            val col = when (r.nextInt(4)) {
                0 -> primary
                1 -> secondary
                2 -> Amber
                else -> Mint
            }
            when (kind) {
                0 -> {
                    val on = sin(t * rate + ph) > 0f
                    drawCircle(col.copy(alpha = if (on) 0.95f else 0.15f), cell * 0.17f, Offset(x, y))
                }
                1 -> drawRect(Color(0xFF2A2545), Offset(x - cell * 0.32f, y - cell * 0.22f), Size(cell * 0.64f, cell * 0.44f))
                2 -> {
                    // Toggle switch.
                    drawRect(Color(0xFF05040A), Offset(x - cell * 0.15f, y - cell * 0.3f), Size(cell * 0.3f, cell * 0.6f))
                    val up = r.nextBoolean()
                    drawLine(Color(0xFFB8BCD8), Offset(x, y), Offset(x + cell * 0.12f, y + (if (up) -1 else 1) * cell * 0.32f), 1.5f.dp.toPx(), StrokeCap.Round)
                }
                3 -> {
                    val lvl = (0.5f + 0.5f * sin(t * rate + ph)).coerceIn(0f, 1f)
                    drawRect(Color(0xFF05040A), Offset(x - cell * 0.1f, y - cell * 0.35f), Size(cell * 0.2f, cell * 0.7f))
                    drawRect(col.copy(alpha = 0.8f), Offset(x - cell * 0.1f, y + cell * 0.35f - cell * 0.7f * lvl), Size(cell * 0.2f, cell * 0.7f * lvl))
                }
                else -> Unit
            }
            x += cell
        }
        y += cell
    }
}

/** A little CRT: 0 radar, 1 waveform, 2 bar meters, 3 scrolling readout. */
private fun DrawScope.monitor(left: Float, top: Float, w: Float, h: Float, kind: Int, t: Float, primary: Color, secondary: Color) {
    drawRect(Color(0xFF03050A), Offset(left, top), Size(w, h))
    clipRect(left, top, left + w, top + h) {
        val c = Offset(left + w / 2, top + h / 2)
        when (kind) {
            0 -> {
                val rr = minOf(w, h) * 0.45f
                for (i in 1..3) drawCircle(Mint.copy(alpha = 0.25f), rr * i / 3f, c, style = Stroke(1f))
                val a = t * 1.6f
                drawLine(Mint.copy(alpha = 0.8f), c, c + Offset(cos(a), sin(a)) * rr, 1.5f.dp.toPx())
                for (k in 0 until 4) {
                    val ba = k * 1.9f + 0.4f
                    val fade = (((a - ba) % (2 * PI.toFloat())) + 2 * PI.toFloat()) % (2 * PI.toFloat())
                    drawCircle(Mint.copy(alpha = (1f - fade / 6.3f).coerceIn(0f, 1f)), 2.dp.toPx(), c + Offset(cos(ba), sin(ba)) * rr * (0.35f + 0.15f * k))
                }
            }
            1 -> {
                val p = Path()
                val steps = 40
                for (i in 0..steps) {
                    val x = left + w * i / steps
                    val y = c.y + sin(i * 0.6f + t * 4f) * h * 0.22f * (0.6f + 0.4f * sin(t + i * 0.2f))
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, primary.copy(alpha = 0.9f), style = Stroke(1.5f.dp.toPx()))
            }
            2 -> {
                val n = 8
                val bw = w / (n * 1.6f)
                for (i in 0 until n) {
                    val lvl = 0.2f + 0.8f * (0.5f + 0.5f * sin(t * (1.3f + i * 0.4f) + i))
                    drawRect(if (i > 5) Warn.copy(alpha = 0.8f) else secondary.copy(alpha = 0.85f), Offset(left + bw * 0.4f + i * bw * 1.6f, top + h * (0.9f - 0.8f * lvl)), Size(bw, h * 0.8f * lvl))
                }
            }
            else -> {
                val line = 5.dp.toPx()
                val off = (t * 12f) % line
                var y = top + line - off
                val r = Random(9)
                while (y < top + h) {
                    drawLine(Amber.copy(alpha = 0.7f), Offset(left + w * 0.08f, y), Offset(left + w * (0.15f + 0.7f * r.nextFloat()), y), 1.5f.dp.toPx())
                    y += line
                }
            }
        }
        // Scanlines.
        var sy = top
        while (sy < top + h) {
            drawLine(Color.Black.copy(alpha = 0.3f), Offset(left, sy), Offset(left + w, sy), 1f)
            sy += 3.dp.toPx()
        }
    }
    drawRect(primary.copy(alpha = 0.45f), Offset(left, top), Size(w, h), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.spark(at: Offset, t: Float, seed: Int) {
    val burst = sin(t * 2.3f + seed)
    if (burst < 0.9f) return
    val r = Random((t * 20).toInt() + seed)
    repeat(7) {
        val a = r.nextFloat() * 2 * PI.toFloat()
        val l = (4 + r.nextFloat() * 12).dp.toPx()
        drawLine(Color(0xFFFFF1A8), at, at + Offset(cos(a), sin(a)) * l, 1.2f.dp.toPx(), StrokeCap.Round)
    }
    drawCircle(Color.White.copy(alpha = 0.8f), 3.dp.toPx(), at)
}

// Fighter: canopy struts, a curved dash, a loose monitor swinging from the frame.
private fun DrawScope.drawFighterCockpit(stars: List<Star>, primary: Color, secondary: Color, t: Float) {
    val w = size.width
    val h = size.height
    drawNebula(primary, secondary, t * 0.3f)
    // Gentle forward drift through the stars.
    val c = Offset(w / 2f, h * 0.40f)
    stars.forEach { s ->
        val a = s.x * 2 * PI.toFloat()
        val p = (s.y + t * 0.03f * (0.3f + s.z)) % 1f
        val pos = c + Offset(cos(a), sin(a)) * (p * p * size.maxDimension * 0.75f)
        drawCircle(Color.White.copy(alpha = (0.15f + 0.8f * p).coerceIn(0f, 1f)), (0.5f + 1.6f * p * s.z).dp.toPx(), pos)
    }
    drawNeonPlanet(Offset(w * 0.74f, h * 0.2f), w * 0.1f, lerp(Color(0xFF1B0F3A), secondary, 0.3f), primary, -18f, 7L, t * 0.05f)
    // Faint HUD reticle.
    drawCircle(primary.copy(alpha = 0.10f), w * 0.40f, c, style = Stroke(1.dp.toPx()))
    for (k in 0 until 4) {
        val a = k * PI.toFloat() / 2
        drawLine(primary.copy(alpha = 0.25f), c + Offset(cos(a), sin(a)) * w * 0.37f, c + Offset(cos(a), sin(a)) * w * 0.43f, 1.5f.dp.toPx())
    }
    // Glass glare.
    drawLine(Color.White.copy(alpha = 0.04f), Offset(w * 0.25f, h * 0.08f), Offset(w * 0.55f, h * 0.6f), w * 0.05f)

    val hull = lerp(Hull, primary, 0.05f)
    val edge = primary.copy(alpha = 0.35f)
    // Canopy frame: top bar and side struts.
    val top = Path().apply {
        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.07f)
        cubicTo(w * 0.7f, h * 0.04f, w * 0.3f, h * 0.04f, 0f, h * 0.07f); close()
    }
    val left = Path().apply { moveTo(0f, h * 0.05f); lineTo(w * 0.09f, h * 0.05f); lineTo(w * 0.2f, h * 0.72f); lineTo(0f, h * 0.72f); close() }
    val right = Path().apply { moveTo(w, h * 0.05f); lineTo(w * 0.91f, h * 0.05f); lineTo(w * 0.8f, h * 0.72f); lineTo(w, h * 0.72f); close() }
    val dash = Path().apply {
        moveTo(0f, h * 0.70f); cubicTo(w * 0.3f, h * 0.63f, w * 0.7f, h * 0.63f, w, h * 0.70f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    listOf(top, left, right, dash).forEach {
        drawPath(it, hull)
        drawPath(it, edge, style = Stroke(1.5f.dp.toPx()))
    }
    // Rivets along the struts.
    for (i in 0..8) {
        val f = i / 8f
        drawCircle(TextLoDim, 1.5f.dp.toPx(), Offset(w * (0.085f + 0.11f * f), h * (0.08f + 0.6f * f)))
        drawCircle(TextLoDim, 1.5f.dp.toPx(), Offset(w * (0.915f - 0.11f * f), h * (0.08f + 0.6f * f)))
    }
    // Side wall panels.
    buttonPanel(w * 0.012f, h * 0.36f, w * 0.12f, h * 0.24f, 3, t, primary, secondary)
    buttonPanel(w * 0.868f, h * 0.36f, w * 0.12f, h * 0.24f, 4, t, primary, secondary)
    monitor(w * 0.015f, h * 0.24f, w * 0.1f, h * 0.06f, 3, t, primary, secondary)
    // Dashboard.
    monitor(w * 0.06f, h * 0.76f, w * 0.25f, h * 0.1f, 0, t, primary, secondary)
    monitor(w * 0.69f, h * 0.76f, w * 0.25f, h * 0.1f, 1, t, primary, secondary)
    monitor(w * 0.38f, h * 0.71f, w * 0.24f, h * 0.06f, 2, t, primary, secondary)
    buttonPanel(w * 0.33f, h * 0.88f, w * 0.34f, h * 0.09f, 9, t, primary, secondary)
    buttonPanel(w * 0.04f, h * 0.89f, w * 0.26f, h * 0.08f, 12, t, primary, secondary)
    buttonPanel(w * 0.70f, h * 0.89f, w * 0.26f, h * 0.08f, 13, t, primary, secondary)
    // Warning beacon on the frame.
    val blink = ((sin(t * 3f) + 1) / 2)
    drawCircle(Brush.radialGradient(listOf(Warn.copy(alpha = 0.6f * blink), Color.Transparent), center = Offset(w * 0.5f, h * 0.03f), radius = 18.dp.toPx()), 18.dp.toPx(), Offset(w * 0.5f, h * 0.03f))
    drawCircle(Warn.copy(alpha = 0.4f + 0.6f * blink), 3.dp.toPx(), Offset(w * 0.5f, h * 0.03f))
    // Cables sagging off the frame, a couple hanging loose.
    hangingCable(Offset(w * 0.04f, h * 0.06f), Offset(w * 0.32f, h * 0.05f), h * 0.05f + sin(t * 0.8f) * h * 0.005f, Color(0xFF2E2848), 3.dp.toPx())
    hangingCable(Offset(w * 0.1f, h * 0.06f), Offset(w * 0.26f, h * 0.05f), h * 0.08f + sin(t * 0.7f + 1f) * h * 0.006f, Color(0xFF3A1E2E), 2.dp.toPx())
    hangingCable(Offset(w * 0.6f, h * 0.05f), Offset(w * 0.95f, h * 0.06f), h * 0.065f + sin(t * 0.9f) * h * 0.006f, Color(0xFF1E3340), 4.dp.toPx())
    val plug = danglingCable(Offset(w * 0.86f, h * 0.06f), h * 0.13f, t * 0.9f + 2f, Color(0xFF2E2848), Amber)
    spark(plug, t, 5)
    danglingCable(Offset(w * 0.67f, h * 0.05f), h * 0.07f, t * 1.1f, Color(0xFF3A1E2E), Mint)
    // A loose monitor swinging on two wires from the top left.
    val pivot = Offset(w * 0.2f, h * 0.05f)
    rotate(sin(t * 0.7f) * 8f, pivot) {
        val sw = w * 0.15f
        val sh = h * 0.06f
        val sx = pivot.x - sw / 2
        val sy = pivot.y + h * 0.07f
        drawLine(Color(0xFF2E2848), pivot, Offset(sx + sw * 0.15f, sy), 2.dp.toPx())
        drawLine(Color(0xFF2E2848), pivot, Offset(sx + sw * 0.85f, sy), 2.dp.toPx())
        monitor(sx, sy, sw, sh, 2, t, primary, secondary)
    }
}

private val TextLoDim = Color(0xFF3A3656)
