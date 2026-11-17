package com.stellapp.disc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.stellapp.disc.R

/** The two looks: the neon cockpit, or the manga page. */
enum class UiStyle(val label: String) { CYBER("◉ CYBER"), INK("✎ INK & SCREENTONE") }

/** App-wide look switches that plain colour values depend on. Compose state, so changes redraw. */
object ThemeState {
    var pureBlack by mutableStateOf(false)
    var style by mutableStateOf(UiStyle.CYBER)
    var inkAccent by mutableStateOf(0)
    /** Ink & Screentone on charcoal paper with light ink. */
    var inkDark by mutableStateOf(false)
    val ink: Boolean get() = style == UiStyle.INK
}

private val DeepSpace = Color(0xFF05040D)

/** Ink & Screentone: newsprint paper and black ink, or charcoal paper and pale grey ink. */
val Paper: Color get() = if (ThemeState.inkDark) Color(0xFF2A2A2D) else Color(0xFFF3EFE6)
val InkBlack: Color get() = if (ThemeState.inkDark) Color(0xFFE2DED4) else Color(0xFF111111)
/** Speech-balloon white: the fill of panels and chips on the page. */
val InkSheet: Color get() = if (ThemeState.inkDark) Color(0xFF3A3A3E) else Color.White
/** The hard drop shadow behind panels. */
val InkShadow: Color get() = if (ThemeState.inkDark) Color(0xFF111113) else Color(0xFF111111)

/** The app background: deep space, true black (#000) with the OLED option on, or paper. */
val Void: Color get() = when {
    ThemeState.ink -> Paper
    ThemeState.pureBlack -> Color.Black
    else -> DeepSpace
}
val PanelSolid: Color get() = if (ThemeState.ink) InkSheet else Color(0xFF0E0B1F)
val Panel: Color get() = if (ThemeState.ink) InkSheet else Color(0xCC0E0B1F)
val TextHi: Color get() = if (ThemeState.ink) InkBlack else Color(0xFFE8EAFF)
val TextLo: Color get() = when {
    ThemeState.ink && ThemeState.inkDark -> Color(0xFFA19D95)
    ThemeState.ink -> Color(0xFF4A4A4A)
    else -> Color(0xFF8C8FB5)
}

/** Comic lettering for Ink (Bangers, SIL Open Font License). */
val Bangers = FontFamily(Font(R.font.bangers))

/** The label font: monospace in the cockpit, comic lettering on the page. */
val Mono: FontFamily get() = if (ThemeState.ink) Bangers else FontFamily.Monospace

/** Ink colours. [text] is the darker shade used for words and icons on paper. */
data class InkAccent(val name: String, val primary: Color, val text: Color)

val InkAccents = listOf(
    InkAccent("Red ink", Color(0xFFD7263D), Color(0xFFA3172B)),
    InkAccent("Blue ink", Color(0xFF1F5FD6), Color(0xFF17459C)),
    InkAccent("Pure ink", Color(0xFF111111), Color(0xFF111111)),
)

/** The same inks, lightened so they read on charcoal paper. */
val InkAccentsDark = listOf(
    InkAccent("Red ink", Color(0xFFFF5A6E), Color(0xFFFF8A97)),
    InkAccent("Blue ink", Color(0xFF6E9BFF), Color(0xFF9DBBFF)),
    InkAccent("Pure ink", Color(0xFFE2DED4), Color(0xFFE2DED4)),
)

/** The ink in use right now (follows the charcoal switch). */
val currentInk: InkAccent get() =
    (if (ThemeState.inkDark) InkAccentsDark else InkAccents)[ThemeState.inkAccent.coerceIn(0, InkAccents.lastIndex)]

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
    if (ThemeState.ink) {
        val ink = currentInk
        if (ThemeState.inkDark) {
            val charcoalScheme = darkColorScheme(
                primary = ink.primary,
                onPrimary = Paper,
                secondary = ink.text,
                onSecondary = Paper,
                tertiary = InkBlack,
                onTertiary = Paper,
                background = Paper,
                onBackground = InkBlack,
                surface = InkSheet,
                onSurface = InkBlack,
                surfaceVariant = Color(0xFF323236),
                onSurfaceVariant = TextLo,
                outline = InkBlack,
            )
            MaterialTheme(colorScheme = charcoalScheme, typography = Typography(), content = content)
            return
        }
        val paperScheme = lightColorScheme(
            primary = ink.primary,
            onPrimary = Color.White,
            secondary = ink.text,
            onSecondary = Color.White,
            tertiary = InkBlack,
            onTertiary = Color.White,
            background = Paper,
            onBackground = InkBlack,
            surface = Color.White,
            onSurface = InkBlack,
            surfaceVariant = Color(0xFFE4DFD3),
            onSurfaceVariant = Color(0xFF4A4A4A),
            outline = InkBlack,
        )
        MaterialTheme(colorScheme = paperScheme, typography = Typography(), content = content)
        return
    }
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
