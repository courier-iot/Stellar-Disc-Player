package com.stellapp.disc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.CrashLog
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.UpdateChecker
import com.stellapp.disc.UpdateInfo
import com.stellapp.disc.UpdateResult
import com.stellapp.disc.swap.SwapReceiver
import com.stellapp.disc.uplink.UplinkManager
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import kotlinx.coroutines.delay

@Composable
fun DiscApp(vm: PlayerViewModel, inPip: Boolean, onEnterPip: () -> Unit) {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val requested = remember {
        buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    fun hasAccess() =
        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasAccess()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAccess() }
    LaunchedEffect(onboarded) {
        if (onboarded && !granted && !asked) {
            asked = true
            launcher.launch(requested)
        }
    }
    LaunchedEffect(granted) {
        if (granted) {
            vm.loadLibrary()
            // Downloads that were cut off last time (app closed, phone restarted…) carry on.
            val resumed = UplinkManager.resumePending(context)
            if (resumed > 0) vm.post("Resuming $resumed download${if (resumed == 1) "" else "s"} from last time")
        }
    }

    val animateSetting by vm.animateBackground.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showVideo by rememberSaveable { mutableStateOf(false) }
    var showUplink by rememberSaveable { mutableStateOf(false) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showSwap by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEq by rememberSaveable { mutableStateOf(false) }
    var sharePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var showShare by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showYtSignIn by rememberSaveable { mutableStateOf(false) }
    // Uplink's engines spool up on the way in and cool down on the way out.
    var engineGate by remember { mutableStateOf<EngineGate?>(null) }
    var shownGate by remember { mutableStateOf(EngineGate.SPOOL) }
    LaunchedEffect(engineGate) { engineGate?.let { shownGate = it } }
    LaunchedEffect(showUplink, granted) {
        if (showUplink && granted) {
            engineGate = EngineGate.SPOOL
        }
    }
    // Leaving Uplink for the player or Swap: shut the engines off without the screen.
    val leaveUplinkQuietly: () -> Unit = {
        UplinkManager.coolDown()
        showUplink = false
    }
    var homeGroup by remember { mutableStateOf<SongGroup?>(null) }
    // The open page's key survives Android closing the app in the background, so it can be rebuilt.
    var homeGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showGroup by rememberSaveable { mutableStateOf(false) }
    val modernHome by vm.modernHome.collectAsStateWithLifecycle()
    // Only the screen on top animates: anything fully covered stops drawing, which saves battery.
    val animate = animateSetting && !inPip
    val playerUp = showPlayer && state.current != null
    val toolUp = showUplink || showSwap || showShare || showSettings || showYtSignIn || showEq || showCredits || showStats
    val homeAnimate = animate && !(playerUp || showSearch || showGroup || toolUp || showVideo)
    val audioOnly by vm.audioOnly.collectAsStateWithLifecycle()
    // With "audio only" on, videos open in Now Playing instead of fullscreen.
    val openVideo: () -> Unit = {
        if (audioOnly) {
            showPlayer = true
        } else {
            showVideo = true
        }
    }
    val openGroup: (SongGroup) -> Unit = {
        homeGroup = it
        homeGroupKey = it.key
        showGroup = true
    }
    val librarySongs by vm.songs.collectAsStateWithLifecycle()
    val allPlaylists by vm.playlists.collectAsStateWithLifecycle()
    val favIds by vm.favourites.collectAsStateWithLifecycle()
    LaunchedEffect(showGroup, homeGroup, librarySongs, allPlaylists) {
        if (showGroup && homeGroup == null) {
            val key = homeGroupKey
            val rebuilt = if (key != null && librarySongs.isNotEmpty()) restoreGroup(key, librarySongs, allPlaylists, favIds) else null
            when {
                rebuilt != null -> { homeGroup = rebuilt }
                librarySongs.isNotEmpty() -> { showGroup = false }
            }
        }
    }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }

    // Shared links from other apps.
    val shared by vm.sharedUrl.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null) {
            showCredits = false
            showUplink = true
        }
    }
    val sharedSwap by vm.sharedSwap.collectAsStateWithLifecycle()
    val libraryReady by vm.libraryReady.collectAsStateWithLifecycle()
    LaunchedEffect(sharedSwap, libraryReady) {
        val text = sharedSwap
        if (text != null && libraryReady) {
            SwapReceiver.startLinks(context, text, vm.songs.value)
            vm.consumeSharedSwap()
            if (showUplink) leaveUplinkQuietly()
            showSwap = true
        }
    }

    // Once a day, quietly check GitHub for a newer version.
    LaunchedEffect(Unit) {
        delay(4_000)
        val r = UpdateChecker.check(context, force = false)
        if (r is UpdateResult.Available) update = r.info
    }

    // A video ended or was replaced by music: leave fullscreen.
    LaunchedEffect(state.current?.isVideo) {
        if (state.current?.isVideo != true) showVideo = false
    }

    // "What's new" once after each update (new installs just had the tour, so they skip it).
    val versionPrefs = remember { context.getSharedPreferences("stellar_disc", android.content.Context.MODE_PRIVATE) }
    val thisVersion = com.stellapp.disc.BuildConfig.VERSION_NAME
    var whatsNew by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(onboarded) {
        if (onboarded && versionPrefs.getString("last_seen_version", null) != thisVersion) {
            whatsNew = WhatsNew.forVersion(thisVersion)
            versionPrefs.edit().putString("last_seen_version", thisVersion).apply()
        }
    }

    if (!onboarded) {
        OnboardingScreen(onFinish = {
            versionPrefs.edit().putString("last_seen_version", thisVersion).apply()
            vm.setOnboarded()
        })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Starfield(Modifier.fillMaxSize(), animate = homeAnimate, speed = 0.6f)

        if (granted && modernHome) {
            HomeScreen(
                vm = vm,
                onOpenPlayer = { showPlayer = true },
                onOpenSettings = { showSettings = true },
                onOpenUplink = { showUplink = true },
                onOpenSwap = { showSwap = true },
                onOpenSearch = { showSearch = true },
                onOpenGroup = openGroup,
                onUnderTheHood = { vm.setModernHome(false) },
            )
        } else if (granted) {
            LibraryScreen(
                vm = vm,
                onOpenPlayer = { showPlayer = true },
                onOpenSettings = { showSettings = true },
                onOpenUplink = { showUplink = true },
                onOpenSwap = { showSwap = true },
                onSharePlaylist = {
                    sharePlaylistId = it
                    showShare = true
                },
                onOpenVideo = openVideo,
                onModernHome = { vm.setModernHome(true) },
            )
        } else {
            PermissionScreen(
                onGrant = { launcher.launch(requested) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }

        Overlay(showSearch && granted) {
            SearchScreen(
                vm = vm,
                animate = animate && !(showGroup || playerUp || toolUp || showVideo),
                onClose = { showSearch = false },
                onOpenGroup = openGroup,
                onOpenVideo = openVideo,
            )
        }

        Overlay(showGroup && homeGroup != null && granted) {
            homeGroup?.let { GroupPage(vm = vm, group = it, animate = animate && !(playerUp || toolUp || showVideo), onClose = { showGroup = false }) }
        }

        AnimatedVisibility(
            visible = showPlayer && state.current != null,
            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)),
        ) {
            NowPlayingScreen(vm = vm, animate = animate && !(toolUp || showVideo), onClose = { showPlayer = false }, onOpenVideo = { showVideo = true })
        }

        Overlay(showUplink && granted) {
            UplinkScreen(
                vm = vm,
                animate = animate && !(showSwap || showSettings || showCredits || showVideo),
                onClose = { engineGate = EngineGate.COOL },
                onOpenCredits = { showCredits = true },
                onOpenSwapList = { text ->
                    SwapReceiver.startLinks(context, text, vm.songs.value)
                    leaveUplinkQuietly()
                    showSwap = true
                },
                onStreamStarted = { isVideo ->
                    leaveUplinkQuietly()
                    if (isVideo) showVideo = true else showPlayer = true
                },
            )
        }

        val gate = engineGate
        AnimatedVisibility(visible = gate != null, enter = fadeIn(), exit = fadeOut()) {
            EngineGateScreen(gate ?: shownGate, onDone = {
                if ((gate ?: shownGate) == EngineGate.COOL) showUplink = false
                engineGate = null
            })
        }

        Overlay(showSwap && granted) {
            SwapReceiveScreen(
                vm = vm,
                animate = animate && !(showShare || showSettings || showCredits || showVideo),
                onClose = { showSwap = false },
                onOpenUplink = {
                    showSwap = false
                    showUplink = true
                },
            )
        }

        Overlay(showShare && sharePlaylistId != null && granted) {
            sharePlaylistId?.let {
                SwapShareScreen(vm = vm, playlistId = it, animate = animate && !(showSettings || showCredits || showVideo), onClose = { showShare = false })
            }
        }

        Overlay(showSettings) {
            SettingsScreen(
                vm = vm,
                animate = animate && !(showEq || showCredits || showYtSignIn || showStats || showVideo),
                onClose = { showSettings = false },
                onOpenEqualizer = { showEq = true },
                onOpenCredits = { showCredits = true },
                onUpdateFound = { update = it },
                onOpenYtSignIn = { showYtSignIn = true },
                onOpenStats = { showStats = true },
            )
        }

        Overlay(showYtSignIn) {
            YouTubeSignInScreen(vm = vm, onClose = { showYtSignIn = false })
        }

        Overlay(showEq) {
            EqualizerScreen(animate = animate && !showVideo, onClose = { showEq = false })
        }

        Overlay(showStats) {
            StatsScreen(vm = vm, animate = animate && !showVideo, onClose = { showStats = false })
        }

        Overlay(showCredits) {
            CreditsScreen(
                animate = animate && !showVideo,
                onClose = { showCredits = false },
                onUpdateFound = { update = it },
            )
        }

        // Fullscreen video sits above everything so picture-in-picture shows just the video.
        AnimatedVisibility(
            visible = showVideo && state.current?.isVideo == true && !audioOnly,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            VideoPlayerScreen(vm = vm, inPip = inPip, onClose = { showVideo = false }, onEnterPip = onEnterPip)
        }

        if (!inPip) {
            // "Removed … UNDO" bar, above the little notes.
            val undo by vm.undo.collectAsStateWithLifecycle()
            LaunchedEffect(undo?.id) {
                val id = undo?.id ?: return@LaunchedEffect
                delay(5_000)
                vm.dismissUndo(id)
            }
            AnimatedVisibility(
                visible = undo != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (message != null) 156.dp else 96.dp, start = 24.dp, end = 24.dp),
            ) {
                Row(
                    Modifier
                        .neonPanel(MaterialTheme.colorScheme.primary, glow = 12.dp, fill = PanelSolid)
                        .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(undo?.label ?: "", color = TextHi, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.runUndo() }) {
                        Text("UNDO", color = MaterialTheme.colorScheme.primary, fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
            }
            // Little notes at the bottom of the screen.
            AnimatedVisibility(
                visible = message != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(
                    message ?: "",
                    color = TextHi,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .neonPanel(MaterialTheme.colorScheme.secondary, glow = 12.dp, fill = PanelSolid)
                        .clickable { vm.consumeMessage() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_200)
            vm.consumeMessage()
        }
    }

    whatsNew?.let { notes ->
        InfoDialog(
            title = "WHAT'S NEW IN v$thisVersion",
            body = notes,
            confirm = "NICE",
            onConfirm = { whatsNew = null },
            onDismiss = { whatsNew = null },
        )
    }

    update?.let { info ->
        InfoDialog(
            title = "UPDATE AVAILABLE",
            body = "Stellar Disc v${info.version} is out. You have v${com.stellapp.disc.BuildConfig.VERSION_NAME}.\n\nDownload it, then open the file to install over this version.",
            confirm = "DOWNLOAD",
            onConfirm = {
                openUrl(context, info.downloadUrl)
                update = null
            },
            onDismiss = { update = null },
        )
    }
    crash?.let { report ->
        InfoDialog(
            title = "CRASH DETECTED",
            body = "Stellar Disc crashed last time. Sending the report helps get it fixed.\n\n" + report.lines().take(8).joinToString("\n"),
            confirm = "REPORT ON GITHUB",
            onConfirm = {
                openUrl(context, CrashLog.issueUrl(report))
                CrashLog.clear(context)
                crash = null
            },
            onDismiss = {
                CrashLog.clear(context)
                crash = null
            },
        )
    }
}

/** A full-screen overlay that slides in from the right. */
@Composable
private fun Overlay(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        // A short, eased glide rather than a full-width slide feels smoother and quicker.
        enter = slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260)),
        exit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)),
    ) {
        content()
    }
}

@Composable
fun InfoDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .neonPanel(primary, glow = 20.dp, fill = PanelSolid)
                .padding(20.dp)
        ) {
            Text(title, style = neonTitleStyle(18))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(body, color = TextHi, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("LATER", color = TextLo, fontFamily = Mono, letterSpacing = 2.sp) }
                TextButton(onClick = onConfirm) {
                    Text(confirm, color = primary, fontFamily = Mono, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun neonTitleStyle(size: Int = 26): TextStyle {
    val primary = MaterialTheme.colorScheme.primary
    return TextStyle(
        color = primary,
        fontFamily = Mono,
        fontWeight = FontWeight.Black,
        fontSize = size.sp,
        letterSpacing = 3.sp,
        shadow = Shadow(color = primary, blurRadius = 18f),
    )
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("STELLAR//DISC", style = neonTitleStyle())
        Spacer(Modifier.height(24.dp))
        Text(
            "Uplink needs access to your music files to scan for tracks.",
            color = TextHi,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "GRANT ACCESS",
            modifier = Modifier
                .neonPanel(primary, glow = 16.dp)
                .clickable(onClick = onGrant)
                .padding(horizontal = 28.dp, vertical = 14.dp),
            color = primary,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Open app settings", color = TextLo)
        }
    }
}

/** Rebuilds a playlist, artist or album page from its key after the app was restarted. */
private fun restoreGroup(
    key: String,
    songs: List<com.stellapp.disc.data.Song>,
    playlists: List<com.stellapp.disc.data.Playlist>,
    favourites: List<Long>,
): SongGroup? {
    val byId = songs.associateBy { it.id }
    return when {
        key == "smart:favourites" -> {
            val list = favourites.mapNotNull { byId[it] }
            if (list.isEmpty()) null else SongGroup(key, "♥ Favourites", "${list.size} tracks", list, list.first(), GroupKind.SMART)
        }
        key.startsWith("playlist:") -> {
            val p = playlists.firstOrNull { it.id == key.removePrefix("playlist:") } ?: return null
            val list = p.songIds.mapNotNull { byId[it] }
            SongGroup(key, p.name, "${list.size} tracks", list, list.firstOrNull(), GroupKind.PLAYLIST, p.id)
        }
        key.startsWith("artist:") -> {
            val name = key.removePrefix("artist:")
            val list = songs.filter { !it.isVideo && it.artist == name }.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
            if (list.isEmpty()) null else SongGroup(key, name, "artist", list, list.first(), GroupKind.ARTIST)
        }
        key.startsWith("album:") -> {
            val id = key.removePrefix("album:").toLongOrNull() ?: return null
            val list = songs.filter { it.albumId == id }.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
            if (list.isEmpty()) null else SongGroup(key, list.first().album, list.first().artist, list, list.first(), GroupKind.ALBUM)
        }
        else -> null // generated mixes change over time, so they aren't restored
    }
}
