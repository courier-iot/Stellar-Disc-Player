package com.stellapp.disc.ui

import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.swap.FitPriority
import com.stellapp.disc.swap.MatchKind
import com.stellapp.disc.swap.SwapItem
import com.stellapp.disc.swap.SwapReceiver
import com.stellapp.disc.swap.SwapSession
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void

/** Space kept free on the new phone so it isn't left completely full. */
private const val RESERVE_BYTES = 1L shl 30

fun formatBytes(b: Long): String {
    val v = b.coerceAtLeast(0L).toDouble()
    return when {
        v >= 1e9 -> "%.1f GB".format(v / 1e9)
        v >= 1e6 -> "%.0f MB".format(v / 1e6)
        else -> "%.0f KB".format(v / 1e3)
    }
}

/** Rough time on ordinary home Wi-Fi (about 10–20 MB/s for phone to phone). */
private fun timeEstimate(bytes: Long): String {
    if (bytes <= 0) return "a moment"
    val slow = bytes / 10_000_000L / 60
    val fast = bytes / 20_000_000L / 60
    return when {
        slow < 1 -> "under a minute"
        fast < 1 -> "about 1–$slow min"
        else -> "about $fast–$slow min"
    }
}

private data class PhoneSpace(val total: Long, val free: Long)

private fun phoneSpace(): PhoneSpace = try {
    val st = StatFs(Environment.getExternalStorageDirectory().path)
    PhoneSpace(st.totalBytes, st.availableBytes)
} catch (e: Exception) {
    PhoneSpace(0L, 0L)
}

// ======================================================================
// Old phone: send everything
// ======================================================================

@Composable
fun MoveSendScreen(vm: PlayerViewModel, animate: Boolean, onClose: () -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val extras = remember { vm.moveExtras() }
    val music = songs.count { !it.isVideo && !it.isStream }
    val videos = songs.count { it.isVideo }
    val tracks = remember(songs) { songs.filter { !it.isStream } }
    val bytes = remember(tracks) { tracks.sumOf { it.size.coerceAtLeast(0L) } }
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
                    Text("MOVE TO A NEW PHONE", style = neonTitleStyle(20))
                    Text(
                        "$music songs · $videos videos · ${formatBytes(bytes)}",
                        color = TextLo,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (tracks.isEmpty()) {
                    EmptyState("◎", "NOTHING TO MOVE", "This phone has no music or videos in Stellar Disc yet.")
                } else {
                    NearbyShare("Move from ${android.os.Build.MODEL}", tracks, extras)
                    Text(
                        "The new phone picks what to bring and checks it has room before anything is sent. Playlists, favourites, play counts and settings come too.",
                        color = TextLo,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }
    }
}

// ======================================================================
// New phone: choose what to bring
// ======================================================================

private enum class GroupBy(val label: String) { ALBUMS("ALBUMS"), ARTISTS("ARTISTS"), PLAYLISTS("PLAYLISTS") }

private data class PickGroup(val key: String, val name: String, val indices: List<Int>)

private fun buildGroups(s: SwapSession, by: GroupBy): List<PickGroup> {
    val items = s.items
    return when (by) {
        GroupBy.ALBUMS -> items.indices.groupBy { i ->
            val t = items[i].track
            if (t.isVideo) "▶ Videos" else t.album.ifBlank { "Unknown album" }
        }.map { (k, v) -> PickGroup("al:$k", k, v) }.sortedBy { it.name.lowercase() }
        GroupBy.ARTISTS -> items.indices.groupBy { i ->
            val t = items[i].track
            if (t.isVideo) "▶ Videos" else t.artist.ifBlank { "Unknown artist" }
        }.map { (k, v) -> PickGroup("ar:$k", k, v) }.sortedBy { it.name.lowercase() }
        GroupBy.PLAYLISTS -> {
            val inList = HashSet<Int>()
            val lists = s.groups.mapIndexed { n, g ->
                inList += g.indices
                PickGroup("pl:$n", g.name, g.indices.filter { it in items.indices }.distinct())
            }
            val rest = items.indices.filter { it !in inList }
            lists + if (rest.isEmpty()) emptyList() else listOf(PickGroup("pl:rest", "Not in a playlist", rest))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveReview(s: SwapSession, onGo: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val space = remember { phoneSpace() }
    var grouping by rememberSaveable { mutableStateOf(GroupBy.ALBUMS) }
    var priority by rememberSaveable { mutableStateOf(FitPriority.MUSIC) }
    var open by remember { mutableStateOf(setOf<String>()) }
    val groups = remember(s.items.size, s.groups, grouping) { buildGroups(s, grouping) }

    val need = s.selectedBytes
    val room = (space.free - RESERVE_BYTES).coerceAtLeast(0L)
    val fits = space.total == 0L || need <= room
    val newFiles = s.items.count { it.match == MatchKind.NEW }
    val already = s.items.count { it.match == MatchKind.HAVE }
    val picked = s.selectedCount
    val pickedMusic = s.items.count { it.selected && it.match != MatchKind.HAVE && !it.track.isVideo }
    val pickedVideo = picked - pickedMusic

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item(key = "space") {
                StorageBar(space, need, fits)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$pickedMusic songs, $pickedVideo videos · ${formatBytes(need)} · ${timeEstimate(need)} on home Wi-Fi",
                    color = TextHi,
                    fontSize = 13.sp,
                )
                Text(
                    if (already > 0) "$already already on this phone (skipped) · $newFiles new" else "$newFiles files to choose from",
                    color = TextLo,
                    fontSize = 12.sp,
                )
                if (!fits) {
                    Text(
                        "Short by ${formatBytes(need - room)}. Untick some, or use Fit to space.",
                        color = DangerRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item(key = "picks") {
                SectionLabel("QUICK PICK")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("ALL", false) { SwapReceiver.pick { true } }
                    NeonChip("♪ MUSIC", false) { SwapReceiver.pick { !it.isVideo } }
                    NeonChip("▶ VIDEOS", false) { SwapReceiver.pick { it.isVideo } }
                    NeonChip("NONE", false) { SwapReceiver.pick { false } }
                }
                SectionLabel("FIT TO SPACE")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitPriority.entries.forEach { p -> NeonChip(p.label, priority == p) { priority = p } }
                }
                Spacer(Modifier.height(8.dp))
                NeonChip("⇲ FIT ${formatBytes(room)} BY ${priority.label}", false) { SwapReceiver.fitToSpace(room, priority) }
                Text(
                    "Ticks the new phone's free space full in that order (most played first within each), keeping 1 GB spare.",
                    color = TextLo,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "Bring playlists, favourites & settings",
                    subtitle = "Also play counts and lyrics. Tiny, so it's on by default",
                    checked = s.includeBackup,
                    onChange = { SwapReceiver.setIncludeBackup(it) },
                )
                SectionLabel("CHOOSE BY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupBy.entries.forEach { g -> NeonChip(g.label, grouping == g) { grouping = g } }
                }
                Spacer(Modifier.height(8.dp))
            }
            groups.forEach { g ->
                item(key = g.key) {
                    GroupRow(
                        s = s,
                        group = g,
                        expanded = g.key in open,
                        onExpand = { open = if (g.key in open) open - g.key else open + g.key },
                    )
                }
                if (g.key in open) {
                    items(g.indices, key = { i -> g.key + "#" + i }) { i ->
                        FileRow(s.items[i]) { SwapReceiver.setSelected(listOf(i), !s.items[i].selected) }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            val label = when {
                picked == 0 && s.includeBackup -> "BRING PLAYLISTS & SETTINGS ONLY ▶"
                picked == 0 -> "NOTHING SELECTED"
                !fits -> "WON'T FIT · UNTICK ${formatBytes(need - room)}"
                else -> "MOVE $picked FILES · ${formatBytes(need)} ▶"
            }
            BigButton(label, enabled = (picked > 0 && fits) || (picked == 0 && s.includeBackup), accent = primary, onClick = onGo)
        }
    }
}

@Composable
private fun StorageBar(space: PhoneSpace, need: Long, fits: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    if (space.total <= 0L) {
        Text("Couldn't read this phone's storage.", color = TextLo, fontSize = 12.sp)
        return
    }
    val used = (space.total - space.free).coerceAtLeast(0L)
    val usedF = (used.toFloat() / space.total).coerceIn(0f, 1f)
    val needF = (need.toFloat() / space.total).coerceIn(0f, 1f - usedF)
    val freeAfter = space.free - need
    SectionLabel("THIS PHONE'S STORAGE")
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .neonPanel(primary, glow = 0.dp)
            .semantics {
                contentDescription = "Storage: ${formatBytes(used)} used, this move ${formatBytes(need)}, ${formatBytes(freeAfter.coerceAtLeast(0))} free after"
            }
    ) {
        if (usedF > 0f) Box(Modifier.fillMaxHeight().weight(usedF).background(TextLo.copy(alpha = 0.55f)))
        if (needF > 0f) Box(Modifier.fillMaxHeight().weight(needF).background(if (fits) primary else DangerRed))
        val rest = 1f - usedF - needF
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Legend(TextLo.copy(alpha = 0.55f), "Used ${formatBytes(used)}")
        Legend(if (fits) primary else DangerRed, "This move ${formatBytes(need)}")
        Legend(Color.Transparent, "Free after ${formatBytes(freeAfter.coerceAtLeast(0L))}")
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color)
                .border(1.dp, TextLo)
        )
        Spacer(Modifier.width(5.dp))
        Text(text, color = TextLo, fontSize = 11.sp)
    }
}

@Composable
private fun GroupRow(s: SwapSession, group: PickGroup, expanded: Boolean, onExpand: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val choosable = group.indices.filter { s.items[it].match != MatchKind.HAVE }
    val on = choosable.count { s.items[it].selected }
    val state = when {
        choosable.isEmpty() || on == 0 -> ToggleableState.Off
        on == choosable.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val bytes = group.indices.sumOf { if (s.items[it].selected && s.items[it].match != MatchKind.HAVE) s.items[it].track.size.coerceAtLeast(0L) else 0L }
    val have = group.indices.size - choosable.size
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .neonPanel(primary.copy(alpha = 0.4f), glow = 0.dp)
            .clickable(onClick = onExpand)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            onClick = if (choosable.isEmpty()) null else ({ SwapReceiver.setSelected(choosable, state != ToggleableState.On) }),
            colors = CheckboxDefaults.colors(checkedColor = primary, uncheckedColor = TextLo, checkmarkColor = Void),
        )
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(group.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$on of ${choosable.size} ticked · ${formatBytes(bytes)}" + if (have > 0) " · $have already here" else "",
                color = TextLo,
                fontSize = 11.sp,
            )
        }
        Text(if (expanded) "▾" else "▸", color = TextLo, fontSize = 16.sp)
    }
}

@Composable
private fun FileRow(item: SwapItem, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val here = item.match == MatchKind.HAVE
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !here, onClick = onToggle)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = item.selected && !here,
            onCheckedChange = if (here) null else ({ onToggle() }),
            enabled = !here,
            colors = CheckboxDefaults.colors(checkedColor = primary, uncheckedColor = TextLo, checkmarkColor = Void),
        )
        Column(Modifier.weight(1f)) {
            Text(
                (if (item.track.isVideo) "▶ " else "") + item.track.title,
                color = if (here) TextLo else TextHi,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (item.match) {
                    MatchKind.HAVE -> "Already on this phone"
                    MatchKind.PROBABLY -> "${item.track.artist} · you may have this one"
                    MatchKind.NEW -> item.track.artist
                } + " · " + formatBytes(item.track.size),
                color = TextLo,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
