package com.stellapp.disc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.Song
import com.stellapp.disc.lyrics.Lyrics
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object None : LyricsLoad
    data class Ready(val lyrics: Lyrics) : LyricsLoad
}

/** Lyrics in Now Playing. Synced lyrics scroll along and you can tap a line to jump there. */
@Composable
fun LyricsPanel(vm: PlayerViewModel, song: Song, positionMs: Long, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val version by vm.lyricsVersion.collectAsStateWithLifecycle()
    val auto by vm.lyricsAuto.collectAsStateWithLifecycle()
    var pasting by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importLyrics(song, it) }
    }

    val load by produceState<LyricsLoad>(LyricsLoad.Loading, song.id, version, auto) {
        value = LyricsLoad.Loading
        value = vm.loadLyrics(song, allowOnline = auto)?.let { LyricsLoad.Ready(it) } ?: LyricsLoad.None
    }

    Column(modifier.neonPanel(primary.copy(alpha = 0.5f), glow = 0.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val l = load) {
                LyricsLoad.Loading -> CircularProgressIndicator(color = primary, modifier = Modifier.align(Alignment.Center))
                LyricsLoad.None -> Text(
                    "No lyrics for this track yet.\nFind them online, search by hand, paste them, or import an .lrc file.",
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp),
                )
                is LyricsLoad.Ready -> {
                    val synced = l.lyrics.synced
                    if (synced != null) SyncedLyrics(synced, positionMs) { vm.seekTo(it) }
                    else Text(
                        l.lyrics.plain ?: "",
                        color = TextHi,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (load as? LyricsLoad.Ready)?.let {
                Text("from ${it.lyrics.source}", color = TextLo, fontFamily = Mono, fontSize = 10.sp)
            }
            NeonChip("FIND ONLINE", false) { vm.findLyricsOnline(song) }
            NeonChip("⌕ SEARCH", false) { searching = true }
            NeonChip("PASTE", false) { pasting = true }
            NeonChip("IMPORT .LRC", false) { importer.launch(arrayOf("*/*")) }
            if (load is LyricsLoad.Ready) NeonChip("REMOVE", false) { vm.removeLyrics(song) }
        }
    }

    if (searching) {
        LyricsSearchDialog(vm, song, onDismiss = { searching = false }) { hit ->
            vm.saveLyrics(song, hit.text)
            searching = false
        }
    }

    if (pasting) {
        TextEntryDialog(
            title = "PASTE LYRICS",
            hint = "Plain text, or LRC with [mm:ss.xx] timestamps",
            onDismiss = { pasting = false },
        ) { text ->
            vm.saveLyrics(song, text)
            pasting = false
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<com.stellapp.disc.lyrics.LyricLine>, positionMs: Long, onSeek: (Long) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val current = lines.indexOfLast { it.timeMs <= positionMs + 300 }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 16.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            Text(
                line.text.ifBlank { "♪" },
                color = when {
                    active -> primary
                    i < current -> TextLo.copy(alpha = 0.6f)
                    else -> TextHi.copy(alpha = 0.8f)
                },
                fontSize = if (active) 20.sp else 16.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/** A multi-line text box in a dialog. */
@Composable
fun TextEntryDialog(title: String, hint: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .heightIn(min = 120.dp, max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                NeonTextField(text, { text = it }, placeholder = hint, singleLine = false)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("CANCEL", color = TextLo, fontFamily = Mono, letterSpacing = 2.sp) }
                TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                    Text("SAVE", color = primary, fontFamily = Mono, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Manual match: fix the title or artist, search, and pick the right result yourself.
 * Synced results come first; each shows its length so you can match it to your copy.
 */
@Composable
private fun LyricsSearchDialog(
    vm: PlayerViewModel,
    song: Song,
    onDismiss: () -> Unit,
    onPick: (com.stellapp.disc.lyrics.LyricsHit) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(if (song.artist.equals("Unknown artist", true)) "" else song.artist) }
    var query by remember { mutableStateOf<Pair<String, String>?>(title to artist) }
    var busy by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<com.stellapp.disc.lyrics.LyricsHit>?>(null) }
    LaunchedEffect(query) {
        val q = query ?: return@LaunchedEffect
        if (q.first.isBlank()) return@LaunchedEffect
        busy = true
        hits = vm.searchLyrics(q.first, q.second)
        busy = false
    }
    val songSec = (song.durationMs / 1000).toInt()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .neonPanel(primary, glow = 20.dp, fill = PanelSolid)
                .padding(18.dp)
        ) {
            Text("MANUAL MATCH", style = neonTitleStyle(18))
            Text("Fix the words if the tags are off, then pick the right one.", color = TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            NeonTextField(title, { title = it }, placeholder = "Song title")
            Spacer(Modifier.height(8.dp))
            NeonTextField(artist, { artist = it }, placeholder = "Artist (optional)")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonChip(if (busy) "SEARCHING…" else "⌕ SEARCH", false) { if (!busy) query = title.trim() to artist.trim() }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("CLOSE", color = TextLo, fontFamily = Mono, letterSpacing = 2.sp) }
            }
            Spacer(Modifier.height(6.dp))
            val list = hits
            when {
                busy -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primary)
                }
                list == null -> Unit
                list.isEmpty() -> Text(
                    "Nothing found. Try fewer words, or just the title.",
                    color = TextLo,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(list) { _, hit ->
                        val diff = if (hit.durationSec > 0 && songSec > 0) kotlin.math.abs(hit.durationSec - songSec) else -1
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(hit) }
                                .padding(vertical = 9.dp)
                        ) {
                            Text(hit.title, color = TextHi, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                listOf(hit.artist, hit.album).filter { it.isNotBlank() }.joinToString(" · "),
                                color = TextLo,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (hit.synced) "◉ SYNCED" else "○ PLAIN",
                                    color = if (hit.synced) primary else TextLo,
                                    fontFamily = Mono,
                                    fontSize = 10.sp,
                                )
                                if (hit.durationSec > 0) {
                                    Text(
                                        "%d:%02d".format(hit.durationSec / 60, hit.durationSec % 60) +
                                            if (diff in 0..3) " ✓ LENGTH MATCHES" else "",
                                        color = if (diff in 0..3) primary else TextLo,
                                        fontFamily = Mono,
                                        fontSize = 10.sp,
                                    )
                                }
                                Text(hit.source, color = TextLo, fontFamily = Mono, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
