package com.stellapp.disc.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin

/**
 * A neon planet with a proper ring: the far half of the ring passes behind the
 * planet and the near half in front. Includes banded clouds, a lit side and a
 * shadow side, a glowing rim and an atmosphere halo.
 *
 * @param phase slides the cloud bands sideways (animate it for a spinning planet)
 */
fun DrawScope.drawNeonPlanet(
    center: Offset,
    r: Float,
    body: Color,
    accent: Color,
    tilt: Float,
    seed: Long,
    phase: Float = 0f,
    ring: Boolean = true,
) {
    // Atmosphere halo.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(body.copy(alpha = 0.42f), accent.copy(alpha = 0.12f), Color.Transparent),
            center = center,
            radius = r * 1.75f,
        ),
        radius = r * 1.75f,
        center = center,
    )

    if (ring) rotate(tilt, pivot = center) { drawRingHalf(center, r, accent, body, back = true) }

    // Planet body, clipped so the bands stay on the sphere.
    val sphere = Path().apply { addOval(Rect(center, r)) }
    clipPath(sphere) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(lerp(body, Color.White, 0.45f), body, lerp(body, Color.Black, 0.55f)),
                center = Offset(center.x - r * 0.4f, center.y - r * 0.45f),
                radius = r * 1.7f,
            ),
            radius = r,
            center = center,
        )
        // Cloud bands, wobbling a little like gas giant storms.
        rotate(tilt, pivot = center) {
            val bands = 7
            val bandH = 2f * r / bands
            for (i in 0 until bands) {
                val wobble = sin((seed % 97) * 0.13f + i * 1.7f + phase) * bandH * 0.18f
                val y = center.y - r + i * bandH + wobble
                val c = if (i % 2 == 0) accent else lerp(body, Color.White, 0.25f)
                drawRect(
                    color = c.copy(alpha = 0.10f + 0.10f * ((seed + i * 31) % 3)),
                    topLeft = Offset(center.x - r * 1.2f, y),
                    size = Size(r * 2.4f, bandH * (0.45f + 0.15f * (i % 3))),
                )
            }
            // A storm spot.
            val spotX = center.x + ((seed % 50) / 50f - 0.5f) * r * 0.9f
            drawOval(
                color = lerp(accent, Color.White, 0.3f).copy(alpha = 0.35f),
                topLeft = Offset(spotX - r * 0.16f, center.y + r * 0.18f),
                size = Size(r * 0.32f, r * 0.14f),
            )
        }
        // Shadow side.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                center = Offset(center.x - r * 0.35f, center.y - r * 0.35f),
                radius = r * 1.55f,
            ),
            radius = r,
            center = center,
        )
    }
    // Neon rim light.
    drawCircle(accent.copy(alpha = 0.25f), radius = r * 1.04f, center = center, style = Stroke(r * 0.08f))
    drawCircle(lerp(accent, Color.White, 0.3f).copy(alpha = 0.75f), radius = r, center = center, style = Stroke(r * 0.025f))

    if (ring) rotate(tilt, pivot = center) { drawRingHalf(center, r, accent, body, back = false) }
}

/** One half of a three-band ring. back = the far side (top half), drawn before the planet. */
private fun DrawScope.drawRingHalf(center: Offset, r: Float, accent: Color, body: Color, back: Boolean) {
    val start = if (back) 180f else 0f
    val bands = listOf(1.45f to 0.9f, 1.62f to 0.55f, 1.80f to 0.35f)
    bands.forEachIndexed { i, (scale, alpha) ->
        val w = r * scale
        val h = w * 0.30f
        val topLeft = Offset(center.x - w, center.y - h)
        val size = Size(w * 2, h * 2)
        val c = if (i == 1) lerp(accent, body, 0.4f) else accent
        // Soft glow under the line.
        drawArc(c.copy(alpha = alpha * 0.25f), start, 180f, false, topLeft, size, style = Stroke(r * 0.11f, cap = StrokeCap.Round))
        drawArc(c.copy(alpha = alpha * (if (back) 0.6f else 1f)), start, 180f, false, topLeft, size, style = Stroke(r * 0.035f, cap = StrokeCap.Round))
    }
}
