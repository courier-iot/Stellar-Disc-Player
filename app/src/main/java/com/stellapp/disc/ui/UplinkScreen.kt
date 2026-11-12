package com.stellapp.disc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellapp.disc.AppInfo
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.swap.ShareText
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void
import com.stellapp.disc.uplink.DlFormat
import com.stellapp.disc.uplink.DownloadJob
import com.stellapp.disc.uplink.DownloadOptions
import com.stellapp.disc.uplink.JobStatus
import com.stellapp.disc.uplink.PlaylistTarget
import com.stellapp.disc.uplink.UplinkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NEW_TARGET = "__new__"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UplinkScreen(
    vm: PlayerViewModel,
    animate: Boolean,
    onClose: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenSwapList: (String) -> Unit,
    onStreamStarted: (isVideo: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val jobs by UplinkManager.jobs.collectAsStateWithLifecycle()
    val version by UplinkManager.version.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val shared by vm.sharedUrl.collectAsStateWithLifecycle()
    val saved = remember { vm.loadUplinkPrefs() }

    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(saved.format) }
    var kbps by rememberSaveable { mutableIntStateOf(saved.audioKbps) }
    var height by rememberSaveable { mutableIntStateOf(saved.maxHeight) }
    var whole by rememberSaveable { mutableStateOf(saved.wholePlaylist) }
    var embed by rememberSaveable { mutableStateOf(saved.embed) }
    var queue by rememberSaveable { mutableStateOf(saved.queueWhenDone) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var subs by rememberSaveable { mutableStateOf(saved.subtitles) }
    var subLangs by rememberSaveable { mutableStateOf(saved.subLangs) }
    var skipExisting by rememberSaveable { mutableStateOf(saved.skipExisting) }
    var streaming by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)
    LaunchedEffect(shared) {
        shared?.let {
            url = it
            vm.consumeSharedUrl()
        }
    }
    LaunchedEffect(Unit) { UplinkManager.ensureInit(context) }

    // If the chosen playlist gets deleted, fall back to none.
    LaunchedEffect(playlists, targetId) {
        val t = targetId
        if (t != null && t != NEW_TARGET && playlists.none { it.id == t }) targetId = null
    }

    val urlOk = url.trim().let { it.startsWith("http://") || it.startsWith("https://") } && !ShareText.looksLikeList(url)

    fun start() {
        val t = targetId
        val target = when (t) {
            null -> PlaylistTarget.None
            NEW_TARGET -> PlaylistTarget.New(
                newName.ifBlank { "Uplink " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date()) }
            )
            else -> PlaylistTarget.Existing(t)
        }
        vm.startDownload(
            DownloadOptions(
                url = url.trim(),
                format = format,
                audioKbps = kbps,
                maxHeight = height,
                wholePlaylist = whole,
                embed = embed,
                target = target,
                queueWhenDone = queue,
                extraArgs = extra,
                subtitles = subs,
                subLangs = subLangs,
                skipExisting = skipExisting,
            )
        )
        url = ""
    }

    fun stream() {
        if (streaming) return
        streaming = true
        streamError = null
        val link = url.trim()
        scope.launch {
            val result = UplinkManager.resolveStream(context, link, video = format.isVideo)
            streaming = false
            result.onSuccess { info ->
                vm.playStream(info)
                url = ""
                onStreamStarted(info.isVideo)
            }.onFailure { streamError = it.message ?: "Couldn't open that stream" }
        }
    }

    val videoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

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
            // Top bar
            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text("UPLINK", style = neonTitleStyle(22))
                    Text(
                        "yt-dlp " + (version ?: "booting…"),
                        color = TextLo,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                }
                IconButton(onClick = onOpenCredits) {
                    Icon(Icons.Rounded.Info, contentDescription = "Credits & Support", tint = TextLo)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SectionLabel("SIGNAL SOURCE")
                NeonTextField(
                    value = url,
                    onChange = { url = it },
                    placeholder = "https://…",
                    singleLine = false, // so a pasted multi-line playlist share survives
                    leading = { Icon(Icons.Rounded.Link, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp)) },
                    trailing = {
                        if (url.isEmpty()) {
                            Text(
                                "PASTE",
                                color = primary,
                                fontFamily = Mono,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.clickable {
                                    clipboard.getText()?.text?.trim()?.let { url = it }
                                },
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = TextLo,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { url = "" },
                            )
                        }
                    },
                )
                if (ShareText.looksLikeList(url)) {
                    Spacer(Modifier.height(8.dp))
                    NeonChip("THAT'S A SHARED PLAYLIST · OPEN IN SWAP ›", false) {
                        val text = url
                        url = ""
                        onOpenSwapList(text)
                    }
                }
                Text(
                    "Works with YouTube, SoundCloud, Bandcamp, Vimeo and 1,000+ other sites. See the full list ›",
                    color = TextLo,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable { openUrl(context, AppInfo.SUPPORTED_SITES_URL) },
                )

                SectionLabel("IF IT'S A PLAYLIST OR ALBUM")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("GRAB EVERYTHING", whole) { whole = true }
                    NeonChip("JUST THIS ONE", !whole) { whole = false }
                }

                SectionLabel("SAVE AS · AUDIO")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlFormat.entries.filter { !it.isVideo }.forEach { f -> NeonChip(f.label, format == f) { format = f } }
                }
                SectionLabel("SAVE AS · VIDEO")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlFormat.entries.filter { it.isVideo }.forEach { f -> NeonChip(f.label, format == f) { format = f } }
                }
                Text(format.hint, color = secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

                if (format.bitrate) {
                    SectionLabel("BITRATE")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(320, 256, 192, 128).forEach { k -> NeonChip("${k}K", kbps == k) { kbps = k } }
                    }
                }
                if (format.isVideo) {
                    SectionLabel("MAX RESOLUTION")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2160 to "4K", 1440 to "1440P", 1080 to "1080P", 720 to "720P", 480 to "480P", 0 to "ANY").forEach { (h, label) ->
                            NeonChip(label, height == h) { height = h }
                        }
                    }
                }

                if (format.isVideo) {
                    Spacer(Modifier.height(6.dp))
                    ToggleRow(
                        title = "Embed subtitles",
                        subtitle = "Uses the site's subtitles, or auto-captions if that's all there is",
                        checked = subs,
                        onChange = { subs = it },
                    )
                    if (subs) {
                        NeonTextField(subLangs, { subLangs = it }, placeholder = "Languages, e.g. en.*,es")
                        Text(
                            "Comma-separated language codes. en.* means any English.",
                            color = TextLo,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Embed cover art & tags",
                    subtitle = if (format == DlFormat.WAV) "WAV can't hold tags" else "Title, artist and artwork go into the file",
                    checked = embed,
                    onChange = { embed = it },
                )

                SectionLabel("ADD TO")
                Text(
                    "✓ LIBRARY  ·  ${if (format.isVideo) "Movies" else "Music"}/${AppInfo.FOLDER}",
                    color = primary,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("NO PLAYLIST", targetId == null) { targetId = null }
                    playlists.forEach { p -> NeonChip(p.name.uppercase(), targetId == p.id) { targetId = p.id } }
                    NeonChip("+ NEW PLAYLIST", targetId == NEW_TARGET) { targetId = NEW_TARGET }
                }
                if (targetId == NEW_TARGET) {
                    Spacer(Modifier.height(10.dp))
                    NeonTextField(newName, { newName = it }, placeholder = "Playlist name (optional)")
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "Skip what I already have",
                    subtitle = "Doesn't re-download things you got before, and removes new copies of songs already in your library",
                    checked = skipExisting,
                    onChange = { skipExisting = it },
                )
                ToggleRow(
                    title = "Queue it up when done",
                    subtitle = "Adds the new tracks to what's playing",
                    checked = queue,
                    onChange = { queue = it },
                )

                Text(
                    (if (showAdvanced) "▾" else "▸") + " ADVANCED",
                    color = TextLo,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clickable { showAdvanced = !showAdvanced },
                )
                if (showAdvanced) {
                    Spacer(Modifier.height(8.dp))
                    NeonTextField(extra, { extra = it }, placeholder = "extra yt-dlp options", singleLine = false)
                    Text(
                        "Passed straight to yt-dlp, for example --embed-chapters or --sponsorblock-remove music_offtopic.",
                        color = TextLo,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(
                            if (urlOk) Modifier.neonPanel(primary, glow = 18.dp, fill = Color.Transparent)
                            else Modifier.neonPanel(TextLo.copy(alpha = 0.4f), glow = 0.dp)
                        )
                        .background(
                            if (urlOk) Brush.horizontalGradient(listOf(primary, secondary))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable(enabled = urlOk) {
                            val needsVideoPerm = format.isVideo && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
                            if (needsVideoPerm) videoPermission.launch(Manifest.permission.READ_MEDIA_VIDEO) else start()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "TRANSMIT ▶",
                        color = if (urlOk) Void else TextLo,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .neonPanel(if (urlOk) secondary else TextLo.copy(alpha = 0.4f), glow = if (urlOk) 10.dp else 0.dp)
                        .clickable(enabled = urlOk && !streaming) { stream() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (streaming) "TUNING IN…" else if (format.isVideo) "▷ STREAM VIDEO NOW" else "▷ STREAM AUDIO NOW",
                        color = if (urlOk) secondary else TextLo,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    )
                }
                Text(
                    streamError ?: "Streaming plays right away without saving. Quality may be lower than a download (YouTube streams are often 360p).",
                    color = if (streamError != null) DangerRed else TextLo,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Only download what you have the rights to. Details in Credits & Support ›",
                    color = TextLo,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable(onClick = onOpenCredits),
                )

                if (jobs.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("TRANSMISSIONS", Modifier.padding(top = 10.dp))
                        Spacer(Modifier.weight(1f))
                        if (jobs.any { !it.status.active }) {
                            Text(
                                "CLEAR DONE",
                                color = primary,
                                fontFamily = Mono,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp,
                                modifier = Modifier
                                    .padding(top = 18.dp)
                                    .clickable { UplinkManager.clearFinished() },
                            )
                        }
                    }
                    jobs.asReversed().forEach { JobCard(it) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun JobCard(job: DownloadJob) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val color = when (job.status) {
        JobStatus.FAILED -> DangerRed
        JobStatus.DONE -> primary
        JobStatus.CANCELLED -> TextLo
        else -> secondary
    }
    val pct = (job.overallProgress * 100).toInt()
    val statusText = when (job.status) {
        JobStatus.QUEUED -> "QUEUED"
        JobStatus.PREPARING -> "BOOTING"
        JobStatus.DOWNLOADING -> if (job.itemCount > 1) "RECEIVING ${job.item}/${job.itemCount}" else "RECEIVING $pct%"
        JobStatus.PROCESSING -> if (job.itemCount > 1) "PROCESSING ${job.item}/${job.itemCount}" else "PROCESSING"
        JobStatus.SAVING -> "DOCKING"
        JobStatus.DONE -> "COMPLETE"
        JobStatus.FAILED -> "FAILED"
        JobStatus.CANCELLED -> "CANCELLED"
    }

    Column(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .neonPanel(color.copy(alpha = 0.6f), glow = 0.dp)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    job.title ?: job.options.url,
                    color = TextHi,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${job.options.format.label} · $statusText",
                    color = color,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
            }
            if (job.status.active) {
                IconButton(onClick = { UplinkManager.cancel(job.id) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = TextLo)
                }
            }
        }
        if (job.status.active) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(primary.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(job.overallProgress)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(secondary, primary)))
                )
            }
        }
        job.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = TextLo, fontSize = 11.sp, fontFamily = Mono, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}
