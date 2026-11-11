package com.stellapp.disc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val Void = Color(0xFF05040D)
val PanelSolid = Color(0xFF0E0B1F)
val Panel = Color(0xCC0E0B1F)
val TextHi = Color(0xFFE8EAFF)
val TextLo = Color(0xFF8C8FB5)

val Mono = FontFamily.Monospace

data class Accent(val name: String, val primary: Color, val secondary: Color)

val Accents = listOf(
    Accent("Neon Cyan", Color(0xFF00F0FF), Color(0xFFFF2BD6)),
    Accent("Synth Magenta", Color(0xFFFF2BD6), Color(0xFF7B5CFF)),
    Accent("Solar Flare", Color(0xFFFFB020), Color(0xFFFF4D6D)),
    Accent("Toxic Nebula", Color(0xFF39FF14), Color(0xFF00C2FF)),
    Accent("Ion Violet", Color(0xFFB388FF), Color(0xFF00F0FF)),
)

@Composable
fun DiscTheme(accentIndex: Int, content: @Composable () -> Unit) {
    val a = Accents[accentIndex.coerceIn(0, Accents.lastIndex)]
    val scheme = darkColorScheme(
        primary = a.primary,
        onPrimary = Void,
        secondary = a.secondary,
        onSecondary = Void,
        background = Void,
        onBackground = TextHi,
        surface = PanelSolid,
        onSurface = TextHi,
        surfaceVariant = Color(0xFF1A1633),
        onSurfaceVariant = TextLo,
        outline = a.primary.copy(alpha = 0.5f),
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
