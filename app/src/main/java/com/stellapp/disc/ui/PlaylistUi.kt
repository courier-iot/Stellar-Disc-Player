package com.stellapp.disc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.Playlist
import com.stellapp.disc.data.Song
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo

val DangerRed = Color(0xFFFF4D6D)

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String = "SAVE",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val primary = MaterialTheme.colorScheme.primary
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .neonPanel(primary, glow = 20.dp, fill = PanelSolid)
                .padding(20.dp)
        ) {
            Text(title, style = neonTitleStyle(18))
            Spacer(Modifier.height(16.dp))
            NeonTextField(text, { text = it }, placeholder = "Playlist name")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = TextLo, fontFamily = Mono, letterSpacing = 2.sp)
                }
                TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                    Text(confirmLabel, color = primary, fontFamily = Mono, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .neonPanel(DangerRed, glow = 20.dp, fill = PanelSolid)
                .padding(20.dp)
        ) {
            Text(title, color = DangerRed, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(12.dp))
            Text(body, color = TextHi)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = TextLo, fontFamily = Mono, letterSpacing = 2.sp)
                }
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = DangerRed, fontFamily = Mono, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Long-press menu for a track: queue it, or add/remove it from playlists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionSheet(
    song: Song,
    playlists: List<Playlist>,
    inPlaylistId: String?,
    vm: PlayerViewModel,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onHideFolder: (() -> Unit)? = null,
) {
    var creating by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelSolid, contentColor = TextHi) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(song, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = TextHi, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = TextLo, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SheetAction("Play next") { vm.playNext(song); onDismiss() }
            SheetAction("Add to queue") { vm.enqueueSongs(listOf(song.id)); onDismiss() }
            if (inPlaylistId != null) {
                SheetAction("Remove from this playlist", color = DangerRed) {
                    vm.removeFromPlaylist(inPlaylistId, song.id)
                    onDismiss()
                }
            }
            if (onEdit != null) SheetAction("Edit info & cover") { onEdit() }
            if (onHideFolder != null && song.folder.isNotBlank()) {
                SheetAction("Hide folder \"${song.folder.substringAfterLast('/')}\"") { onHideFolder() }
            }
            if (onDelete != null) SheetAction("Delete from phone", color = DangerRed) { onDelete() }
            SectionLabel("ADD TO PLAYLIST", Modifier.padding(horizontal = 20.dp))
            SheetAction("+ New playlist…", color = primary) { creating = true }
            playlists.filter { it.id != inPlaylistId }.forEach { p ->
                val already = song.id in p.songIds
                SheetAction(p.name, trailing = if (already) "ADDED" else "${p.songIds.size}") {
                    if (!already) vm.addToPlaylist(p.id, listOf(song.id))
                    onDismiss()
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "NEW PLAYLIST",
            initial = "",
            confirmLabel = "CREATE",
            onDismiss = { creating = false },
        ) { name ->
            val id = vm.createPlaylist(name)
            vm.addToPlaylist(id, listOf(song.id))
            creating = false
            onDismiss()
        }
    }
}


/** The tag editor. Changes are kept inside Stellar Disc; the files themselves aren't rewritten. */
@Composable
fun EditInfoDialog(song: Song, vm: PlayerViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    val primary = MaterialTheme.colorScheme.primary
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setCover(song, it) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .neonPanel(primary, glow = 20.dp, fill = PanelSolid)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("EDIT INFO", style = neonTitleStyle(18))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(song, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    NeonChip("CHANGE COVER", false) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Text(
                        if (song.isVideo) "For this video" else "For the whole album",
                        color = TextLo,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            SectionLabel("TITLE")
            NeonTextField(title, { title = it }, placeholder = "Title")
            SectionLabel("ARTIST")
            NeonTextField(artist, { artist = it }, placeholder = "Artist")
            SectionLabel("ALBUM")
            NeonTextField(album, { album = it }, placeholder = "Album")
            Text(
                "Saved in Stellar Disc only. The file itself isn't changed, so other apps still show the original tags.",
                color = TextLo,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    vm.resetMeta(song)
                    onDismiss()
                }) { Text("RESET", color = DangerRed, fontFamily = Mono, letterSpacing = 2.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("CANCEL", color = TextLo, fontFamily = Mono, letterSpacing = 2.sp) }
                TextButton(onClick = {
                    vm.setMeta(song, title, artist, album)
                    onDismiss()
                }) { Text("SAVE", color = primary, fontFamily = Mono, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
