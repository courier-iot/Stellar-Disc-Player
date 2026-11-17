package com.stellapp.disc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.stellapp.disc.HubShare
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.BuildConfig
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.UpdateChecker
import com.stellapp.disc.UpdateResult
import com.stellapp.disc.playback.AudioFx
import com.stellapp.disc.playback.PlaybackExtras
import com.stellapp.disc.uplink.UplinkManager
import com.stellapp.disc.ui.theme.accentGradient
import com.stellapp.disc.ui.theme.Accents
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.InkBlack
import com.stellapp.disc.ui.theme.InkAccents
import com.stellapp.disc.ui.theme.UiStyle
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The pages behind the Settings hub. */
private enum class ConfigPage(val code: String, val glyph: String, val title: String) {
    VISUALS("01", "◐", "VISUALS"),
    AUDIO("02", "♫", "AUDIO"),
    UPLINK("04", "⇣", "UPLINK"),
    ARCHIVE("05", "▤", "ARCHIVE"),
    SYSTEM("06", "⌬", "SYSTEM"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: PlayerViewModel,
    animate: Boolean,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenCredits: () -> Unit,
    onUpdateFound: (com.stellapp.disc.UpdateInfo) -> Unit,
    onOpenYtSignIn: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenTips: () -> Unit = {},
    onOpenMoveSend: () -> Unit = {},
    onOpenMoveReceive: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val accent by vm.accentIndex.collectAsStateWithLifecycle()
    val fade by PlaybackExtras.fadeSeconds.collectAsStateWithLifecycle()
    val fx by AudioFx.state.collectAsStateWithLifecycle()
    val lyricsAuto by vm.lyricsAuto.collectAsStateWithLifecycle()
    val hidden by vm.hiddenFolders.collectAsStateWithLifecycle()
    var updateMsg by remember { mutableStateOf<String?>(null) }
    val ytSignedIn by vm.ytSignedIn.collectAsStateWithLifecycle()
    val audioOnly by vm.audioOnly.collectAsStateWithLifecycle()
    val modernHome by vm.modernHome.collectAsStateWithLifecycle()
    val backdrop by vm.backdrop.collectAsStateWithLifecycle()
    val nightly by vm.nightly.collectAsStateWithLifecycle()
    var globalOpts by remember { mutableStateOf(vm.ytdlpGlobalOptions()) }
    val customVersion by CustomBackdrop.version.collectAsStateWithLifecycle()
    val btAutoplay by vm.btAutoplay.collectAsStateWithLifecycle()
    val pureBlack by vm.pureBlack.collectAsStateWithLifecycle()
    val uiStyle by vm.uiStyle.collectAsStateWithLifecycle()
    val inkAccent by vm.inkAccent.collectAsStateWithLifecycle()
    val inkDark by vm.inkDark.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val assistantDownloads by vm.assistantDownloads.collectAsStateWithLifecycle()
    val strictOptions by vm.strictOptions.collectAsStateWithLifecycle()
    val solverFromGithub by vm.solverFromGithub.collectAsStateWithLifecycle()
    val solverMissing by UplinkManager.solverMissing.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val engineVersion by UplinkManager.version.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { CustomBackdrop.refresh(context) }
    val backdropPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                vm.post("Setting up your backdrop…")
                if (CustomBackdrop.import(context, uri)) {
                    vm.setBackdrop(Backdrop.CUSTOM)
                    vm.post("Your photo is now the player backdrop")
                } else {
                    vm.post("Couldn't use that image")
                }
            }
        }
    }
    val pickBackdropPhoto = {
        backdropPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importYtCookies(it) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AudioFx.setVisualizer(true) else vm.post("The real visualizer needs that permission")
    }
    val videoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.loadLibrary(force = true)
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importBackup(it) }
    }
    val importM3u = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importM3u(it, displayName(context, it)) }
    }

    // The engine's one status line, shared by the hub tile and the Uplink page.
    val fault = UplinkManager.initError
    val (engineOk, engineStatus) = when {
        fault != null -> false to "⚠ Engine fault: $fault"
        !vm.hasJsRuntime -> false to "⚠ Engine online, but YouTube may struggle: this build has no JavaScript runtime."
        solverMissing && !solverFromGithub -> false to "⚠ YouTube's locks changed. Open Engine room below and turn on Solver scripts from GitHub."
        else -> true to "✓ Engine ready" + (engineVersion?.let { " · yt-dlp $it" } ?: "") + " · YouTube unlocked"
    }

    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { n -> ConfigPage.entries.firstOrNull { it.name == n } }

    BackHandler(onBack = onClose)
    BackHandler(enabled = page != null) { pageName = null }

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
            Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (page != null) pageName = null else onClose() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (page == null) "SYSTEM CONFIG" else page.title, style = neonTitleStyle(20), maxLines = 1)
                    Text(
                        if (page == null) "STELLAR//DISC v${BuildConfig.VERSION_NAME} · ALL SYSTEMS NOMINAL"
                        else "CONFIG // ${page.code} · ${page.title}",
                        color = TextLo,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                when (page) {
                    null -> {
                        // ---------- the hub ----------
                        Spacer(Modifier.height(10.dp))
                        val fxSummary = when {
                            !fx.supported -> "Play something to wake it"
                            !fx.enabled -> "Bypassed"
                            fx.custom != null -> "Sound: ${fx.custom}"
                            fx.preset in fx.presets.indices -> "Preset: ${fx.presets[fx.preset]}"
                            else -> "Custom curve"
                        }
                        val tiles = listOf(
                            HubTile(ConfigPage.VISUALS.code, ConfigPage.VISUALS.glyph, "VISUALS",
                                if (uiStyle == UiStyle.INK) (if (inkDark) "Ink · charcoal" else "Ink · paper") else "Cyber · ${backdrop.label}") { pageName = ConfigPage.VISUALS.name },
                            HubTile(ConfigPage.AUDIO.code, ConfigPage.AUDIO.glyph, "AUDIO",
                                (if (fade > 0) "Fade ${fade}s" else "No fade") + if (fx.leveling) " · levelled" else "") { pageName = ConfigPage.AUDIO.name },
                            HubTile("03", "≋", "FX RACK", fxSummary, redirect = true, onClick = onOpenEqualizer),
                            HubTile(ConfigPage.UPLINK.code, ConfigPage.UPLINK.glyph, "UPLINK",
                                if (engineOk) ("Engine ready" + (if (ytSignedIn) " · signed in" else "")) else "Needs attention", warn = !engineOk) { pageName = ConfigPage.UPLINK.name },
                            HubTile(ConfigPage.ARCHIVE.code, ConfigPage.ARCHIVE.glyph, "ARCHIVE",
                                "${songs.count { it.showsInMusic }} tracks · backups") { pageName = ConfigPage.ARCHIVE.name },
                            HubTile(ConfigPage.SYSTEM.code, ConfigPage.SYSTEM.glyph, "SYSTEM", "v${BuildConfig.VERSION_NAME} · updates & help") { pageName = ConfigPage.SYSTEM.name },
                        )
                        tiles.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { t -> HubTileView(t, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        SectionLabel("QUICK JUMPS")
                        LinkRow("Listening stats", "Your top songs and artists, and hours listened", onOpenStats)
                        LinkRow("Gestures & tips", "Every swipe, tap and shortcut in one list", onOpenTips)
                        LinkRow("Credits & Support", "Responsible use, yt-dlp updates, crash reports, licences", onOpenCredits)
                    }

                    ConfigPage.VISUALS -> {
                        Fold("THEME", if (uiStyle == UiStyle.INK) "Ink & Screentone" else "Cyber", startOpen = true) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiStyle.entries.forEach { st -> NeonChip(st.label, uiStyle == st) { vm.setUiStyle(st) } }
                            }
                            Text(
                                if (uiStyle == UiStyle.INK) "A manga page: paper, ink, screentone and comic lettering."
                                else "The neon cockpit: deep space, glowing edges and animated backdrops.",
                                color = TextLo,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            if (uiStyle == UiStyle.INK) {
                                Spacer(Modifier.height(10.dp))
                                ToggleRow("Charcoal paper", "Grey paper with pale ink. Easier on the eyes at night.", inkDark) { vm.setInkDark(it) }
                            }
                        }
                        if (uiStyle == UiStyle.INK) {
                            Fold("INK", InkAccents[inkAccent.coerceIn(0, InkAccents.lastIndex)].name) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val inks = if (inkDark) com.stellapp.disc.ui.theme.InkAccentsDark else InkAccents
                                    inks.forEachIndexed { i, a ->
                                        Column(
                                            Modifier
                                                .clickable { vm.setInkAccent(i) }
                                                .padding(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(36.dp)
                                                    .background(a.primary)
                                                    .border(if (i == inkAccent) 3.dp else 1.5.dp, InkBlack)
                                            )
                                            Text(a.name.uppercase(), color = if (i == inkAccent) InkBlack else TextLo, fontFamily = Mono, fontSize = 11.sp)
                                        }
                                    }
                                }
                                Text(
                                    "Ink pages use their own paper backdrop. Switch back to Cyber for the scenes and photo backdrops.",
                                    color = TextLo,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                        if (uiStyle == UiStyle.CYBER) {
                            Fold("ACCENT", Accents[accent.coerceIn(0, Accents.lastIndex)].name, startOpen = true) {
                                AccentGrid(accent) { vm.setAccent(it) }
                            }
                            Fold("PLAYER BACKDROP", backdrop.label, startOpen = true) {
                                BackdropGrid(backdrop, animate, customVersion) { b ->
                                    if (b == Backdrop.CUSTOM && (customVersion <= 0L || b == backdrop)) pickBackdropPhoto() else vm.setBackdrop(b)
                                }
                                Text(
                                    if (backdrop == Backdrop.CUSTOM) "Tap YOUR PHOTO again to pick a different picture."
                                    else "Photo scenes are real images (credits in Credits & Support). YOUR PHOTO uses any picture from your gallery.",
                                    color = TextLo,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        Fold("DISPLAY", null) {
                            if (uiStyle == UiStyle.CYBER) {
                                ToggleRow("Pure black", "True-black backgrounds. Saves battery on OLED screens", pureBlack) { vm.setPureBlack(it) }
                                ToggleRow("Animated starfield", "Turn off to save battery", animate) { vm.setAnimateBackground(it) }
                            }
                            ToggleRow("Modern home", "Off shows the \"under the hood\" library view", modernHome) { vm.setModernHome(it) }
                        }
                    }

                    ConfigPage.AUDIO -> {
                        LinkPanel("≋ FX RACK", "Equalizer, bass, surround, loudness, reverb, balance, speed, pitch and your saved sounds", onOpenEqualizer)
                        Fold("PLAYBACK", if (fade > 0) "Fade ${fade}s" else "No fade", startOpen = true) {
                            Text("Smooth fade between tracks", color = TextHi, fontSize = 14.sp)
                            Text("Fades out the end and in the start of each song", color = TextLo, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "OFF", 2 to "2S", 4 to "4S", 6 to "6S", 8 to "8S").forEach { (sec, label) ->
                                    NeonChip(label, fade == sec) { vm.setFade(sec) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            ToggleRow(
                                "Volume levelling",
                                if (fx.levelingSupported) "Evens out quiet and loud tracks, handy for downloads from different sites" else "Needs Android 9 or newer",
                                fx.leveling && fx.levelingSupported,
                            ) { if (fx.levelingSupported) AudioFx.setLeveling(it) }
                            ToggleRow(
                                "Bluetooth auto-play",
                                "Starts playing when Bluetooth headphones or your car connect (while Stellar Disc is open or paused in the background)",
                                btAutoplay,
                            ) { vm.setBtAutoplay(it) }
                        }
                        Fold("VISUALIZER", if (fx.visualizer) "Real" else "Simulated") {
                            ToggleRow(
                                "Real visualizer",
                                "Bars react to the actual music. Android calls the permission \"microphone\", but only the app's own sound is read.",
                                fx.visualizer,
                            ) { on ->
                                if (!on) {
                                    AudioFx.setVisualizer(false)
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    AudioFx.setVisualizer(true)
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        Fold("VIDEO", if (audioOnly) "Audio only" else null) {
                            ToggleRow(
                                "Play videos as audio only",
                                "No picture, just the sound. Saves battery when listening in the background.",
                                audioOnly,
                            ) { vm.setAudioOnly(it) }
                            Text(
                                "Videos saved by Uplink join shuffle mixes like songs. For any other video, long-press it and pick Count as music.",
                                color = TextLo,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Fold("LINKED APPS", null) {
                            var hubShare by remember { mutableStateOf(HubShare.enabled(context)) }
                            ToggleRow(
                                "Share listening with Hub Connect S",
                                if (HubShare.hubInstalled(context)) "Your Stellar Hub resident learns your music taste and listens along. Only titles, artists and finishes or skips are shared, and only with that app."
                                else "Install Hub Connect S to share your listening with your Stellar Hub resident",
                                hubShare,
                            ) { HubShare.setEnabled(context, it); hubShare = it }
                        }
                    }

                    ConfigPage.UPLINK -> {
                        Text(engineStatus, color = if (engineOk) primary else DangerRed, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                        Fold("DOWNLOADS", if (wifiOnly) "Wi-Fi only" else null, startOpen = true) {
                            ToggleRow("Wi-Fi only", "Downloads wait until you're on Wi-Fi, so they don't use mobile data", wifiOnly) { vm.setWifiOnly(it) }
                            ToggleRow(
                                "Let assistants start downloads",
                                "When Stella (StellAI) or another app asks Uplink for a song, download it straight away. Off: Uplink opens filled in and you tap Download. Any app on this phone can send these requests.",
                                assistantDownloads,
                            ) { vm.setAssistantDownloads(it) }
                        }
                        Fold("YOUTUBE SIGN-IN", if (ytSignedIn) "Signed in" else "Signed out", startOpen = !ytSignedIn) {
                            Text(
                                if (ytSignedIn) "✓ Signed in. Uplink and live feeds use your account for YouTube."
                                else "Use this if a download says the site thinks we're a bot.",
                                color = if (ytSignedIn) primary else TextLo,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeonChip(if (ytSignedIn) "SIGN IN AGAIN" else "SIGN IN TO YOUTUBE", !ytSignedIn, onOpenYtSignIn)
                                NeonChip("IMPORT COOKIES.TXT", false) { cookiePicker.launch(arrayOf("text/plain", "*/*")) }
                                if (ytSignedIn) NeonChip("SIGN OUT", false) { vm.clearYtCookies() }
                            }
                            Text(
                                "Your cookies stay encrypted inside the app, but they work like a password, so a spare account is safest: Google can flag accounts that download a lot.",
                                color = TextLo,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Fold("ENGINE ROOM · FOR TINKERERS", null) {
                            ToggleRow(
                                "Nightly yt-dlp updates",
                                "Gets fixes for YouTube days earlier. Applies next time you tap Update yt-dlp in Credits & Support.",
                                nightly,
                            ) { vm.setNightly(it) }
                            ToggleRow(
                                "Strict yt-dlp options",
                                "Only well-known safe options are used from your always-on and raw commands. Off: only options that run programs are blocked.",
                                strictOptions,
                            ) { vm.setStrictOptions(it) }
                            ToggleRow(
                                "Solver scripts from GitHub",
                                "A fallback for when YouTube changes its locks. Off: the solver built into yt-dlp is used, and nothing extra is downloaded.",
                                solverFromGithub,
                            ) { vm.setSolverFromGithub(it) }
                            Spacer(Modifier.height(8.dp))
                            Text("Always-on yt-dlp options", color = TextHi, fontSize = 14.sp)
                            Text("Added to every download and live feed, e.g. --embed-chapters", color = TextLo, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            NeonTextField(globalOpts, { globalOpts = it }, placeholder = "--option value …", singleLine = false)
                            Spacer(Modifier.height(6.dp))
                            NeonChip("SAVE OPTIONS", false) { vm.setYtdlpGlobalOptions(globalOpts) }
                        }
                    }

                    ConfigPage.ARCHIVE -> {
                        Fold("LIBRARY", "${songs.count { it.showsInMusic }} tracks", startOpen = true) {
                            LinkRow("Listening stats", "Your top songs and artists, and hours listened", onOpenStats)
                            LinkRow("Rescan library", "Pick up new files right away") { vm.loadLibrary(force = true) }
                            if (!vm.hasVideoAccess()) {
                                LinkRow("Show all phone videos", "Allow video access for the Videos tab") {
                                    videoPermission.launch(
                                        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
                                    )
                                }
                            }
                        }
                        Fold("HIDDEN FOLDERS", if (hidden.isEmpty()) "None" else "${hidden.size}") {
                            if (hidden.isEmpty()) {
                                Text("None. Hide a folder from its page in the Folders tab.", color = TextLo, fontSize = 12.sp)
                            } else {
                                hidden.sorted().forEach { f ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(f, color = TextLo, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                                        NeonChip("UNHIDE", false) { vm.unhideFolder(f) }
                                    }
                                }
                            }
                        }
                        Fold("LYRICS", if (lyricsAuto) "Auto" else "On request") {
                            ToggleRow(
                                "Find lyrics online automatically",
                                "Looks up the title and artist on lrclib.net (then lyrics.ovh) when you open lyrics. Off means only when you tap Find online or Search.",
                                lyricsAuto,
                            ) { vm.setLyricsAuto(it) }
                        }
                        Fold("MOVE PHONES", null) {
                            LinkRow("Move to a new phone", "Send your music, videos, playlists and settings over Wi-Fi", onOpenMoveSend)
                            LinkRow("Receive from your old phone", "Scan the code your old phone shows", onOpenMoveReceive)
                        }
                        Fold("BACKUP", null) {
                            LinkRow("Export backup", "Playlists and settings to a file") {
                                exportBackup.launch("stellar-disc-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json")
                            }
                            LinkRow("Restore backup", "Rebuilds playlists from your library, even on a new phone") {
                                importBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                            LinkRow("Import M3U playlist", "From other music apps or a computer") {
                                importM3u.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                            }
                            Text("To export a playlist as M3U, open it and tap Share.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    ConfigPage.SYSTEM -> {
                        Fold("UPDATES", "v${BuildConfig.VERSION_NAME}", startOpen = true) {
                            LinkRow("Check for updates", updateMsg ?: "You're on v${BuildConfig.VERSION_NAME}") {
                                updateMsg = "Checking…"
                                scope.launch {
                                    updateMsg = when (val r = UpdateChecker.check(context, force = true)) {
                                        is UpdateResult.Available -> {
                                            onUpdateFound(r.info)
                                            "v${r.info.version} is available"
                                        }
                                        UpdateResult.UpToDate -> "You're up to date (v${BuildConfig.VERSION_NAME})"
                                        is UpdateResult.Failed -> r.message
                                        UpdateResult.Skipped -> null
                                    }
                                }
                            }
                        }
                        Fold("HELP", null, startOpen = true) {
                            LinkRow("Gestures & tips", "Every swipe, tap and shortcut in one list", onOpenTips)
                            LinkRow("Credits & Support", "Responsible use, yt-dlp updates, crash reports, licences", onOpenCredits)
                            LinkRow("Show the welcome tour again", null) {
                                vm.post("The welcome tour shows on next launch")
                                context.getSharedPreferences("stellar_disc", android.content.Context.MODE_PRIVATE).edit().putBoolean("onboarded", false).apply()
                            }
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

private class HubTile(
    val code: String,
    val glyph: String,
    val title: String,
    val status: String,
    val warn: Boolean = false,
    val redirect: Boolean = false,
    val onClick: () -> Unit,
)

/** One square on the Settings hub: a code, a glyph, the name and a live status line. */
@Composable
private fun HubTileView(t: HubTile, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Column(
        modifier
            .height(118.dp)
            .neonPanel(if (t.warn) DangerRed else primary.copy(alpha = 0.7f), glow = 6.dp)
            .clickable(onClick = t.onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("// ${t.code}", color = TextLo, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Text(if (t.redirect) "↗" else "›", color = secondary, fontSize = 16.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(t.glyph, color = if (t.warn) DangerRed else primary, fontSize = 22.sp)
        Text(t.title, color = TextHi, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp, maxLines = 1)
        Text(
            t.status,
            color = if (t.warn) DangerRed else TextLo,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** A collapsible group on a Settings page. The summary shows while it's folded. */
@Composable
private fun Fold(title: String, summary: String?, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .neonPanel(primary.copy(alpha = if (open) 0.6f else 0.3f), glow = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (open) "▾" else "▸", color = primary, fontFamily = Mono, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, color = primary, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            if (summary != null && !open) {
                Text(
                    summary.uppercase(),
                    color = TextLo,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 150.dp),
                )
            }
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

/** A Settings row that opens another screen, styled like a fold header. */
@Composable
private fun LinkPanel(title: String, subtitle: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .neonPanel(primary.copy(alpha = 0.6f), glow = 6.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = primary, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
            Text(subtitle, color = TextLo, fontSize = 12.sp)
        }
        Text("↗", color = MaterialTheme.colorScheme.secondary, fontSize = 18.sp)
    }
}

/** Accent colours in an even grid. */
@Composable
private fun AccentGrid(selected: Int, onPick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(4, (maxWidth / 72.dp).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Accents.withIndex().toList().chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (i, a) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(SmallCut)
                                .clickable { onPick(i) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(a.primary, a.secondary)))
                                    .then(if (i == selected) Modifier.border(2.dp, TextHi, CircleShape) else Modifier)
                            )
                            Text(
                                a.name.substringBefore(" ").uppercase(),
                                color = if (i == selected) a.primary else TextLo,
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Backdrops in a fixed grid: every cell is the same width and every name gets two lines, so long
 * names like "Primordial black holes" no longer push the rows out of line.
 */
@Composable
private fun BackdropGrid(selected: Backdrop, animate: Boolean, customVersion: Long, onPick: (Backdrop) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(3, (maxWidth / 84.dp).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Backdrop.entries.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    row.forEach { b ->
                        val picked = b == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(SmallCut)
                                .clickable { onPick(b) }
                                .padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clip(SmallCut)
                                    .border(if (picked) 2.dp else 1.dp, if (picked) primary else TextLo.copy(alpha = 0.3f), SmallCut)
                            ) {
                                // Your photo's thumbnail follows the picture you picked.
                                androidx.compose.runtime.key(if (b == Backdrop.CUSTOM) customVersion else 0L) {
                                    PlayerBackdrop(b, animate = picked && animate, playing = true, modifier = Modifier.fillMaxSize(), thumbnail = true)
                                }
                            }
                            Text(
                                b.label.uppercase(),
                                color = if (picked) primary else TextLo,
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                maxLines = 2,
                                minLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = TextLo, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (e: Exception) {
    null
}

/**
 * The FX rack: equalizer bands up top, then the other effects as fold-down cards side by side,
 * and your own saved sounds next to the phone's presets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(vm: PlayerViewModel, animate: Boolean, onClose: () -> Unit) {
    val fx by AudioFx.state.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var saving by remember { mutableStateOf(false) }
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
            Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text("FX RACK", style = neonTitleStyle(20))
                    Text("CONFIG // 03 · SIGNAL CHAIN", color = TextLo, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 2.sp)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (!fx.supported) {
                    Text(
                        "Start playing something, then come back. If it still doesn't appear, this phone doesn't offer an equalizer to apps.",
                        color = TextLo,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                } else {
                    ToggleRow("Effects on", "Equalizer, bass, surround, loudness and reverb. Balance, speed and pitch work either way.", fx.enabled) { AudioFx.setEnabled(it) }

                    SectionLabel("PRESETS")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fx.presets.forEachIndexed { i, name -> NeonChip(name.uppercase(), fx.preset == i && fx.custom == null) { AudioFx.usePreset(i) } }
                        NeonChip("FLAT", false) { AudioFx.resetFlat() }
                    }

                    SectionLabel("MY SOUNDS")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fx.customs.forEach { p -> NeonChip("★ ${p.name.uppercase()}", fx.custom == p.name) { vm.applySound(p) } }
                        NeonChip("+ SAVE CURRENT", false) { saving = true }
                        fx.custom?.let { name -> NeonChip("✕ DELETE ${name.uppercase()}", false) { AudioFx.deleteCustom(name) } }
                    }
                    Text(
                        "A saved sound keeps every slider below, plus speed and pitch.",
                        color = TextLo,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    Fold("EQUALIZER", if (fx.custom != null) fx.custom else if (fx.preset in fx.presets.indices) fx.presets[fx.preset] else "Custom", startOpen = true) {
                        fx.bands.forEachIndexed { i, band ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (band.centerHz >= 1000) "${band.centerHz / 1000}k" else "${band.centerHz}",
                                    color = TextLo,
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(40.dp),
                                )
                                FxSlider(band.level.toFloat(), fx.minLevel.toFloat()..fx.maxLevel.toFloat(), primary, fx.enabled, Modifier.weight(1f)) {
                                    AudioFx.setBand(i, it.toInt())
                                }
                                Text(
                                    "%+.1f".format(band.level / 100f),
                                    color = TextHi,
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(40.dp),
                                )
                            }
                        }
                        Text("Values are in dB.", color = TextLo, fontSize = 11.sp)
                    }

                    // Side-by-side fold-down cards.
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        FxCard("BASS", if (fx.bassSupported) pct(fx.bass, 1000) else "N/A", Modifier.weight(1f)) {
                            if (fx.bassSupported) FxSlider(fx.bass.toFloat(), 0f..1000f, secondary, fx.enabled) { AudioFx.setBass(it.toInt()) }
                            else FxNote("This phone doesn't offer bass boost")
                        }
                        FxCard("SURROUND", if (fx.virtualizerSupported) pct(fx.virtualizer, 1000) else "N/A", Modifier.weight(1f)) {
                            if (fx.virtualizerSupported) {
                                FxSlider(fx.virtualizer.toFloat(), 0f..1000f, secondary, fx.enabled) { AudioFx.setVirtualizer(it.toInt()) }
                                FxNote("Widens the stage. Best on headphones")
                            } else FxNote("This phone doesn't offer surround")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        FxCard("LOUDNESS", if (fx.loudnessSupported) "+%.1f dB".format(fx.loudness / 100f) else "N/A", Modifier.weight(1f)) {
                            if (fx.loudnessSupported) {
                                FxSlider(fx.loudness.toFloat(), 0f..1000f, secondary, fx.enabled) { AudioFx.setLoudness(it.toInt()) }
                                FxNote("Lifts quiet tracks. Go easy, it can clip")
                            } else FxNote("This phone doesn't offer loudness")
                        }
                        FxCard("REVERB", if (fx.reverbSupported) AudioFx.REVERBS[fx.reverb.coerceIn(0, AudioFx.REVERBS.lastIndex)] else "N/A", Modifier.weight(1f)) {
                            if (fx.reverbSupported) {
                                AudioFx.REVERBS.forEachIndexed { i, name ->
                                    Text(
                                        (if (fx.reverb == i) "◉ " else "○ ") + name,
                                        color = if (fx.reverb == i) primary else TextHi,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { AudioFx.setReverb(i) }
                                            .padding(vertical = 5.dp),
                                    )
                                }
                                FxNote("Some phones ignore reverb")
                            } else FxNote("This phone doesn't offer reverb")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    val bal = fx.balance
                    FxCard(
                        "BALANCE",
                        when {
                            !fx.balanceSupported -> "N/A"
                            bal == 0 -> "Centre"
                            bal < 0 -> "L ${-bal}%"
                            else -> "R $bal%"
                        },
                        Modifier.weight(1f),
                    ) {
                        if (fx.balanceSupported) {
                            FxSlider(bal.toFloat(), -100f..100f, secondary, true) { AudioFx.setBalance(((it / 5f).toInt()) * 5) }
                            Row(Modifier.fillMaxWidth()) {
                                Text("L", color = TextLo, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(
                                    "CENTRE",
                                    color = primary,
                                    fontFamily = Mono,
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable { AudioFx.setBalance(0) },
                                )
                                Text("R", color = TextLo, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        } else FxNote("Needs Android 9 or newer")
                    }
                    FxCard("SPEED & PITCH", "%.2f× · %.2f".format(player.speed, player.pitch), Modifier.weight(1f)) {
                        Text("SPEED %.2f×".format(player.speed), color = TextLo, fontFamily = Mono, fontSize = 10.sp)
                        FxSlider(player.speed, 0.5f..2f, secondary, true) { vm.setSpeed((it * 20f).toInt() / 20f) }
                        Text("PITCH %.2f".format(player.pitch), color = TextLo, fontFamily = Mono, fontSize = 10.sp)
                        FxSlider(player.pitch, 0.5f..2f, secondary, true) { vm.setPitch((it * 20f).toInt() / 20f) }
                        Text(
                            "RESET",
                            color = primary,
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clickable {
                                    vm.setSpeed(1f)
                                    vm.setPitch(1f)
                                }
                                .padding(vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("⟲ RESET ALL EFFECTS", false) {
                        AudioFx.resetAll()
                        vm.setSpeed(1f)
                        vm.setPitch(1f)
                    }
                }
                Text(
                    "Everything here applies to all Stellar Disc plays, including videos.",
                    color = TextLo,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
    }

    if (saving) {
        NameDialog(title = "SAVE THIS SOUND", initial = fx.custom ?: "", confirmLabel = "SAVE", onDismiss = { saving = false }) { name ->
            vm.saveSound(name)
            saving = false
        }
    }
}

private fun pct(value: Int, max: Int): String = "${(value * 100f / max).toInt()}%"

/** A fold-down effect card for the FX rack's two-column grid. */
@Composable
private fun FxCard(title: String, value: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.neonPanel(primary.copy(alpha = if (open) 0.6f else 0.3f), glow = 0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = primary, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(if (open) "▴" else "▾", color = primary, fontSize = 13.sp)
            }
            Text(value, color = TextHi, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
        }
        if (open) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) { content() }
        }
    }
}

@Composable
private fun FxNote(text: String) {
    Text(text, color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

@Composable
private fun FxSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    androidx.compose.material3.Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        enabled = enabled,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = color,
            activeTrackColor = color,
            inactiveTrackColor = color.copy(alpha = 0.2f),
        ),
        modifier = modifier,
    )
}

private data class TourPage(val glyph: String, val title: String, val body: String)

private val TOUR = listOf(
    TourPage("◉", "WELCOME TO STELLAR//DISC", "A music player from deep space. Your songs, videos and playlists, all in one neon cockpit."),
    TourPage("⇆", "SWIPE TO STEER", "Swipe the disc or the mini player to change songs. Double-tap the disc to pause, long-press it for the queue, swipe up for lyrics."),
    TourPage("♥", "YOUR FAVOURITES", "Tap ♥ in the player, the notification or the widget. Favourites, playlists and mixes all live on the home screen."),
    TourPage("⇣", "UPLINK", "Paste or share a link from YouTube, SoundCloud, Bandcamp and 1,000+ sites to download it, or just stream it. Only grab what you have the rights to."),
    TourPage("⇄", "SWAP", "Share playlists with friends: scan a QR code on the same Wi-Fi, or send a link list anywhere. The song checker skips what you already have."),
    TourPage("♪", "MAKE IT YOURS", "Backdrops, accent colours, pure black, lyrics, equalizer and a resizable widget. Every gesture is in Settings → System → Gestures & tips. Next, Android will ask for access to your music."),
)

/** First-run tour. Permissions are asked right after it. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var page by remember { mutableStateOf(0) }
    val p = TOUR[page]
    BackHandler(enabled = page > 0) { page-- }

    Box(Modifier.fillMaxSize()) {
        Starfield(Modifier.fillMaxSize(), animate = true, grid = page == 0, speed = 0.8f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "SKIP",
                    color = TextLo,
                    fontFamily = Mono,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .clickable(onClick = onFinish)
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(p.glyph, color = primary, fontSize = 72.sp, style = neonTitleStyle(72))
            Spacer(Modifier.height(24.dp))
            Text(p.title, style = neonTitleStyle(20), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(p.body, color = TextHi, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TOUR.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 22.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (i == page) primary else TextLo.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .neonPanel(primary, glow = 18.dp, fill = Color.Transparent)
                    .background(Brush.horizontalGradient(accentGradient()))
                    .clickable { if (page < TOUR.lastIndex) page++ else onFinish() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (page < TOUR.lastIndex) "NEXT ▶" else "LAUNCH ▶",
                    color = com.stellapp.disc.ui.theme.Void,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
            }
        }
    }
}
