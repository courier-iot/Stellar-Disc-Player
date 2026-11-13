package com.stellapp.disc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.AppInfo
import com.stellapp.disc.BuildConfig
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.uplink.UplinkManager
import kotlinx.coroutines.launch

private data class Credit(val name: String, val role: String, val license: String, val url: String)

private val CREDITS = listOf(
    Credit("yt-dlp", "The downloader behind Uplink", "The Unlicense", "https://github.com/yt-dlp/yt-dlp"),
    Credit("youtubedl-android", "Runs yt-dlp on Android", "GPL-3.0", "https://github.com/yausername/youtubedl-android"),
    Credit("FFmpeg", "Audio conversion and video merging", "LGPL / GPL", "https://ffmpeg.org"),
    Credit("Python", "Runtime for yt-dlp", "PSF License", "https://www.python.org"),
    Credit("AndroidX Media3", "Playback engine and media controls", "Apache-2.0", "https://github.com/androidx/media"),
    Credit("Jetpack Compose", "User interface", "Apache-2.0", "https://developer.android.com/compose"),
    Credit("Kotlin Coroutines", "Background work", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Credit("ZXing", "QR codes for Swap", "Apache-2.0", "https://github.com/journeyapps/zxing-android-embedded"),
    Credit("Bangers", "Comic lettering for the Ink & Screentone theme", "SIL Open Font License 1.1", "https://github.com/googlefonts/bangers"),
    Credit("Jetpack Glance", "Home screen widget", "Apache-2.0", "https://developer.android.com/develop/ui/compose/glance"),
    Credit("LRCLIB", "Online lyrics (only when you ask)", "Free API", "https://lrclib.net"),
)

private val BACKDROP_CREDITS = listOf(
    Credit("Carina nebula", "NASA, ESA, N. Smith (UC Berkeley) & the Hubble Heritage Team", "Public domain", "https://science.nasa.gov/mission/hubble/"),
    Credit("Spiral galaxy", "ESA/Hubble & NASA", "CC BY 4.0", "https://esahubble.org/images/"),
    Credit("Deep field", "NASA, ESA, S. Beckwith (STScI) & the HUDF Team", "Public domain", "https://science.nasa.gov/mission/hubble/"),
    Credit("Saturn & Saturn close-up", "NASA/JPL-Caltech/Space Science Institute (PIA17172)", "Public domain", "https://photojournal.jpl.nasa.gov/catalog/PIA17172"),
    Credit("Primordial black holes", "NASA's Goddard Space Flight Center", "Public domain", "https://svs.gsfc.nasa.gov/14524"),
    Credit("ISS window", "NASA, via Unsplash", "Unsplash License", "https://unsplash.com/photos/space-shuttle-view-outside-the-earth-CpHNKNRwXps"),
    Credit("Eclipse", "Tyler van der Hoeven, via Unsplash", "Unsplash License", "https://unsplash.com/photos/solar-eclipse-with-diamond-ring-effect-_ok8uVzL2gI"),
    Credit("Warp drive (photo)", "Casey Horner, via Unsplash", "Unsplash License", "https://unsplash.com/photos/timelapse-photography-of-warped-lines-RmoWqDCqN2E"),
    Credit("Neon city", "Valentin Beauvais, via Unsplash", "Unsplash License", "https://unsplash.com/photos/wet-street-at-night-in-tokyo-yVUQlyRlJSw"),
    Credit("Tokyo from above", "Pawel Nolbert, via Unsplash", "Unsplash License", "https://unsplash.com/photos/timelapse-photography-of-vehicles-and-buildings-4u2U8EO9OzY"),
    Credit("Night train", "Andre Benz, via Unsplash", "Unsplash License", "https://unsplash.com/photos/narrow-japanese-street-at-night-qi2hmCwlhcE"),
    Credit("Kabukicho", "Sérgio Rola, via Unsplash", "Unsplash License", "https://unsplash.com/photos/roadway-beside-commercial-buildings-bVM7IO7pt7s"),
)

@Composable
fun CreditsScreen(animate: Boolean, onClose: () -> Unit, onUpdateFound: (com.stellapp.disc.UpdateInfo) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val version by UplinkManager.version.collectAsStateWithLifecycle()
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var appUpdateMsg by remember { mutableStateOf<String?>(null) }
    var crash by remember { mutableStateOf(com.stellapp.disc.CrashLog.read(context)) }

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
                Text("CREDITS & SUPPORT", style = neonTitleStyle(20))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                InfoPanel("RESPONSIBLE USE", DangerRed) {
                    Body("Only download what you have the rights to: your own uploads, music the creator offers as a free download, or content whose license allows it, such as Creative Commons.")
                    Body("Many sites, including YouTube, don't allow downloading in their terms of service except through their own apps. You're responsible for how you use Uplink and for following the rules where you live.")
                    Body("Stellar Disc doesn't host, index or share any content. It only saves what you point it at onto your own phone.")
                }

                InfoPanel("SUPPORT", primary) {
                    Body("Downloads failing? Sites change all the time, and a newer yt-dlp usually fixes it.")
                    Text(
                        "yt-dlp " + (version ?: "not started yet"),
                        color = TextLo,
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    NeonChip(if (updating) "UPDATING…" else "UPDATE YT-DLP", false) {
                        if (!updating) {
                            updating = true
                            updateMsg = null
                            scope.launch {
                                updateMsg = UplinkManager.updateYtDlp(context)
                                updating = false
                            }
                        }
                    }
                    updateMsg?.let {
                        Text(it, color = secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    NeonChip("CHECK FOR APP UPDATES", false) {
                        appUpdateMsg = "Checking…"
                        scope.launch {
                            appUpdateMsg = when (val r = com.stellapp.disc.UpdateChecker.check(context, force = true)) {
                                is com.stellapp.disc.UpdateResult.Available -> {
                                    onUpdateFound(r.info)
                                    "v${r.info.version} is available"
                                }
                                com.stellapp.disc.UpdateResult.UpToDate -> "Stellar Disc is up to date"
                                is com.stellapp.disc.UpdateResult.Failed -> r.message
                                com.stellapp.disc.UpdateResult.Skipped -> null
                            }
                        }
                    }
                    appUpdateMsg?.let { Text(it, color = secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(12.dp))
                    crash?.let { report ->
                        Body("A crash log from last time is saved.")
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            NeonChip("REPORT IT", false) { openUrl(context, com.stellapp.disc.CrashLog.issueUrl(report)) }
                            NeonChip("CLEAR", false) {
                                com.stellapp.disc.CrashLog.clear(context)
                                crash = null
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    LinkText("Supported sites list ›") { openUrl(context, AppInfo.SUPPORTED_SITES_URL) }
                    if (AppInfo.SUPPORT_URL.isNotBlank()) {
                        LinkText("Report a problem ›") { openUrl(context, AppInfo.SUPPORT_URL) }
                    }
                    Body("YouTube sometimes needs extra checks that phones can't always pass, so some videos may only offer lower quality or fail. Other sites are usually more reliable.")
                }

                InfoPanel("CREDITS", secondary) {
                    Body("Stellar Disc is built on these open-source projects. Tap one to visit it.")
                    CREDITS.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(context, c.url) }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, color = TextHi, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(c.license, color = TextLo, fontFamily = Mono, fontSize = 11.sp)
                            }
                            Text(c.role, color = TextLo, fontSize = 12.sp)
                        }
                    }
                }

                InfoPanel("BACKDROP PHOTOS", secondary) {
                    Body("The photo backdrops are real images, used under the licences below. Tap one to see the original.")
                    BACKDROP_CREDITS.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(context, c.url) }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, color = TextHi, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(c.license, color = TextLo, fontFamily = Mono, fontSize = 11.sp)
                            }
                            Text(c.role, color = TextLo, fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    "Stellar Disc v${BuildConfig.VERSION_NAME}",
                    color = TextLo,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .neonPanel(color.copy(alpha = 0.7f), glow = 8.dp)
            .padding(16.dp)
    ) {
        Text(title, color = color, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, color = TextHi, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
