package com.stellapp.disc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.accentGradient

/** Your listening, at a glance: top songs and artists, hours listened, when you listen. */
@Composable
fun StatsScreen(vm: PlayerViewModel, animate: Boolean, onClose: () -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    BackHandler(onBack = onClose)

    val music = remember(songs) { songs.filter { !it.isVideo } }
    val byId = remember(music) { music.associateBy { it.id } }
    val totalPlays = remember(stats) { stats.values.sumOf { it.count } }
    val hours = remember(tastes) { tastes.values.sumOf { it.listenedMs } / 3_600_000.0 }
    val topSongs = remember(stats, byId) {
        stats.entries.filter { it.key in byId && it.value.count > 0 }.sortedByDescending { it.value.count }.take(10)
            .map { byId.getValue(it.key) to it.value.count }
    }
    val topArtists = remember(stats, byId) {
        stats.entries.mapNotNull { (id, st) -> byId[id]?.let { it.artist to st.count } }
            .filter { !it.first.equals("Unknown artist", ignoreCase = true) }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
    }
    val dayParts = remember(tastes) {
        val sums = IntArray(4)
        tastes.values.forEach { t -> t.hours.forEachIndexed { i, n -> if (i < 4) sums[i] += n } }
        sums.toList()
    }
    val skips = remember(tastes) { tastes.values.sumOf { it.skips } }
    val completes = remember(tastes) { tastes.values.sumOf { it.completes } }
    val libraryHours = remember(music) { music.sumOf { it.durationMs } / 3_600_000.0 }

    Box(Modifier.fillMaxSize()) {
        Starfield(Modifier.fillMaxSize(), animate = animate, speed = 0.4f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Text("LISTENING STATS", style = neonTitleStyle(20))
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("PLAYS", "$totalPlays", Modifier.weight(1f))
                        StatTile("HOURS", if (hours < 10) "%.1f".format(hours) else "${hours.toInt()}", Modifier.weight(1f))
                        StatTile("FAVOURITES", "${favourites.size}", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("LIBRARY", "${music.size} songs", Modifier.weight(1f))
                        StatTile("RUNTIME", "${libraryHours.toInt()} h", Modifier.weight(1f))
                        val finished = if (completes + skips > 0) completes * 100 / (completes + skips) else 0
                        StatTile("PLAYED THROUGH", "$finished%", Modifier.weight(1f))
                    }
                }
                item {
                    Panel("WHEN YOU LISTEN") {
                        val labels = listOf("Morning", "Afternoon", "Evening", "Night")
                        val max = (dayParts.maxOrNull() ?: 0).coerceAtLeast(1)
                        labels.forEachIndexed { i, label ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = TextHi, fontSize = 13.sp, modifier = Modifier.width(84.dp))
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(12.dp)
                                        .background(primary.copy(alpha = 0.12f))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(dayParts[i].toFloat() / max)
                                            .fillMaxHeight()
                                            .background(Brush.horizontalGradient(accentGradient()))
                                    )
                                }
                                Text(" ${dayParts[i]}", color = TextLo, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                            }
                        }
                    }
                }
                item {
                    Panel("TOP SONGS") {
                        if (topSongs.isEmpty()) Text("Play some music and your favourites show up here.", color = TextLo, fontSize = 13.sp)
                        topSongs.forEachIndexed { i, (song, count) -> RankRow(i + 1, song.title, song.artist, count) { vm.playSong(song) } }
                    }
                }
                item {
                    Panel("TOP ARTISTS") {
                        if (topArtists.isEmpty()) Text("Nothing yet.", color = TextLo, fontSize = 13.sp)
                        topArtists.forEachIndexed { i, (artist, count) -> RankRow(i + 1, artist, null, count, null) }
                    }
                }
                item {
                    Text(
                        "Stats are counted on this phone only, from when you started using Stellar Disc.",
                        color = TextLo,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .neonPanel(primary, glow = 10.dp)
            .padding(12.dp)
    ) {
        Text(value, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = TextLo, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .neonPanel(primary, glow = 12.dp)
            .padding(14.dp)
    ) {
        Text(title, color = primary, fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun RankRow(rank: Int, title: String, subtitle: String?, count: Int, onClick: (() -> Unit)? = null) {
    val secondary = MaterialTheme.colorScheme.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(rank), color = secondary, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$count", color = TextLo, fontFamily = Mono, fontSize = 12.sp)
    }
}
