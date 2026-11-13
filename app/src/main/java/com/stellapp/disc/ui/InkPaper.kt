package com.stellapp.disc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.stellapp.disc.ui.theme.InkBlack
import com.stellapp.disc.ui.theme.Paper
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The Ink & Screentone backdrop: newsprint paper, faint speed lines framing the middle, and
 * patches of halftone dots in two corners, like a page of manga. Drawn once (no animation),
 * so it costs nothing while you listen.
 */
@Composable
fun InkPaper(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawInkPaper() }
}

internal fun DrawScope.drawInkPaper() {
    drawRect(Paper)
    val w = size.width
    val h = size.height
    val c = Offset(w / 2f, h * 0.42f)
    val far = hypot(w, h)

    // Speed lines: start well away from the middle so content stays clean.
    val lines = 72
    for (i in 0 until lines) {
        val a = (i.toFloat() / lines) * (2f * Math.PI.toFloat()) + (i % 3) * 0.013f
        val inner = far * (0.36f + (i % 5) * 0.025f)
        val start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner)
        val end = Offset(c.x + cos(a) * far, c.y + sin(a) * far)
        drawLine(
            InkBlack.copy(alpha = if (i % 4 == 0) 0.10f else 0.05f),
            start,
            end,
            strokeWidth = if (i % 4 == 0) 2.dp.toPx() else 1.dp.toPx(),
        )
    }

    // Halftone dots fading out from the top-right and bottom-left corners.
    val step = 7.dp.toPx()
    val maxR = step * 0.32f
    val reach = minOf(w, h) * 0.55f
    val corners = listOf(Offset(w, 0f), Offset(0f, h))
    corners.forEach { corner ->
        var y = 0f
        while (y <= h) {
            var x = if (((y / step).toInt() % 2) == 0) 0f else step / 2f
            while (x <= w) {
                val d = hypot(x - corner.x, y - corner.y)
                if (d < reach) {
                    val r = maxR * (1f - d / reach)
                    if (r > 0.4f) drawCircle(InkBlack.copy(alpha = 0.55f), r, Offset(x, y))
                }
                x += step
            }
            y += step
        }
    }
}
