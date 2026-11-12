package com.stellapp.disc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** App-wide look switches that plain colour values depend on. Compose state, so changes redraw. */
object ThemeState {
    var pureBlack by mutableStateOf(false)
}

private val DeepSpace = Color(0xFF05040D)

/** The app background: deep space, or true black (#000) with the OLED option on. */
val Void: Color get() = if (ThemeState.pureBlack) Color.Black else DeepSpace
val PanelSolid = Color(0xFF0E0B1F)
val Panel = Color(0xCC0E0B1F)
val TextHi = Color(0xFFE8EAFF)
val TextLo = Color(0xFF8C8FB5)

val Mono = FontFamily.Monospace

/**
 * An accent gradient. [primary] → [secondary] is the true gradient (play button, bars, disc rim).
 * [ink] is the version of [secondary] used for text and icons, for gradients that end in a colour
 * too dark to read on the dark UI (like pure black).
 */
data class Accent(val name: String, val primary: Color, val secondary: Color, val ink: Color = secondary)

val Accents = listOf(
    Accent("Neon Cyan", Color(0xFF00F0FF), Color(0xFFFF2BD6)),
    Accent("Synth Magenta", Color(0xFFFF2BD6), Color(0xFF7B5CFF)),
    Accent("Solar Flare", Color(0xFFFFB020), Color(0xFFFF4D6D)),
    Accent("Toxic Nebula", Color(0xFF39FF14), Color(0xFF00C2FF)),
    Accent("Ion Violet", Color(0xFFB388FF), Color(0xFF00F0FF)),
    // v2.5.0
    Accent("Obsidian Silver", Color(0xFFDDE3F5), Color(0xFF000000), ink = Color(0xFF9AA2C4)),
    Accent("Crimson Ember", Color(0xFFE8173F), Color(0xFFFF9E3D)),
    Accent("Glacier Comet", Color(0xFF8BE9FF), Color(0xFF4A6BFF)),
    Accent("Rose Gold", Color(0xFFFF8FB8), Color(0xFFFFC98B)),
)

@Composable
fun DiscTheme(accentIndex: Int, content: @Composable () -> Unit) {
    val a = Accents[accentIndex.coerceIn(0, Accents.lastIndex)]
    val scheme = darkColorScheme(
        primary = a.primary,
        onPrimary = Void,
        secondary = a.ink,
        onSecondary = Void,
        // The raw gradient end colour (may be pure black). Use accentGradient() for gradients.
        tertiary = a.secondary,
        onTertiary = TextHi,
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

/** The accent's true two-colour gradient, start → end. */
@Composable
@ReadOnlyComposable
fun accentGradient(): List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
