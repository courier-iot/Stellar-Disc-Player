package com.stellapp.disc.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.Song
import com.stellapp.disc.swap.ShareText
import com.stellapp.disc.swap.SwapServer
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo

private const val MAX_QR_TEXT = 900

@Composable
fun SwapShareScreen(vm: PlayerViewModel, playlistId: String, animate: Boolean, onClose: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val byId = remember(songs) { songs.associateBy { it.id } }
    val tracks = remember(playlist, byId) { playlist?.songIds?.mapNotNull { byId[it] } ?: emptyList() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val m3u = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        uri?.let { vm.exportM3u(playlistId, it) }
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
            Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text("SHARE", style = neonTitleStyle(22))
                    Text(
                        "${playlist?.name ?: "Playlist"} · ${tracks.size} tracks",
                        color = TextLo,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NeonChip("NEARBY · WI-FI", tab == 0) { tab = 0 }
                NeonChip("LINKS", tab == 1) { tab = 1 }
                NeonChip("⇩ .M3U", false) { m3u.launch((playlist?.name ?: "playlist") + ".m3u") }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    playlist == null || tracks.isEmpty() -> Text(
                        "This playlist is empty, so there's nothing to share yet.",
                        color = TextLo,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                    tab == 0 -> NearbyShare(playlist.name, tracks)
                    else -> LinksShare(playlist.name, tracks)
                }
                Text(
                    "Only share music you have the rights to share.",
                    color = TextLo,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
internal fun NearbyShare(name: String, tracks: List<Song>, move: com.stellapp.disc.swap.MoveExtras? = null) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var attempt by remember { mutableIntStateOf(0) }
    val server = remember(tracks, attempt) { SwapServer(context.applicationContext, name, tracks, move) }
    var code by remember(server) { mutableStateOf<String?>(null) }
    var error by remember(server) { mutableStateOf<String?>(null) }
    val sent by server.sent.collectAsStateWithLifecycle()
    val peers by server.peers.collectAsStateWithLifecycle()
    val expired by server.expired.collectAsStateWithLifecycle()

    LaunchedEffect(server) {
        code = server.start()
        if (code == null) error = server.lastError ?: "Couldn't start sharing."
    }
    DisposableEffect(server) { onDispose { server.stop() } }

    // Keep the screen on so the transfer isn't cut off.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Spacer(Modifier.height(12.dp))
    val c = code
    when {
        expired -> {
            Text(
                "Sharing switched itself off after 10 minutes with no activity, so nobody else on this Wi-Fi can connect.",
                color = TextLo,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            NeonChip("SHARE AGAIN", false) { attempt++ }
        }
        error != null -> {
            Text(error ?: "", color = DangerRed, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            NeonChip("TRY AGAIN", false) { attempt++ }
        }
        c == null -> {
            CircularProgressIndicator(color = primary)
            Spacer(Modifier.height(12.dp))
            Text("Preparing ${tracks.size} tracks…", color = TextLo, fontFamily = Mono, fontSize = 12.sp)
        }
        else -> {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .neonPanel(primary, glow = 22.dp)
                    .padding(10.dp)
            ) {
                QrCode(c, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (move != null) "On the new phone: Stellar Disc → Settings → Receive from your old phone (or Swap → Scan QR code)"
                else "On your friend's phone: Stellar Disc → swap icon → Scan QR code",
                color = TextHi,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (move != null) "Both phones need the same Wi-Fi, or the new phone can join this phone's hotspot. Keep this screen open and plugged in until the move finishes."
                else "Both phones need the same Wi-Fi, or your friend can join this phone's hotspot. Keep this screen open until they're done.",
                color = TextLo,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    peers.isEmpty() -> "WAITING FOR A CONNECTION…"
                    else -> "${peers.size} PHONE(S) CONNECTED · $sent FILE(S) SENT"
                },
                color = if (peers.isEmpty()) TextLo else secondary,
                fontFamily = Mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
private fun LinksShare(name: String, tracks: List<Song>) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val linked = remember(tracks) {
        LinkStore.init(context)
        tracks.mapNotNull { s -> LinkStore.get(s.id)?.let { s to it } }
    }
    val text = remember(linked, name) { ShareText.build(name, linked) }

    Spacer(Modifier.height(12.dp))
    Text(
        "${linked.size} of ${tracks.size} tracks have a source link.",
        color = TextHi,
        textAlign = TextAlign.Center,
    )
    if (linked.size < tracks.size) {
        Text(
            "Only tracks downloaded with Uplink (v1.2.0 or newer) have links. Use Nearby for the rest.",
            color = TextLo,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (linked.isEmpty()) return

    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .neonPanel(primary, glow = 16.dp)
            .clickable {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Stellar Disc playlist: $name")
                    .putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share playlist links").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("SHARE AS TEXT ▶", color = primary, fontFamily = Mono, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    }
    Text(
        "Works over any distance. Your friend shares the text to Stellar Disc, or pastes it into Swap.",
        color = TextLo,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )

    Spacer(Modifier.height(20.dp))
    if (text.length <= MAX_QR_TEXT) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .neonPanel(primary, glow = 18.dp)
                .padding(10.dp)
        ) {
            QrCode(text, Modifier.fillMaxSize())
        }
        Text(
            "Or scan this in person. No shared Wi-Fi needed.",
            color = TextLo,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        Text(
            "Too many links to fit in one QR code. Use Share as text instead.",
            color = TextLo,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
