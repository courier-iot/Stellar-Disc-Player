package com.stellapp.disc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo

private data class Tip(val gesture: String, val does: String)

private val TIP_GROUPS: List<Pair<String, List<Tip>>> = listOf(
    "NOW PLAYING" to listOf(
        Tip("Swipe left / right", "Next or previous song"),
        Tip("Double-tap the disc", "Play or pause"),
        Tip("Long-press the disc", "Open the queue"),
        Tip("Swipe up", "Show the lyrics"),
        Tip("Swipe down", "Close the player"),
        Tip("Drag the volume strip", "Turn the volume up or down"),
    ),
    "MINI PLAYER" to listOf(
        Tip("Swipe left / right", "Next or previous song"),
        Tip("Swipe up or tap", "Open Now Playing"),
    ),
    "LIBRARY" to listOf(
        Tip("Long-press a song", "Song menu: queue, playlist, share, delete"),
        Tip("Drag the letter rail", "Jump to a letter (A–Z and Artist sorts)"),
        Tip("UNDO on the banner", "Bring back a removed song or playlist"),
        Tip("♥ in Now Playing", "Add the song to Favourites"),
    ),
    "VIDEO" to listOf(
        Tip("Double-tap left / right", "Skip back or ahead 10 seconds"),
        Tip("Double-tap the middle", "Play or pause"),
        Tip("Drag up / down on the left", "Screen brightness"),
        Tip("Drag up / down on the right", "Volume"),
    ),
    "NOTIFICATION & WIDGET" to listOf(
        Tip("♥ button", "Favourite the song that's playing"),
        Tip("Shuffle button", "Turn shuffle on or off"),
        Tip("Resize the widget", "Small, wide and large layouts"),
    ),
    "SHARING" to listOf(
        Tip("Share a link to the app", "Opens Uplink, ready to download"),
        Tip("⇄ SHARE on a playlist", "Show its QR code, or share it as links"),
        Tip("Swap → Send one of yours", "Pick any playlist to share from there"),
        Tip("Swap QR code", "Same Wi-Fi only, and encrypted"),
    ),
)

/** Every gesture in one place, linked from Settings. */
@Composable
fun TipsScreen(animate: Boolean, onClose: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield(Modifier.fillMaxSize(), animate = animate, speed = 0.3f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Text("GESTURES & TIPS", style = neonTitleStyle(20))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                TIP_GROUPS.forEach { (title, tips) ->
                    Column(
                        Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .neonPanel(primary.copy(alpha = 0.5f), glow = 0.dp)
                            .padding(16.dp)
                    ) {
                        Text(title, color = primary, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
                        Spacer(Modifier.height(6.dp))
                        tips.forEach { t ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Text(t.gesture, color = TextHi, fontSize = 14.sp, modifier = Modifier.weight(0.48f))
                                Spacer(Modifier.width(10.dp))
                                Text(t.does, color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(0.52f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The friendly "nothing here yet" panel used across the app. [action] adds a single button.
 */
@Composable
fun EmptyState(
    glyph: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(glyph, color = primary.copy(alpha = 0.85f), fontSize = 52.sp, style = neonTitleStyle(52))
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextHi, fontFamily = Mono, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextLo, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                actionLabel,
                color = primary,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = 13.sp,
                modifier = Modifier
                    .neonPanel(primary, glow = 8.dp)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
