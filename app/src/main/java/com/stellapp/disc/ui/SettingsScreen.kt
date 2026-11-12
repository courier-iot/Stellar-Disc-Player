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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val strictOptions by vm.strictOptions.collectAsStateWithLifecycle()
    val solverFromGithub by vm.solverFromGithub.collectAsStateWithLifecycle()
    val solverMissing by UplinkManager.solverMissing.collectAsStateWithLifecycle()
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
                Text("SYSTEM CONFIG", style = neonTitleStyle(20))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Section("LOOK") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Accents.forEachIndexed { i, a ->
                            Column(
                                Modifier
                                    .clip(SmallCut)
                                    .clickable { vm.setAccent(i) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(a.primary, a.secondary)))
                                        .then(if (i == accent) Modifier.border(2.dp, TextHi, CircleShape) else Modifier)
                                )
                                Text(a.name.substringBefore(" ").uppercase(), color = if (i == accent) a.primary else TextLo, fontFamily = Mono, fontSize = 9.sp)
                            }
                        }
                    }
                    Text("PLAYER BACKDROP", color = TextLo, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Backdrop.entries.forEach { b ->
                            val picked = b == backdrop
                            Column(
                                Modifier
                                    .clip(SmallCut)
                                    .clickable {
                                        if (b == Backdrop.CUSTOM && (customVersion <= 0L || picked)) pickBackdropPhoto() else vm.setBackdrop(b)
                                    }
                                    .padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 66.dp, height = 92.dp)
                                        .clip(SmallCut)
                                        .border(if (picked) 2.dp else 1.dp, if (picked) MaterialTheme.colorScheme.primary else TextLo.copy(alpha = 0.3f), SmallCut)
                                ) {
                                    PlayerBackdrop(b, animate = picked && animate, playing = true, modifier = Modifier.fillMaxSize(), thumbnail = true)
                                }
                                Text(
                                    b.label.uppercase(),
                                    color = if (picked) MaterialTheme.colorScheme.primary else TextLo,
                                    fontFamily = Mono,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    Text(
                        if (backdrop == Backdrop.CUSTOM) "Tap YOUR PHOTO again to pick a different picture." else "Photo scenes are real images (credits in Credits & Support). YOUR PHOTO uses any picture from your gallery.",
                        color = TextLo,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ToggleRow("Animated starfield", "Turn off to save battery", animate) { vm.setAnimateBackground(it) }
                    ToggleRow("Modern home", "Off shows the \"under the hood\" library view", modernHome) { vm.setModernHome(it) }
                }

                Section("SOUND") {
                    LinkRow("Equalizer & bass boost", if (fx.supported) (if (fx.enabled) "On" else "Off") else "Start playing something first", onOpenEqualizer)
                    Text("Smooth fade between tracks", color = TextHi, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("Fades out the end and in the start of each song", color = TextLo, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "OFF", 2 to "2S", 4 to "4S", 6 to "6S", 8 to "8S").forEach { (sec, label) ->
                            NeonChip(label, fade == sec) { vm.setFade(sec) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
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

                Section("VIDEO") {
                    ToggleRow(
                        "Play videos as audio only",
                        "No picture, just the sound. Saves battery when listening in the background.",
                        audioOnly,
                    ) { vm.setAudioOnly(it) }
                }

                Section("UPLINK ENGINE") {
                    ToggleRow("Wi-Fi only", "Downloads wait until you're on Wi-Fi, so they don't use mobile data", wifiOnly) { vm.setWifiOnly(it) }
                    ToggleRow(
                        "Strict yt-dlp options",
                        "Only well-known safe options are used from your always-on and advanced options. Off: only options that run programs are blocked.",
                        strictOptions,
                    ) { vm.setStrictOptions(it) }
                    ToggleRow(
                        "Solver scripts from GitHub (fallback)",
                        if (solverMissing) "⚠ yt-dlp reported its YouTube solver missing. Turning this on lets it fetch the scripts from yt-dlp's GitHub."
                        else "Off: yt-dlp uses the solver built into its official release, so nothing is downloaded at run time.",
                        solverFromGithub,
                    ) { vm.setSolverFromGithub(it) }
                    if (UplinkManager.initError != null) {
                        Text("⚠ " + (UplinkManager.initError ?: ""), color = DangerRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (vm.hasJsRuntime) "✓ JavaScript runtime: QuickJS-NG built in. yt-dlp can solve YouTube's checks."
                        else "⚠ No JavaScript runtime in this build, so YouTube may fail or offer fewer formats.",
                        color = if (vm.hasJsRuntime) primary else DangerRed,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("YouTube sign-in", color = TextHi, fontSize = 14.sp)
                    Text(
                        if (ytSignedIn) "✓ Signed in. Uplink and streaming use your account for YouTube."
                        else "Use this if YouTube says \"sign in to confirm you're not a bot\".",
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
                        "Only your YouTube cookies are kept, privately inside the app. They work like a password, so consider a spare account: Google can flag accounts that download a lot. Import cookies.txt is the backup if Google won't allow sign-in inside apps.",
                        color = TextLo,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        "Nightly yt-dlp updates",
                        "Gets fixes for YouTube days earlier. Applies next time you tap Update yt-dlp in Credits & Support.",
                        nightly,
                    ) { vm.setNightly(it) }
                    Spacer(Modifier.height(8.dp))
                    Text("Always-on yt-dlp options", color = TextHi, fontSize = 14.sp)
                    Text("Added to every download and stream, e.g. --embed-chapters", color = TextLo, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    NeonTextField(globalOpts, { globalOpts = it }, placeholder = "--option value …", singleLine = false)
                    Spacer(Modifier.height(6.dp))
                    NeonChip("SAVE OPTIONS", false) { vm.setYtdlpGlobalOptions(globalOpts) }
                }

                Section("LYRICS") {
                    ToggleRow(
                        "Find lyrics online automatically",
                        "Looks up the title and artist on lrclib.net when you open lyrics. Off means only when you tap Find online.",
                        lyricsAuto,
                    ) { vm.setLyricsAuto(it) }
                }

                Section("LIBRARY") {
                    LinkRow("Listening stats", "Your top songs and artists, and hours listened", onOpenStats)
                    LinkRow("Rescan library", "Pick up new files right away") { vm.loadLibrary(force = true) }
                    if (!vm.hasVideoAccess()) {
                        LinkRow("Show all phone videos", "Allow video access for the Videos tab") {
                            videoPermission.launch(
                                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                        }
                    }
                    Text("Hidden folders", color = TextHi, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
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

                Section("BACKUP") {
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

                Section("ABOUT") {
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
                    LinkRow("Credits & Support", "Responsible use, yt-dlp updates, crash reports, licences", onOpenCredits)
                    LinkRow("Show the welcome tour again", null) {
                        vm.post("The welcome tour shows on next launch")
                        context.getSharedPreferences("stellar_disc", android.content.Context.MODE_PRIVATE).edit().putBoolean("onboarded", false).apply()
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
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .neonPanel(primary.copy(alpha = 0.5f), glow = 0.dp)
            .padding(16.dp)
    ) {
        Text(title, color = primary, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        content()
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

/** The equalizer and bass boost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(animate: Boolean, onClose: () -> Unit) {
    val fx by AudioFx.state.collectAsStateWithLifecycle()
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
                Text("EQUALIZER", style = neonTitleStyle(20))
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
                    return@Column
                }
                ToggleRow("Equalizer on", null, fx.enabled) { AudioFx.setEnabled(it) }

                SectionLabel("PRESETS")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fx.presets.forEachIndexed { i, name -> NeonChip(name.uppercase(), fx.preset == i) { AudioFx.usePreset(i) } }
                    NeonChip("FLAT", false) { AudioFx.resetFlat() }
                    if (fx.preset == -1) NeonChip("CUSTOM", true) {}
                }

                SectionLabel("BANDS")
                fx.bands.forEachIndexed { i, band ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (band.centerHz >= 1000) "${band.centerHz / 1000}k" else "${band.centerHz}",
                            color = TextLo,
                            fontFamily = Mono,
                            fontSize = 11.sp,
                            modifier = Modifier.width(44.dp),
                        )
                        androidx.compose.material3.Slider(
                            value = band.level.toFloat(),
                            onValueChange = { AudioFx.setBand(i, it.toInt()) },
                            valueRange = fx.minLevel.toFloat()..fx.maxLevel.toFloat(),
                            enabled = fx.enabled,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = primary,
                                activeTrackColor = primary,
                                inactiveTrackColor = primary.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+.1f".format(band.level / 100f),
                            color = TextHi,
                            fontFamily = Mono,
                            fontSize = 11.sp,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }

                if (fx.bassSupported) {
                    SectionLabel("BASS BOOST")
                    androidx.compose.material3.Slider(
                        value = fx.bass.toFloat(),
                        onValueChange = { AudioFx.setBass(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = fx.enabled,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        ),
                    )
                }
                Text(
                    "Values are in dB. The equalizer applies to everything Stellar Disc plays, including videos.",
                    color = TextLo,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
    }
}

private data class TourPage(val glyph: String, val title: String, val body: String)

private val TOUR = listOf(
    TourPage("◉", "WELCOME TO STELLAR//DISC", "A music player from deep space. Your songs, videos and playlists, all in one neon cockpit."),
    TourPage("⇣", "UPLINK", "Paste or share a link from YouTube, SoundCloud, Bandcamp and 1,000+ sites to download it, or just stream it. Only grab what you have the rights to."),
    TourPage("⇄", "SWAP", "Share playlists with friends: scan a QR code on the same Wi-Fi, or send a link list anywhere. The song checker skips what you already have."),
    TourPage("♪", "MAKE IT YOURS", "Lyrics, equalizer, sleep timer, a home screen widget, and accent colours in Settings. Next, Android will ask for access to your music."),
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
