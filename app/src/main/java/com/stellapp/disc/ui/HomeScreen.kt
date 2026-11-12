package com.stellapp.disc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.HeroMode
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.Mix
import com.stellapp.disc.data.PlayStat
import com.stellapp.disc.data.Playlist
import com.stellapp.disc.data.Song
import com.stellapp.disc.data.Taste
import com.stellapp.disc.data.TasteStore
import com.stellapp.disc.ui.theme.accentGradient
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void

private fun Playlist.toGroup(byId: Map<Long, Song>): SongGroup {
    val list = songIds.mapNotNull { byId[it] }
    return SongGroup("playlist:$id", name, "${list.size} tracks", list, list.firstOrNull(), GroupKind.PLAYLIST, id)
}

private fun Mix.toGroup(): SongGroup = SongGroup(key, title, subtitle, songs, songs.firstOrNull(), GroupKind.SMART)

/** Why a song is in Regular picks, in a few words. */
private fun reasonFor(t: Taste?, song: Song): String = when {
    t == null || t.plays == 0 -> if (System.currentTimeMillis() / 1000 - song.dateAdded < 14 * 24 * 3600) "NEW IN YOUR LIBRARY" else "SOMETHING DIFFERENT"
    t.completes >= 5 -> "PLAYED ${t.plays} TIMES"
    t.hours[TasteStore.bucketOf(System.currentTimeMillis())] * 2 >= t.hours.sum() -> "YOUR ${listOf("MORNING", "AFTERNOON", "EVENING", "LATE NIGHT")[TasteStore.bucketOf(System.currentTimeMillis())]} GO-TO"
    else -> "BACK IN ROTATION"
}

/** What the hero card is showing right now. */
private sealed interface Hero {
    val reason: String
    data class OfSong(val song: Song, override val reason: String, val queue: List<Song>) : Hero
    data class OfList(val group: SongGroup, override val reason: String) : Hero
}

private const val DAY_MS = 24L * 3600 * 1000

private fun pickHero(
    mode: HeroMode,
    songs: List<Song>,
    picks: List<Song>,
    tastes: Map<Long, Taste>,
    stats: Map<Long, PlayStat>,
    listGroups: List<SongGroup>,
    pinnedLists: List<SongGroup>,
    seed: Long,
): Hero? {
    if (songs.isEmpty()) return null
    val now = System.currentTimeMillis()
    val count = { s: Song -> stats[s.id]?.count ?: tastes[s.id]?.plays ?: 0 }
    val last = { s: Song -> maxOf(stats[s.id]?.lastPlayed ?: 0L, tastes[s.id]?.lastPlayed ?: 0L) }
    val rng = kotlin.random.Random(seed)
    val fallback = picks.firstOrNull()?.let { Hero.OfSong(it, reasonFor(tastes[it.id], it), picks) }

    return when (mode) {
        HeroMode.TOP_PICK -> fallback
        HeroMode.MOST_PLAYED -> {
            val ranked = songs.filter { count(it) > 0 }.sortedByDescending(count)
            ranked.firstOrNull()?.let { Hero.OfSong(it, "YOUR #1 · PLAYED ${count(it)} TIMES", ranked.take(40)) } ?: fallback
        }
        HeroMode.PLAYLIST -> {
            val pinned = pinnedLists.firstOrNull { it.songs.isNotEmpty() }
            if (pinned != null) {
                Hero.OfList(pinned, "PINNED PLAYLIST")
            } else {
                listGroups.filter { it.songs.isNotEmpty() }
                    .maxByOrNull { g -> g.songs.sumOf(count) }
                    ?.let { Hero.OfList(it, "YOUR TOP PLAYLIST") } ?: fallback
            }
        }
        HeroMode.RARE -> {
            val rare = songs.filter { count(it) in 1..2 }.shuffled(rng)
            if (rare.isNotEmpty()) {
                val s = rare.first()
                Hero.OfSong(s, if (count(s) == 1) "ONLY PLAYED ONCE" else "ONLY PLAYED TWICE", rare.take(40))
            } else {
                val never = songs.filter { count(it) == 0 }.shuffled(rng)
                never.firstOrNull()?.let { Hero.OfSong(it, "NEVER PLAYED", never.take(40)) } ?: fallback
            }
        }
        HeroMode.FORGOTTEN -> {
            val gems = songs.filter { count(it) >= 3 && now - last(it) > 30 * DAY_MS }.sortedByDescending(count)
            val pool = gems.ifEmpty { songs.filter { count(it) > 0 }.sortedBy(last) }
            pool.firstOrNull()?.let {
                val days = ((now - last(it)) / DAY_MS).coerceAtLeast(1)
                Hero.OfSong(it, "NOT HEARD IN $days DAY${if (days == 1L) "" else "S"}", pool.take(40))
            } ?: fallback
        }
        HeroMode.FRESH -> {
            val fresh = songs.sortedByDescending { it.dateAdded }
            fresh.firstOrNull()?.let { Hero.OfSong(it, "JUST ADDED", fresh.take(40)) }
        }
        HeroMode.RANDOM -> {
            val mixed = songs.shuffled(rng)
            Hero.OfSong(mixed.first(), "RANDOM DRAW", mixed.take(40))
        }
    }
}

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUplink: () -> Unit,
    onOpenSwap: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGroup: (SongGroup) -> Unit,
    onUnderTheHood: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val songs by vm.songs.collectAsStateWithLifecycle()
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val pinnedSongIds by vm.pinnedSongs.collectAsStateWithLifecycle()
    val pinnedListIds by vm.pinnedPlaylists.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val heroMode by vm.heroMode.collectAsStateWithLifecycle()

    val byId = remember(songs) { songs.associateBy { it.id } }
    val picks = remember(songs, tastes) { TasteStore.picks(songs) }
    val mixes = remember(songs, tastes) { TasteStore.mixes(songs) }
    val favIds by vm.favourites.collectAsStateWithLifecycle()
    // Favourites leads the playlists row once you've hearted something.
    val listGroups = remember(playlists, byId, favIds) {
        val favs = favIds.mapNotNull { byId[it] }
        val favGroup = if (favs.isEmpty()) emptyList()
        else listOf(SongGroup("smart:favourites", "♥ Favourites", "${favs.size} tracks", favs, favs.first(), GroupKind.SMART))
        favGroup + playlists.map { it.toGroup(byId) }
    }
    val pinnedLists = remember(listGroups, pinnedListIds) { pinnedListIds.mapNotNull { id -> listGroups.firstOrNull { it.playlistId == id } } }
    val pinnedSongs = remember(pinnedSongIds, byId) { pinnedSongIds.mapNotNull { byId[it] } }
    val heroSeed = remember { System.nanoTime() }
    val hero = remember(heroMode, songs, picks, stats, listGroups, pinnedLists) {
        pickHero(heroMode, songs, picks, tastes, stats, listGroups, pinnedLists, heroSeed)
    }
    val heroSongId = (hero as? Hero.OfSong)?.song?.id
    val rowPicks = remember(picks, heroSongId) { picks.filter { it.id != heroSongId }.take(12) }
    var pickingHero by remember { mutableStateOf(false) }

    var menuSong by remember { mutableStateOf<Song?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    var creating by remember { mutableStateOf(false) }
    val deleteSong = rememberSongDeleter(vm)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar: hood toggle · logo · search, settings
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onUnderTheHood) {
                Icon(Icons.Rounded.Build, contentDescription = "Under the hood view", tint = TextLo)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("STELLAR//DISC", style = neonTitleStyle(20))
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = primary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextLo)
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            // ---------- Regular picks ----------
            item(key = "picks-title") {
                SectionHead(
                    "REGULAR PICKS",
                    if (heroMode != HeroMode.TOP_PICK) heroMode.blurb
                    else if (TasteStore.totalListens < 10) "Learning your taste. The more you play, the better this gets."
                    else "Picked from how you've been listening",
                ) {
                    SmallTab("◈ ${heroMode.label.uppercase()}") { pickingHero = true }
                }
            }
            if (hero == null) {
                item(key = "picks-empty") {
                    Text(
                        if (loading) "Scanning your library…" else "No music yet. Pull some in with Uplink or Swap.",
                        color = TextLo,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                item(key = "hero") {
                    when (hero) {
                        is Hero.OfSong -> HeroCard(
                            art = hero.song,
                            reason = hero.reason,
                            title = hero.song.title,
                            subtitle = hero.song.artist,
                            active = state.current?.id == hero.song.id,
                            onClick = { vm.playSong(hero.song) },
                            onPlay = { vm.playSong(hero.song) },
                            onLongPress = { menuSong = hero.song },
                        )
                        is Hero.OfList -> HeroCard(
                            art = hero.group.artSong,
                            reason = hero.reason,
                            title = hero.group.title,
                            subtitle = hero.group.subtitle,
                            active = state.current != null && hero.group.songs.any { it.id == state.current?.id },
                            onClick = { onOpenGroup(hero.group) },
                            onPlay = { vm.playList(hero.group.songs, 0) },
                            onLongPress = { onOpenGroup(hero.group) },
                        )
                        null -> Unit
                    }
                }
                if (rowPicks.isNotEmpty()) {
                    item(key = "pick-rows") {
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(rowPicks, key = { _, s -> s.id }) { i, s ->
                                PickTile(s, active = state.current?.id == s.id, playing = state.isPlaying, onClick = { vm.playSong(s) }, onLongPress = { menuSong = s })
                            }
                        }
                    }
                }
            }

            // ---------- quick launch ----------
            item(key = "quick") {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeonChip("⇣ UPLINK", false, onOpenUplink)
                    NeonChip("⇄ SWAP", false, onOpenSwap)
                    NeonChip("▤ FULL LIBRARY", false, onUnderTheHood)
                }
            }

            // ---------- playlists ----------
            item(key = "pl-title") {
                SectionHead("PLAYLISTS", if (listGroups.isEmpty()) "Nothing here yet. Tap + NEW to make one." else null) {
                    SmallTab("+ NEW") { creating = true }
                }
            }
            item(key = "pl-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(listGroups, key = { it.key }) { g -> CoverCard(g.artSong, g.title, g.subtitle, pinned = g.playlistId in pinnedListIds) { onOpenGroup(g) } }
                }
            }

            // ---------- pinned playlists ----------
            if (pinnedLists.isNotEmpty()) {
                item(key = "pin-pl-title") { SectionHead("PINNED PLAYLISTS", null) }
                item(key = "pin-pl-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(pinnedLists, key = { it.key }) { g -> CoverCard(g.artSong, g.title, g.subtitle, pinned = true, big = true) { onOpenGroup(g) } }
                    }
                }
            }

            // ---------- pinned songs ----------
            if (pinnedSongs.isNotEmpty()) {
                item(key = "pin-songs-title") { SectionHead("PINNED SONGS", null) }
                itemsIndexed(pinnedSongs, key = { _, s -> "pin-${s.id}" }) { i, s ->
                    SongRow(
                        song = s,
                        active = state.current?.id == s.id,
                        playing = state.isPlaying,
                        onClick = { vm.playSong(s) },
                        onLongClick = { menuSong = s },
                    )
                }
            } else {
                item(key = "pin-hint") {
                    Text(
                        "Tip: long-press any song or open a playlist to pin it here.",
                        color = TextLo,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // ---------- generated mixes ----------
            if (mixes.isNotEmpty()) {
                item(key = "mix-title") { SectionHead("MADE FROM YOUR LISTENING", "Mixes that update as your habits change") }
                item(key = "mix-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(mixes, key = { _, m -> m.key }) { i, m -> MixCard(m, i, primary, secondary) { onOpenGroup(m.toGroup()) } }
                    }
                }
            }
        }

        MiniPlayer(s = state, onTogglePlay = vm::togglePlay, onNext = vm::next, onOpen = onOpenPlayer, onPrevious = { vm.swipeTrack(false) })
        Spacer(Modifier.navigationBarsPadding())
    }

    menuSong?.let { s ->
        SongActionSheet(
            song = s,
            playlists = playlists,
            inPlaylistId = null,
            vm = vm,
            onDismiss = { menuSong = null },
            onEdit = if (s.isStream) null else ({
                menuSong = null
                editing = s
            }),
            onDelete = if (s.isStream) null else ({
                menuSong = null
                deleteSong(s)
            }),
            onHideFolder = if (s.isStream) null else ({
                menuSong = null
                vm.hideFolder(s.folder)
            }),
        )
    }
    editing?.let { s -> EditInfoDialog(s, vm) { editing = null } }
    if (creating) {
        NameDialog(title = "NEW PLAYLIST", initial = "", confirmLabel = "CREATE", onDismiss = { creating = false }) { name ->
            vm.createPlaylist(name)
            creating = false
        }
    }
    if (pickingHero) {
        ModalBottomSheet(onDismissRequest = { pickingHero = false }, containerColor = PanelSolid, contentColor = TextHi) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                Text(
                    "HERO SHOWS",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                HeroMode.entries.forEach { m ->
                    val on = m == heroMode
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmallCut)
                            .clickable {
                                vm.setHeroMode(m)
                                pickingHero = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            (if (on) "◉ " else "○ ") + m.label,
                            color = if (on) MaterialTheme.colorScheme.primary else TextHi,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(m.blurb, color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(start = 22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, subtitle: String?, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
            if (action != null) {
                Spacer(Modifier.width(10.dp))
                action()
            }
        }
        if (subtitle != null) Text(subtitle, color = TextLo, fontSize = 12.sp)
    }
}

/** Small outlined tab that sits beside a section title. */
@Composable
private fun SmallTab(label: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        label,
        color = primary,
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(SmallCut)
            .border(1.dp, primary.copy(alpha = 0.7f), SmallCut)
            .background(primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun HeroCard(
    art: Song?,
    reason: String,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .neonPanel(primary, glow = 18.dp, fill = Void)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        AlbumArt(art, Modifier.fillMaxSize(), shape = NeonCut)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Void.copy(alpha = 0.35f), Void.copy(alpha = 0.92f))))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .padding(end = 72.dp)
        ) {
            Text(reason, color = secondary, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextLo, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .shadow(20.dp, CircleShape, ambientColor = primary, spotColor = primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(accentGradient()))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            if (active) EqualizerBars(true, Void, Modifier.size(22.dp))
            else Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Void, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun PickTile(song: Song, active: Boolean, playing: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .width(236.dp)
            .height(64.dp)
            .neonPanel(if (active) primary else primary.copy(alpha = 0.35f), SmallCut, glow = 0.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            AlbumArt(song, Modifier.fillMaxSize())
            if (active) {
                Box(Modifier.fillMaxSize().background(Void.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    EqualizerBars(playing, primary, Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (active) primary else TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CoverCard(art: Song?, title: String, subtitle: String, pinned: Boolean, big: Boolean = false, onClick: () -> Unit) {
    val size = if (big) 150.dp else 120.dp
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box {
            AlbumArt(art, Modifier.size(size), shape = NeonCut)
            if (pinned && !big) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = TextLo, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun MixCard(mix: Mix, index: Int, primary: Color, secondary: Color, onClick: () -> Unit) {
    val tint = lerp(primary, secondary, (index % 4) / 3f)
    Box(
        Modifier
            .size(150.dp)
            .neonPanel(tint, glow = 8.dp, fill = Void)
            .clickable(onClick = onClick)
    ) {
        AlbumArt(mix.songs.firstOrNull(), Modifier.fillMaxSize(), shape = NeonCut)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.25f), Void.copy(alpha = 0.9f))))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(mix.title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${mix.songs.size} tracks", color = tint, fontFamily = Mono, fontSize = 10.sp)
        }
    }
}

/** A playlist, mix, artist or album page opened from Home or Search. */
@Composable
fun GroupPage(vm: PlayerViewModel, group: SongGroup, animate: Boolean, onClose: () -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val pinnedListIds by vm.pinnedPlaylists.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val byId = remember(songs) { songs.associateBy { it.id } }
    // Playlists stay live so edits show up straight away.
    val list = remember(group, playlists, byId) {
        group.playlistId?.let { id -> playlists.firstOrNull { it.id == id }?.songIds?.mapNotNull { byId[it] } } ?: group.songs
    }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    var saving by remember { mutableStateOf(false) }
    val deleteSong = rememberSongDeleter(vm)

    BackHandler(onBack = onClose)

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
        ) {
            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = neonTitleStyle(18), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size} tracks · ${group.subtitle}", color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                group.playlistId?.let { id ->
                    IconButton(onClick = { vm.togglePinPlaylist(id) }) {
                        Icon(Icons.Rounded.PushPin, contentDescription = "Pin to home", tint = if (id in pinnedListIds) MaterialTheme.colorScheme.secondary else TextLo)
                    }
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (list.isNotEmpty()) {
                    NeonChip("▶ PLAY", false) { vm.playList(list, 0, shuffle = false) }
                    NeonChip("⤨ SHUFFLE", false) { vm.shuffleAll(list) }
                }
                if (group.kind != GroupKind.PLAYLIST && list.isNotEmpty()) NeonChip("+ SAVE AS PLAYLIST", false) { saving = true }
            }
            if (list.isEmpty()) {
                Text(
                    "Nothing here yet.",
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        active = state.current?.id == s.id,
                        playing = state.isPlaying,
                        onClick = { vm.playSong(s) },
                        onLongClick = { menuSong = s },
                    )
                }
            }
        }
    }

    menuSong?.let { s ->
        SongActionSheet(
            song = s,
            playlists = playlists,
            inPlaylistId = group.playlistId,
            vm = vm,
            onDismiss = { menuSong = null },
            onEdit = if (s.isStream) null else ({
                menuSong = null
                editing = s
            }),
            onDelete = if (s.isStream) null else ({
                menuSong = null
                deleteSong(s)
            }),
        )
    }
    editing?.let { s -> EditInfoDialog(s, vm) { editing = null } }
    if (saving) {
        NameDialog(title = "SAVE AS PLAYLIST", initial = group.title, confirmLabel = "SAVE", onDismiss = { saving = false }) { name ->
            val id = vm.createPlaylist(name)
            vm.addToPlaylist(id, list.map { it.id })
            saving = false
            vm.post("Saved \"$name\"")
        }
    }
}

/** Search across songs, videos, playlists, artists and albums. */
@Composable
fun SearchScreen(vm: PlayerViewModel, animate: Boolean, onClose: () -> Unit, onOpenGroup: (SongGroup) -> Unit, onOpenVideo: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    val deleteSong = rememberSongDeleter(vm)
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
    BackHandler(onBack = onClose)

    val q = query.trim().lowercase()
    val byId = remember(songs) { songs.associateBy { it.id } }
    val music = remember(songs, q) {
        if (q.isEmpty()) emptyList() else songs.filter { it.showsInMusic && (it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)) }.take(60)
    }
    val videos = remember(songs, q) {
        if (q.isEmpty()) emptyList() else songs.filter { it.isVideo && !it.inAppFolder && it.title.lowercase().contains(q) }.take(10)
    }
    val lists = remember(playlists, q, byId) { if (q.isEmpty()) emptyList() else playlists.filter { it.name.lowercase().contains(q) }.map { it.toGroup(byId) } }
    val artists = remember(songs, q) {
        if (q.isEmpty()) emptyList() else songs.filter { it.showsInMusic && it.artist.lowercase().contains(q) }.groupBy { it.artist }
            .map { (a, l) -> SongGroup("artist:$a", a, "artist", l.sortedWith(compareBy({ it.album.lowercase() }, { it.track })), l.first(), GroupKind.ARTIST) }.take(8)
    }
    val albums = remember(songs, q) {
        if (q.isEmpty()) emptyList() else songs.filter { it.showsInMusic && it.album.lowercase().contains(q) }.groupBy { it.albumId }
            .map { (id, l) -> SongGroup("album:$id", l.first().album, l.first().artist, l.sortedWith(compareBy({ it.track }, { it.title.lowercase() })), l.first(), GroupKind.ALBUM) }.take(8)
    }

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
        ) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                NeonTextField(
                    value = query,
                    onChange = { query = it },
                    placeholder = "songs, artists, albums, playlists…",
                    modifier = Modifier.weight(1f),
                    focusRequester = focus,
                    leading = { Icon(Icons.Rounded.Search, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp)) },
                    trailing = {
                        if (query.isNotEmpty()) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextLo, modifier = Modifier.size(18.dp).clickable { query = "" })
                        }
                    },
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (q.isNotEmpty() && music.isEmpty() && videos.isEmpty() && lists.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
                    item { Text("No signals match \"$query\".", color = TextLo, modifier = Modifier.padding(24.dp)) }
                }
                if (lists.isNotEmpty()) {
                    item { SectionHead("PLAYLISTS", null) }
                    items(lists, key = { "l" + it.key }) { g -> SearchGroupRow(g) { onOpenGroup(g) } }
                }
                if (artists.isNotEmpty()) {
                    item { SectionHead("ARTISTS", null) }
                    items(artists, key = { "a" + it.key }) { g -> SearchGroupRow(g, round = true) { onOpenGroup(g) } }
                }
                if (albums.isNotEmpty()) {
                    item { SectionHead("ALBUMS", null) }
                    items(albums, key = { "b" + it.key }) { g -> SearchGroupRow(g) { onOpenGroup(g) } }
                }
                if (music.isNotEmpty()) {
                    item { SectionHead("SONGS", null) }
                    itemsIndexed(music, key = { _, s -> "s${s.id}" }) { i, s ->
                        SongRow(s, active = state.current?.id == s.id, playing = state.isPlaying, onClick = { vm.playSong(s) }, onLongClick = { menuSong = s })
                    }
                }
                if (videos.isNotEmpty()) {
                    item { SectionHead("PHONE VIDEOS", null) }
                    itemsIndexed(videos, key = { _, s -> "v${s.id}" }) { i, s ->
                        SongRow(s, active = state.current?.id == s.id, playing = state.isPlaying, onClick = {
                            vm.playList(videos, i)
                            onOpenVideo()
                        }, onLongClick = { menuSong = s })
                    }
                }
            }
        }
    }

    menuSong?.let { s ->
        SongActionSheet(
            song = s,
            playlists = playlists,
            inPlaylistId = null,
            vm = vm,
            onDismiss = { menuSong = null },
            onEdit = if (s.isStream) null else ({
                menuSong = null
                editing = s
            }),
            onDelete = if (s.isStream) null else ({
                menuSong = null
                deleteSong(s)
            }),
        )
    }
    editing?.let { s -> EditInfoDialog(s, vm) { editing = null } }
}

@Composable
private fun SearchGroupRow(g: SongGroup, round: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(g.artSong, Modifier.size(48.dp), shape = if (round) CircleShape else SmallCut)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, color = TextHi, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${g.songs.size} tracks · ${g.subtitle}", color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
