package com.stellapp.disc.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.PlayStat
import com.stellapp.disc.data.Playlist
import com.stellapp.disc.data.Song
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void

enum class LibTab(val label: String) {
    SONGS("SONGS"), ALBUMS("ALBUMS"), ARTISTS("ARTISTS"), FOLDERS("FOLDERS"), VIDEOS("VIDEOS"), PLAYLISTS("PLAYLISTS")
}

enum class SortMode(val label: String) { TITLE("A–Z"), RECENT("NEWEST"), ARTIST("ARTIST") }

enum class GroupKind { ALBUM, ARTIST, FOLDER, PLAYLIST, SMART }

data class SongGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val artSong: Song?,
    val kind: GroupKind,
    val playlistId: String? = null,
    val folder: String? = null,
)

private fun albumGroups(list: List<Song>): List<SongGroup> =
    list.groupBy { it.albumId }.map { (id, songs) ->
        val sorted = songs.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
        SongGroup("album:$id", sorted.first().album, sorted.first().artist, sorted, sorted.first(), GroupKind.ALBUM)
    }.sortedBy { it.title.lowercase() }

private fun artistGroups(list: List<Song>): List<SongGroup> =
    list.groupBy { it.artist }.map { (name, songs) ->
        val sorted = songs.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
        SongGroup("artist:$name", name, "${songs.size} tracks", sorted, sorted.first(), GroupKind.ARTIST)
    }.sortedBy { it.title.lowercase() }

private fun folderGroups(list: List<Song>): List<SongGroup> =
    list.filter { it.folder.isNotBlank() }.groupBy { it.folder }.map { (folder, songs) ->
        val sorted = songs.sortedBy { it.path.lowercase() }
        SongGroup("folder:$folder", folder.substringAfterLast('/'), "${songs.size} tracks · $folder", sorted, sorted.first(), GroupKind.FOLDER, folder = folder)
    }.sortedBy { it.title.lowercase() }

private fun playlistGroups(playlists: List<Playlist>, byId: Map<Long, Song>): List<SongGroup> =
    playlists.map { p ->
        val songs = p.songIds.mapNotNull { byId[it] }
        SongGroup("playlist:${p.id}", p.name, "${songs.size} tracks", songs, songs.firstOrNull(), GroupKind.PLAYLIST, p.id)
    }

private fun smartGroups(music: List<Song>, stats: Map<Long, PlayStat>, byId: Map<Long, Song>): List<SongGroup> {
    val recent = stats.entries.sortedByDescending { it.value.lastPlayed }.mapNotNull { byId[it.key] }.filter { it.showsInMusic }.take(50)
    val most = stats.entries.sortedByDescending { it.value.count }.mapNotNull { byId[it.key] }.filter { it.showsInMusic }.take(50)
    val added = music.sortedByDescending { it.dateAdded }.take(50)
    return listOf(
        SongGroup("smart:recent", "Recently played", "${recent.size} tracks", recent, recent.firstOrNull(), GroupKind.SMART),
        SongGroup("smart:most", "Most played", "${most.size} tracks", most, most.firstOrNull(), GroupKind.SMART),
        SongGroup("smart:added", "Recently added", "${added.size} tracks", added, added.firstOrNull(), GroupKind.SMART),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUplink: () -> Unit,
    onOpenSwap: () -> Unit,
    onSharePlaylist: (String) -> Unit,
    onOpenVideo: () -> Unit,
    onModernHome: () -> Unit,
) {
    val context = LocalContext.current
    val pinnedListIds by vm.pinnedPlaylists.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(LibTab.SONGS) }
    var sort by rememberSaveable { mutableStateOf(SortMode.TITLE) }
    var query by rememberSaveable { mutableStateOf("") }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }

    var menuSong by remember { mutableStateOf<Song?>(null) }
    var menuPlaylistId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    var confirmDelete by remember { mutableStateOf<Song?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SongGroup?>(null) }
    var deleting by remember { mutableStateOf<SongGroup?>(null) }
    var pendingDelete by remember { mutableStateOf<Song?>(null) }

    // ----- deleting files (the system asks the user to confirm on Android 11+) -----
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val s = pendingDelete
        pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK && s != null) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try { context.contentResolver.delete(s.uri, null, null) } catch (_: Exception) {}
            }
            vm.afterDelete(listOf(s.id))
        }
    }
    fun deleteFile(s: Song) {
        pendingDelete = s
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(s.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                context.contentResolver.delete(s.uri, null, null)
                pendingDelete = null
                vm.afterDelete(listOf(s.id))
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } else {
                pendingDelete = null
                vm.post("Android didn't allow deleting that file")
            }
        } catch (e: Exception) {
            pendingDelete = null
            vm.post("Couldn't delete that file")
        }
    }

    val videoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.loadLibrary(force = true)
    }

    val byId = remember(songs) { songs.associateBy { it.id } }
    val music = remember(songs) { songs.filter { it.showsInMusic } }
    val videos = remember(songs) { songs.filter { it.isVideo }.sortedByDescending { it.dateAdded } }
    val q = query.trim().lowercase()
    fun Song.matches() = q.isEmpty() || title.lowercase().contains(q) || artist.lowercase().contains(q) || album.lowercase().contains(q)

    val filtered = remember(music, q, sort) {
        val base = music.filter { it.matches() }
        when (sort) {
            SortMode.TITLE -> base.sortedBy { it.title.lowercase() }
            SortMode.RECENT -> base.sortedByDescending { it.dateAdded }
            SortMode.ARTIST -> base.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.track }))
        }
    }
    val shownVideos = remember(videos, q) { videos.filter { it.matches() } }
    val albums = remember(filtered) { albumGroups(filtered) }
    val artists = remember(filtered) { artistGroups(filtered) }
    val folders = remember(filtered) { folderGroups(filtered) }
    val allAlbums = remember(music) { albumGroups(music) }
    val allArtists = remember(music) { artistGroups(music) }
    val allFolders = remember(songs) { folderGroups(songs) }
    val allPlaylists = remember(playlists, byId) { playlistGroups(playlists, byId) }
    val smart = remember(music, stats, byId) { smartGroups(music, stats, byId) }
    val shownPlaylists = remember(allPlaylists, q) { if (q.isEmpty()) allPlaylists else allPlaylists.filter { it.title.lowercase().contains(q) } }

    val group: SongGroup? = openKey?.let { k ->
        when {
            k.startsWith("album:") -> allAlbums.firstOrNull { it.key == k }
            k.startsWith("artist:") -> allArtists.firstOrNull { it.key == k }
            k.startsWith("folder:") -> allFolders.firstOrNull { it.key == k }
            k.startsWith("smart:") -> smart.firstOrNull { it.key == k }
            else -> allPlaylists.firstOrNull { it.key == k }
        }
    }
    BackHandler(enabled = group != null) { openKey = null }

    val currentId = state.current?.id
    val openMenu: (Song, String?) -> Unit = { s, pid ->
        menuSong = s
        menuPlaylistId = pid
    }
    val playVideos: (List<Song>, Int) -> Unit = { list, i ->
        vm.playList(list, i)
        onOpenVideo()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onModernHome) {
                Icon(Icons.Rounded.Dashboard, contentDescription = "Modern home", tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text("STELLAR//DISC", style = neonTitleStyle(22))
                Text(
                    if (loading) "SCANNING SECTORS…" else "${music.size} TRACKS · ${videos.size} VIDEOS",
                    color = TextLo,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
            }
            IconButton(onClick = onOpenUplink) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = "Uplink", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenSwap) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Swap", tint = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextLo)
            }
        }

        Spacer(Modifier.height(12.dp))
        NeonTextField(
            value = query,
            onChange = { query = it },
            placeholder = "scan the archive…",
            modifier = Modifier.padding(horizontal = 16.dp),
            leading = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            },
            trailing = {
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear",
                        tint = TextLo,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))

        if (group == null) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibTab.entries.forEach { t -> NeonChip(t.label, tab == t) { tab = t } }
            }
        } else {
            GroupHeader(
                group = group,
                onBack = { openKey = null },
                onPlay = {
                    if (group.songs.firstOrNull()?.isVideo == true && group.songs.all { it.isVideo }) playVideos(group.songs, 0)
                    else vm.playList(group.songs, 0, shuffle = false)
                },
                onShuffle = { vm.shuffleAll(group.songs) },
                onRename = { renaming = group },
                onDelete = { deleting = group },
                onShare = { group.playlistId?.let(onSharePlaylist) },
                onHide = { group.folder?.let { vm.hideFolder(it) }; openKey = null },
                pinned = group.playlistId in pinnedListIds,
                onPin = { group.playlistId?.let { vm.togglePinPlaylist(it) } },
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f)) {
            when {
                group != null && group.songs.isEmpty() && group.kind == GroupKind.PLAYLIST ->
                    CenterMessage("EMPTY ORBIT\n\nLong-press any track and pick this playlist, or send Uplink downloads here.")
                group != null && group.songs.isEmpty() -> CenterMessage("NOTHING HERE YET\n\nPlay some music and this fills up.")
                group != null -> SongList(
                    songs = group.songs,
                    currentId = currentId,
                    playing = state.isPlaying,
                    onPlay = { i ->
                        if (group.songs[i].isVideo) playVideos(group.songs, i) else vm.playSong(group.songs[i])
                    },
                    onLongPress = { s -> openMenu(s, group.playlistId) },
                )
                tab == LibTab.PLAYLISTS -> PlaylistList(
                    smart = smart,
                    groups = shownPlaylists,
                    onCreate = { creatingPlaylist = true },
                    onOpen = { openKey = it.key },
                )
                tab == LibTab.VIDEOS -> VideoGrid(
                    videos = shownVideos,
                    needsAccess = !vm.hasVideoAccess(),
                    onAskAccess = {
                        videoPermission.launch(
                            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    },
                    onPlay = { i -> playVideos(shownVideos, i) },
                    onLongPress = { s -> openMenu(s, null) },
                )
                loading && songs.isEmpty() -> CenterMessage("SCANNING SECTORS…", showSpinner = true)
                music.isEmpty() -> CenterMessage("NO SIGNALS FOUND\n\nPut music files in your phone's Music folder, or pull some in with Uplink or Swap.")
                tab == LibTab.SONGS -> SongList(
                    songs = filtered,
                    currentId = currentId,
                    playing = state.isPlaying,
                    onPlay = { i -> vm.playSong(filtered[i]) },
                    onLongPress = { s -> openMenu(s, null) },
                    header = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NeonChip("▶ PLAY", false) { vm.playList(filtered, 0, shuffle = false) }
                            NeonChip("⤨ SHUFFLE", false) { vm.shuffleAll(filtered) }
                            Spacer(Modifier.weight(1f))
                            NeonChip("SORT: ${sort.label}", false) {
                                sort = SortMode.entries[(sort.ordinal + 1) % SortMode.entries.size]
                            }
                        }
                    },
                )
                tab == LibTab.ALBUMS -> AlbumGrid(albums) { openKey = it.key }
                tab == LibTab.FOLDERS -> GroupList(folders, round = false) { openKey = it.key }
                else -> GroupList(artists, round = true) { openKey = it.key }
            }
        }

        MiniPlayer(
            s = state,
            onTogglePlay = vm::togglePlay,
            onNext = vm::next,
            onOpen = onOpenPlayer,
        )
        Spacer(Modifier.navigationBarsPadding())
    }

    menuSong?.let { s ->
        SongActionSheet(
            song = s,
            playlists = playlists,
            inPlaylistId = menuPlaylistId,
            vm = vm,
            onDismiss = { menuSong = null },
            onEdit = if (s.isStream) null else ({
                menuSong = null
                editing = s
            }),
            onDelete = if (s.isStream) null else ({
                menuSong = null
                confirmDelete = s
            }),
            onHideFolder = if (s.isStream) null else ({
                menuSong = null
                vm.hideFolder(s.folder)
            }),
        )
    }
    editing?.let { s -> EditInfoDialog(s, vm) { editing = null } }
    confirmDelete?.let { s ->
        ConfirmDialog(
            title = "DELETE FILE",
            body = "Delete \"${s.title}\" from your phone? This can't be undone.",
            confirmLabel = "DELETE",
            onDismiss = { confirmDelete = null },
        ) {
            confirmDelete = null
            deleteFile(s)
        }
    }
    if (creatingPlaylist) {
        NameDialog(
            title = "NEW PLAYLIST",
            initial = "",
            confirmLabel = "CREATE",
            onDismiss = { creatingPlaylist = false },
        ) { name ->
            val id = vm.createPlaylist(name)
            creatingPlaylist = false
            openKey = "playlist:$id"
        }
    }
    renaming?.let { g ->
        NameDialog(title = "RENAME PLAYLIST", initial = g.title, onDismiss = { renaming = null }) { name ->
            g.playlistId?.let { vm.renamePlaylist(it, name) }
            renaming = null
        }
    }
    deleting?.let { g ->
        ConfirmDialog(
            title = "DELETE PLAYLIST",
            body = "Delete \"${g.title}\"? The tracks stay in your library.",
            confirmLabel = "DELETE",
            onDismiss = { deleting = null },
        ) {
            g.playlistId?.let { vm.deletePlaylist(it) }
            deleting = null
            openKey = null
        }
    }
}

@Composable
private fun GroupHeader(
    group: SongGroup,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onHide: () -> Unit,
    pinned: Boolean,
    onPin: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
        }
        Column(Modifier.weight(1f)) {
            Text(group.title, color = TextHi, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (group.kind == GroupKind.ALBUM) "${group.subtitle} · ${group.songs.size} tracks" else group.subtitle,
                color = TextLo,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (group.kind == GroupKind.PLAYLIST) {
            IconButton(onClick = onPin) { Icon(Icons.Rounded.PushPin, contentDescription = "Pin to home", tint = if (pinned) MaterialTheme.colorScheme.secondary else TextLo) }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = TextLo) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = TextLo) }
        }
        if (group.kind == GroupKind.FOLDER) {
            IconButton(onClick = onHide) { Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide folder", tint = TextLo) }
        }
        if (group.songs.isNotEmpty()) {
            NeonChip("▶", false, onPlay)
            Spacer(Modifier.width(6.dp))
            NeonChip("⤨", false, onShuffle)
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentId: Long?,
    playing: Boolean,
    onPlay: (Int) -> Unit,
    onLongPress: (Song) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (header != null) item(key = "header") { header() }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongRow(
                song = song,
                active = song.id == currentId,
                playing = playing,
                onClick = { onPlay(i) },
                onLongClick = { onLongPress(song) },
            )
        }
    }
}

@Composable
private fun PlaylistList(smart: List<SongGroup>, groups: List<SongGroup>, onCreate: () -> Unit, onOpen: (SongGroup) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "smart-label") { SectionLabel("SMART ORBITS", Modifier.padding(horizontal = 16.dp)) }
        items(smart, key = { it.key }) { g -> GroupRow(g, round = false, accent = secondary) { onOpen(g) } }
        item(key = "mine-label") { SectionLabel("YOUR PLAYLISTS", Modifier.padding(horizontal = 16.dp)) }
        item(key = "new") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .neonPanel(primary, SmallCut, glow = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("NEW PLAYLIST", color = primary, fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No playlists yet. Long-press any track to add it to one.",
                    color = TextLo,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        items(groups, key = { it.key }) { g -> GroupRow(g, round = false) { onOpen(g) } }
    }
}

@Composable
private fun GroupRow(g: SongGroup, round: Boolean, accent: androidx.compose.ui.graphics.Color = TextHi, onClick: () -> Unit) {
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
            Text(g.title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(g.subtitle, color = TextLo, fontSize = 12.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GroupList(groups: List<SongGroup>, round: Boolean, onOpen: (SongGroup) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        items(groups, key = { it.key }) { g -> GroupRow(g, round) { onOpen(g) } }
    }
}

@Composable
private fun AlbumGrid(albums: List<SongGroup>, onOpen: (SongGroup) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.key }) { g ->
            Column(
                Modifier
                    .neonPanel(primary.copy(alpha = 0.45f), glow = 0.dp)
                    .clickable { onOpen(g) }
                    .padding(8.dp)
            ) {
                AlbumArt(g.artSong, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(6.dp))
                Text(g.title, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(g.subtitle, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGrid(
    videos: List<Song>,
    needsAccess: Boolean,
    onAskAccess: () -> Unit,
    onPlay: (Int) -> Unit,
    onLongPress: (Song) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (needsAccess) {
            item(key = "access", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .neonPanel(primary.copy(alpha = 0.6f), glow = 0.dp)
                        .padding(14.dp)
                ) {
                    Text("Showing Stellar Disc videos only.", color = TextHi)
                    Text("Allow video access to see every video on your phone.", color = TextLo, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    NeonChip("ALLOW VIDEO ACCESS", false, onAskAccess)
                }
            }
        }
        if (videos.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No videos yet. Download some with Uplink as MP4 or MKV.",
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(videos.size, key = { videos[it].id }) { i ->
            val v = videos[i]
            Column(
                Modifier
                    .neonPanel(primary.copy(alpha = 0.45f), glow = 0.dp)
                    .combinedClickable(onClick = { onPlay(i) }, onLongClick = { onLongPress(v) })
                    .padding(6.dp)
            ) {
                Box {
                    AlbumArt(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Text(
                        formatTime(v.durationMs),
                        color = TextHi,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Void.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(v.title, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, showSpinner: Boolean = false) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
        }
        Text(text, color = TextLo, fontFamily = Mono, textAlign = TextAlign.Center, letterSpacing = 1.sp)
    }
}
