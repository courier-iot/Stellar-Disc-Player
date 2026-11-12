package com.stellapp.disc.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.swap.ItemState
import com.stellapp.disc.swap.MatchKind
import com.stellapp.disc.swap.Pairing
import com.stellapp.disc.swap.SwapItem
import com.stellapp.disc.swap.SwapPhase
import com.stellapp.disc.swap.SwapReceiver
import com.stellapp.disc.swap.SwapSession
import com.stellapp.disc.swap.SwapSource
import com.stellapp.disc.ui.theme.accentGradient
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void
import com.stellapp.disc.uplink.DlFormat

@Composable
fun SwapReceiveScreen(
    vm: PlayerViewModel,
    animate: Boolean,
    onClose: () -> Unit,
    onOpenUplink: () -> Unit,
    onSharePlaylist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val session by SwapReceiver.session.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    var pasted by rememberSaveable { mutableStateOf("") }

    fun handleCode(text: String) {
        if (text.trim().startsWith(Pairing.PREFIX)) SwapReceiver.startNearby(context, text, songs)
        else SwapReceiver.startLinks(context, text, songs)
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleCode(it) }
    }
    fun scan() {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan a Stellar Disc share code")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    val leave = {
        // A running transfer keeps going in the background; anything else is cleared.
        SwapReceiver.close()
        onClose()
    }
    BackHandler(onBack = leave)

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield(Modifier.fillMaxSize(), animate = animate, speed = 0.4f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = leave) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text("SWAP", style = neonTitleStyle(22))
                    Text(
                        session?.name?.let { "receiving · $it" } ?: "send or receive a playlist",
                        color = TextLo,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val s = session
            if (s == null) {
                Intro(
                    pasted = pasted,
                    onPaste = { pasted = it },
                    onScan = { scan() },
                    onCheckLinks = { handleCode(pasted) },
                    playlists = playlists,
                    onShare = onSharePlaylist,
                )
            } else when (s.phase) {
                SwapPhase.CONNECTING, SwapPhase.CHECKING -> Busy(s.message ?: "Working…")
                SwapPhase.ERROR -> ErrorState(s.message ?: "Something went wrong.") {
                    SwapReceiver.close()
                }
                else -> Review(
                    s = s,
                    uplinkLabel = remember { vm.loadUplinkPrefs() }.let { p ->
                        p.format.label + if (p.format.bitrate) " ${p.audioKbps}K" else if (p.format == DlFormat.MP4 || p.format == DlFormat.MKV) " ≤${p.maxHeight}p" else ""
                    },
                    onGo = { SwapReceiver.receive(context, vm.loadUplinkPrefs()) },
                    onDone = leave,
                    onOpenUplink = {
                        SwapReceiver.close()
                        onOpenUplink()
                    },
                    accent = primary,
                )
            }
        }
    }
}

@Composable
private fun Intro(
    pasted: String,
    onPaste: (String) -> Unit,
    onScan: () -> Unit,
    onCheckLinks: () -> Unit,
    playlists: List<com.stellapp.disc.data.Playlist>,
    onShare: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .neonPanel(primary, glow = 20.dp, fill = Color.Transparent)
                .background(Brush.horizontalGradient(accentGradient()))
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Text("SCAN QR CODE", color = Void, fontFamily = Mono, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Ask your friend to open a playlist and tap the share icon. Nearby needs you both on the same Wi-Fi, or you can join their phone's hotspot. Link codes work anywhere.",
            color = TextLo,
            fontSize = 13.sp,
        )

        SectionLabel("SEND ONE OF YOURS")
        if (playlists.isEmpty()) {
            Text(
                "Make a playlist first: long-press any song and pick New playlist. Then it shows up here with a QR code to share.",
                color = TextLo,
                fontSize = 13.sp,
            )
        } else {
            Text("Pick a playlist to show its QR code (same Wi-Fi) or share it as links (anywhere).", color = TextLo, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            playlists.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .neonPanel(primary.copy(alpha = 0.45f), glow = 0.dp)
                        .clickable { onShare(p.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = TextHi, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.songIds.size} tracks", color = TextLo, fontSize = 12.sp)
                    }
                    Text("▦ QR / SHARE", color = primary, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }

        SectionLabel("OR PASTE A SHARED LINK LIST")
        NeonTextField(pasted, onPaste, placeholder = "Stellar Disc playlist: …", singleLine = false)
        Spacer(Modifier.height(10.dp))
        NeonChip("CHECK LINKS", false) { if (pasted.isNotBlank()) onCheckLinks() }

        SectionLabel("HOW THE SONG CHECKER WORKS")
        Legend("HAVE", primary, "You already have this exact file or link. Skipped automatically.")
        Legend("MAYBE", secondary, "Same song, different file. Your copy is used unless you tick it.")
        Legend("NEW", TextHi, "Not in your library. Ticked by default.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Legend(tag: String, color: Color, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(tag, color)
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextLo, fontSize = 13.sp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.dp, color, SmallCut)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Busy(message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = TextLo, fontFamily = Mono, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NO SIGNAL", color = DangerRed, fontFamily = Mono, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, color = TextHi, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        NeonChip("TRY AGAIN", false, onRetry)
    }
}

@Composable
private fun Review(
    s: SwapSession,
    uplinkLabel: String,
    onGo: () -> Unit,
    onDone: () -> Unit,
    onOpenUplink: () -> Unit,
    accent: Color,
) {
    val secondary = MaterialTheme.colorScheme.secondary
    val have = s.items.count { it.match == MatchKind.HAVE }
    val maybe = s.items.count { it.match == MatchKind.PROBABLY }
    val new = s.items.count { it.match == MatchKind.NEW }
    val reviewing = s.phase == SwapPhase.REVIEW

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("$new NEW", TextHi)
                Tag("$maybe MAYBE", secondary)
                Tag("$have HAVE", accent)
            }
            if (reviewing) {
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "Save as a playlist",
                    subtitle = "Uses your copies for songs you already have",
                    checked = s.makePlaylist,
                    onChange = { SwapReceiver.setMakePlaylist(it) },
                )
                if (s.makePlaylist) {
                    NeonTextField(s.playlistName, { SwapReceiver.setPlaylistName(it) }, placeholder = "Playlist name")
                }
                if (s.source == SwapSource.LINKS) {
                    Text(
                        "New tracks download through Uplink as $uplinkLabel. Change this in Uplink.",
                        color = TextLo,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("SELECT NEW", false) { SwapReceiver.selectAllNew() }
                    NeonChip("NONE", false) { SwapReceiver.selectNone() }
                }
            } else {
                s.message?.let {
                    Text(it, color = if (s.phase == SwapPhase.DONE) accent else TextLo, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(s.items, key = { _, it -> it.track.index }) { i, item ->
                ItemRow(item, reviewing, accent) { SwapReceiver.toggle(i) }
            }
        }

        // Bottom action
        Box(Modifier.padding(16.dp)) {
            when (s.phase) {
                SwapPhase.REVIEW -> {
                    val n = s.selectedCount
                    val label = when {
                        n == 0 && s.makePlaylist -> "SAVE PLAYLIST"
                        n == 0 -> "NOTHING SELECTED"
                        s.source == SwapSource.NEARBY -> "RECEIVE $n TRACK(S) ▶"
                        else -> "DOWNLOAD $n WITH UPLINK ▶"
                    }
                    BigButton(label, enabled = n > 0 || s.makePlaylist, accent = accent, onClick = onGo)
                }
                SwapPhase.TRANSFERRING -> BigButton("STOP", enabled = true, accent = DangerRed, filled = false) { SwapReceiver.cancel() }
                else -> {
                    if (s.source == SwapSource.LINKS) BigButton("OPEN UPLINK ▶", enabled = true, accent = accent, onClick = onOpenUplink)
                    else BigButton("DONE", enabled = true, accent = accent, onClick = onDone)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: SwapItem, reviewing: Boolean, accent: Color, onToggle: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = reviewing && item.match != MatchKind.HAVE, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                item.match == MatchKind.HAVE -> Text("✓", color = accent, fontWeight = FontWeight.Bold)
                reviewing -> Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = TextLo, checkmarkColor = Void),
                )
                else -> Text(
                    when (item.state) {
                        ItemState.SAVED -> "✓"
                        ItemState.FAILED -> "✕"
                        ItemState.SKIPPED -> "–"
                        ItemState.QUEUED -> "⇣"
                        ItemState.RECEIVING -> "${(item.progress * 100).toInt()}%"
                        ItemState.WAITING -> "…"
                    },
                    color = when (item.state) {
                        ItemState.SAVED, ItemState.QUEUED -> accent
                        ItemState.FAILED -> DangerRed
                        ItemState.RECEIVING -> secondary
                        else -> TextLo
                    },
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.track.title, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when (item.match) {
                MatchKind.HAVE -> "already in your library"
                MatchKind.PROBABLY -> "you have: " + (item.local?.let { "${it.title} — ${it.artist}" } ?: "a similar track")
                MatchKind.NEW -> item.track.artist.ifBlank { "new" }
            }
            Text(
                detail,
                color = when (item.match) {
                    MatchKind.HAVE -> accent
                    MatchKind.PROBABLY -> secondary
                    MatchKind.NEW -> TextLo
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.state == ItemState.RECEIVING) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progress)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(secondary, accent)))
                    )
                }
            }
        }
        if (item.track.size > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "%.1f MB".format(item.track.size / 1_048_576f),
                color = TextLo,
                fontFamily = Mono,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BigButton(label: String, enabled: Boolean, accent: Color, filled: Boolean = true, onClick: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (!enabled) Modifier.neonPanel(TextLo.copy(alpha = 0.4f), glow = 0.dp)
                else if (filled) Modifier
                    .neonPanel(accent, glow = 18.dp, fill = Color.Transparent)
                    .background(Brush.horizontalGradient(listOf(accent, secondary)))
                else Modifier.neonPanel(accent, glow = 10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled && filled) Void else if (enabled) accent else TextLo,
            fontFamily = Mono,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
    }
}
