package com.stellapp.disc.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellapp.disc.uplink.UplinkManager
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Shown when entering (SPOOL) or leaving (COOL) Uplink while the yt-dlp engines power up or down. */
enum class EngineGate { SPOOL, COOL }

private val Cold = Color(0xFF4FA8FF)
private val Hot = Color(0xFFFF7A2E)

@Composable
fun EngineGateScreen(gate: EngineGate, onDone: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val spooling = gate == EngineGate.SPOOL
    // 0 = stopped & cold, 1 = full speed & hot.
    val heat = remember { Animatable(if (spooling) 0f else 1f) }
    val lines = remember { mutableStateListOf<String>() }
    var title by remember { mutableStateOf(if (spooling) "SPOOLING UP" else "COOLING DOWN") }
    var angle by remember { mutableFloatStateOf(0f) }

    BackHandler {} // let the sequence finish; it only takes a moment

    LaunchedEffect(gate) {
        lines.clear()
        val start = SystemClock.elapsedRealtime()
        if (spooling) {
            title = "SPOOLING UP"
            coroutineScope {
                val boot = async { UplinkManager.spoolUp(context) }
                lines += "› IGNITION"
                heat.animateTo(0.35f, tween(500, easing = FastOutSlowInEasing))
                lines += "› LOADING YT-DLP CORE"
                heat.animateTo(0.65f, tween(500, easing = FastOutSlowInEasing))
                val ok = boot.await()
                lines += if (ok) "› FFMPEG ONLINE" else "› ENGINE FAULT: " + (UplinkManager.initError ?: "DOWNLOADS MAY NOT WORK")
                heat.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                val v = UplinkManager.version.value
                lines += if (ok) "› ENGINES HOT" + (v?.let { "  ·  yt-dlp $it" } ?: "") else "› CONTINUING ANYWAY"
            }
            delay((1_900 - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(250))
        } else {
            title = "COOLING DOWN"
            val left = UplinkManager.coolDown()
            lines += "› THROTTLE TO IDLE"
            heat.animateTo(0.6f, tween(500, easing = LinearEasing))
            if (left > 0) {
                lines += "› $left DOWNLOAD${if (left == 1) "" else "S"} STILL IN FLIGHT"
                heat.animateTo(0.45f, tween(450))
                lines += "› ENGINES IDLE IN THE BACKGROUND, OFF WHEN THEY LAND"
                delay(700)
            } else {
                lines += "› VENTING HEAT"
                heat.animateTo(0.25f, tween(500, easing = LinearEasing))
                lines += "› PARKING YT-DLP"
                heat.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
                lines += "› ENGINES OFFLINE  ·  PLAYER ONLY"
            }
            delay((1_800 - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(300))
        }
        onDone()
    }

    // Spin the turbine at a speed that follows the heat.
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) angle = (angle + heat.value * heat.value * 900f * (now - last) / 1_000_000_000f) % 360f
                last = now
            }
        }
    }

    val h = heat.value
    val glow = lerp(Cold, lerp(primary, Hot, 0.5f), h)
    Box(
        Modifier
            .fillMaxSize()
            .background(Void.copy(alpha = 0.97f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(220.dp)) {
                val r = size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.15f + 0.35f * h), Color.Transparent), center = center, radius = r), r)
                drawCircle(glow.copy(alpha = 0.7f), r * 0.86f, style = Stroke(3.dp.toPx()))
                drawCircle(glow.copy(alpha = 0.25f), r * 0.93f, style = Stroke(1.dp.toPx()))
                // Heat gauge around the rim.
                drawArc(
                    color = glow,
                    startAngle = 135f,
                    sweepAngle = 270f * h,
                    useCenter = false,
                    topLeft = Offset(center.x - r * 0.98f, center.y - r * 0.98f),
                    size = Size(r * 1.96f, r * 1.96f),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
                // Turbine blades.
                rotate(angle, center) {
                    for (i in 0 until 12) {
                        val a = i * (2 * PI.toFloat() / 12)
                        val a2 = a + 0.35f
                        val inner = Offset(center.x + cos(a) * r * 0.18f, center.y + sin(a) * r * 0.18f)
                        val outer = Offset(center.x + cos(a2) * r * 0.8f, center.y + sin(a2) * r * 0.8f)
                        drawLine(lerp(Color(0xFF2A2A44), glow, 0.35f + 0.5f * h), inner, outer, 7.dp.toPx(), StrokeCap.Round)
                    }
                }
                // Hub.
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f + 0.6f * h), glow, Void), center = center, radius = r * 0.22f), r * 0.22f)
                // Vapour when cooling.
                if (!spooling) {
                    for (i in 0 until 10) {
                        val p = ((angle / 360f) + i / 10f) % 1f
                        val a = i * 0.63f
                        drawCircle(Cold.copy(alpha = (1f - p) * 0.25f * (1f - h)), r * (0.04f + 0.06f * p), Offset(center.x + cos(a) * r * (0.9f + p * 0.4f), center.y + sin(a) * r * (0.9f + p * 0.4f)))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(title, color = glow, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 4.sp)
            Text("UPLINK ENGINES", color = TextLo, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(110.dp)) {
                lines.forEach {
                    Text(it, color = TextHi, fontFamily = Mono, fontSize = 12.sp, textAlign = TextAlign.Start, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
