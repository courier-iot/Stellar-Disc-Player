package com.stellapp.disc.uplink

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.PlaylistStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs yt-dlp downloads one at a time, then moves the results into the music library
 * and (optionally) a playlist. Lives for the whole app process.
 */
object UplinkManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runLock = Mutex()
    private val initLock = Mutex()
    private val running = ConcurrentHashMap<String, Job>()
    @Volatile private var initialized = false

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private val _finished = MutableSharedFlow<FinishedBatch>(extraBufferCapacity = 16)
    val finished: SharedFlow<FinishedBatch> = _finished.asSharedFlow()

    private val MEDIA_EXT = setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav", "webm", "mka", "mp4", "mkv", "mov", "m4v")
    private val itemRe = Regex("""Downloading item (\d+) of (\d+)""")
    private val destRe = Regex("""^\[(?:download|ExtractAudio)] Destination: (.+)$""")
    private val mergeRe = Regex("""^\[Merger] Merging formats into "(.+)"$""")
    private val indexRe = Regex("""^(\d+) - """)

    // ---------- public API ----------

    fun enqueue(context: Context, options: DownloadOptions) {
        val app = context.applicationContext
        val id = "dl" + System.currentTimeMillis().toString(36) + (100..999).random()
        _jobs.update { it + DownloadJob(id = id, options = options, message = "Waiting in the queue…") }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        running[id] = scope.launch {
            runLock.withLock {
                if (find(id)?.status == JobStatus.QUEUED) process(app, id)
            }
            running.remove(id)
        }
    }

    fun cancel(id: String) {
        val job = find(id) ?: return
        if (!job.status.active) return
        update(id) { it.copy(status = JobStatus.CANCELLED, message = "Cancelled") }
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (_: Exception) {
        }
    }

    /** Lets other features (like Swap) tell the library that new files arrived. */
    fun announce(batch: FinishedBatch) {
        _finished.tryEmit(batch)
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.status.active } }
    }

    suspend fun ensureInit(context: Context): Boolean = initLock.withLock {
        if (initialized) return@withLock true
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                initialized = true
                _version.value = YoutubeDL.getInstance().version(app)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Downloads the newest yt-dlp release. Sites change often, so this fixes most failures. */
    suspend fun updateYtDlp(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureInit(context)) return@withContext "The downloader couldn't start on this device."
        try {
            val app = context.applicationContext
            val result = YoutubeDL.getInstance().updateYoutubeDL(app)?.toString() ?: ""
            _version.value = YoutubeDL.getInstance().version(app)
            if (result.contains("ALREADY", ignoreCase = true)) "Already up to date (${_version.value})"
            else "Updated to ${_version.value}"
        } catch (e: Exception) {
            "Update failed: ${e.message?.lineSequence()?.firstOrNull()?.take(120) ?: "unknown error"}"
        }
    }

    /**
     * Asks yt-dlp for a direct media link so it can play right away without downloading.
     * Links like this expire after a few hours, so streams aren't kept in the saved queue.
     */
    suspend fun resolveStream(context: Context, url: String, video: Boolean): Result<StreamInfo> = withContext(Dispatchers.IO) {
        if (!ensureInit(context)) return@withContext Result.failure(IllegalStateException("The downloader couldn't start on this device."))
        try {
            val request = YoutubeDLRequest(url.trim())
            request.addOption("--dump-json")
            request.addOption("--no-playlist")
            request.addOption(
                "-f",
                if (video) "b[ext=mp4][vcodec!=none][acodec!=none]/b[vcodec!=none][acodec!=none]/b" else "ba[ext=m4a]/ba/b",
            )
            val response = YoutubeDL.getInstance().execute(request)
            val line = response.out.lineSequence().firstOrNull { it.trim().startsWith("{") }
                ?: throw IllegalStateException("No stream found for that link")
            val o = JSONObject(line)
            val direct = o.optString("url").ifBlank { throw IllegalStateException("That site doesn't offer a direct stream. Try downloading instead.") }
            val protocol = o.optString("protocol")
            val mime = when {
                protocol.contains("m3u8") -> "application/x-mpegURL"
                protocol.contains("dash") -> "application/dash+xml"
                else -> null
            }
            val artist = o.optString("artist").ifBlank { o.optString("uploader") }.ifBlank { o.optString("channel") }.ifBlank { "Stream" }
            Result.success(
                StreamInfo(
                    url = direct,
                    page = o.optString("webpage_url").ifBlank { url.trim() },
                    title = o.optString("title").ifBlank { "Stream" },
                    artist = artist,
                    durationMs = (o.optDouble("duration", 0.0) * 1000).toLong(),
                    thumbnail = o.optString("thumbnail").ifBlank { null },
                    isVideo = video && o.optString("vcodec") != "none",
                    mime = mime,
                )
            )
        } catch (e: Exception) {
            val msg = e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.removePrefix("ERROR:")?.trim()
            Result.failure(IllegalStateException(msg ?: "Couldn't open that stream"))
        }
    }

    // ---------- the actual work ----------

    private suspend fun process(context: Context, id: String) {
        val o = find(id)?.options ?: return
        PlaylistStore.init(context)
        update(id) { it.copy(status = JobStatus.PREPARING, message = "Booting downloader… (first run takes a moment)") }

        if (!ensureInit(context)) {
            finish(context, id) { it.copy(status = JobStatus.FAILED, message = "The downloader couldn't start on this device.") }
            return
        }

        val dir = File(context.cacheDir, "uplink/$id").apply {
            deleteRecursively()
            mkdirs()
        }
        val request = YoutubeDLRequest(o.url.trim())
        buildArgs(o, dir).forEach { (key, value) ->
            if (value == null) request.addOption(key) else request.addOption(key, value)
        }

        update(id) { it.copy(status = JobStatus.DOWNLOADING, message = "Opening channel…") }
        var lastError: String? = null
        var failure: String? = null
        try {
            YoutubeDL.getInstance().execute(request, processId = id) { progress, _, line ->
                if (line.startsWith("ERROR:")) lastError = line.removePrefix("ERROR:").trim()
                handleLine(id, progress, line)
            }
        } catch (e: Exception) {
            failure = lastError ?: e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim() ?: "Download failed"
        }

        if (find(id)?.status == JobStatus.CANCELLED) {
            dir.deleteRecursively()
            return
        }

        val files = collectMedia(dir)
        if (files.isEmpty()) {
            dir.deleteRecursively()
            finish(context, id) { it.copy(status = JobStatus.FAILED, message = failure ?: lastError ?: "Nothing was downloaded.") }
            return
        }

        update(id) { it.copy(status = JobStatus.SAVING, progress = 1f, message = "Docking ${files.size} file(s) into your library…") }
        LinkStore.init(context)
        val ids = ArrayList<Long>()
        for (f in files) {
            val savedId = MediaSaver.save(context, f, o.format.isVideo) ?: continue
            ids += savedId
            // Remember the source link so the track can be shared as a link later.
            val link = readSourceLink(f) ?: if (!o.wholePlaylist || files.size == 1) o.url.trim() else null
            link?.let { LinkStore.put(savedId, it) }
        }
        dir.deleteRecursively()

        val playlistId = when (val t = o.target) {
            PlaylistTarget.None -> null
            is PlaylistTarget.Existing -> t.id
            is PlaylistTarget.New -> PlaylistStore.create(t.name)
        }
        if (playlistId != null && ids.isNotEmpty()) PlaylistStore.addSongs(playlistId, ids)

        val where = if (o.format.isVideo) "Movies" else "Music"
        val partial = if (failure != null) " · some items failed: $failure" else ""
        finish(context, id) {
            if (ids.isEmpty()) {
                it.copy(status = JobStatus.FAILED, message = "Downloaded, but couldn't save to $where/${com.stellapp.disc.AppInfo.FOLDER}.")
            } else {
                it.copy(
                    status = JobStatus.DONE,
                    savedIds = ids,
                    item = it.itemCount,
                    message = "Saved ${ids.size} to $where/${com.stellapp.disc.AppInfo.FOLDER}$partial",
                )
            }
        }
        if (ids.isNotEmpty()) _finished.tryEmit(FinishedBatch(ids, o.queueWhenDone))
    }

    private fun buildArgs(o: DownloadOptions, dir: File): List<Pair<String, String?>> {
        val a = mutableListOf<Pair<String, String?>>()
        a += "-o" to "${dir.absolutePath}/%(playlist_index&{} - |)s%(title).120B.%(ext)s"
        a += (if (o.wholePlaylist) "--yes-playlist" else "--no-playlist") to null
        if (o.wholePlaylist) a += "--ignore-errors" to null // skip private/removed items instead of stopping
        a += "--no-mtime" to null
        a += "--write-info-json" to null // used to remember each track's source link
        a += "--no-write-playlist-metafiles" to null

        val f = o.format
        val h = if (o.maxHeight > 0) "[height<=${o.maxHeight}]" else ""
        when {
            !f.isVideo -> {
                a += "-f" to "ba/b"
                a += "--extract-audio" to null
                a += "--audio-format" to f.codec
                if (f.bitrate) a += "--audio-quality" to "${o.audioKbps}K"
            }
            f == DlFormat.MP4 -> {
                a += "-f" to "bv*${h}[vcodec^=avc1]+ba[ext=m4a]/bv*${h}[ext=mp4]+ba[ext=m4a]/b${h}[ext=mp4]/bv*${h}+ba/b${h}/b"
                a += "--merge-output-format" to "mp4"
                a += "--remux-video" to "mp4"
            }
            else -> {
                a += "-f" to "bv*${h}+ba/b${h}/b"
                a += "--merge-output-format" to "mkv"
                a += "--remux-video" to "mkv"
            }
        }
        if (f.isVideo && o.subtitles) {
            a += "--write-subs" to null
            a += "--write-auto-subs" to null
            a += "--sub-langs" to o.subLangs.ifBlank { "en.*" }
            a += "--embed-subs" to null
        }
        if (o.embed && f != DlFormat.WAV) {
            a += "--embed-metadata" to null
            if (f.embedArt) {
                a += "--embed-thumbnail" to null
                a += "--convert-thumbnails" to "jpg"
            }
        }
        a += parseExtra(o.extraArgs)
        return a
    }

    /** Turns "--flag value --other" into option pairs. Quotes are respected. */
    private fun parseExtra(s: String): List<Pair<String, String?>> {
        val tokens = Regex(""""([^"]*)"|'([^']*)'|(\S+)""").findAll(s).map { m ->
            m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
        }.toList()
        val out = mutableListOf<Pair<String, String?>>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.startsWith("-")) {
                val next = tokens.getOrNull(i + 1)
                if (next != null && !next.startsWith("-")) {
                    out += t to next
                    i += 2
                } else {
                    out += t to null
                    i++
                }
            } else {
                i++
            }
        }
        return out
    }

    private fun handleLine(id: String, progress: Float, line: String) {
        itemRe.find(line)?.let { m ->
            val item = m.groupValues[1].toIntOrNull() ?: 1
            val count = m.groupValues[2].toIntOrNull() ?: 1
            update(id) { it.copy(item = item, itemCount = count, progress = 0f) }
        }
        val path = destRe.find(line)?.groupValues?.get(1) ?: mergeRe.find(line)?.groupValues?.get(1)
        if (path != null) {
            val name = File(path).nameWithoutExtension
                .replace(Regex("""\.f\d+$"""), "")
                .replace(indexRe, "")
            update(id) { it.copy(title = name) }
        }
        val status = when {
            line.startsWith("[ExtractAudio]") || line.startsWith("[Merger]") || line.startsWith("[VideoRemuxer]") ||
                line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]") || line.startsWith("[ThumbnailsConvertor]") -> JobStatus.PROCESSING
            line.startsWith("[download]") -> JobStatus.DOWNLOADING
            else -> null
        }
        update(id) { j ->
            if (!j.status.active || j.status == JobStatus.SAVING) j
            else j.copy(
                status = status ?: j.status,
                progress = if (progress >= 0f) (progress / 100f).coerceIn(0f, 1f) else j.progress,
                message = line.trim().take(140),
            )
        }
    }

    private fun readSourceLink(media: File): String? = try {
        val info = File(media.parentFile, media.nameWithoutExtension + ".info.json")
        if (!info.exists()) null
        else JSONObject(info.readText()).let { o ->
            o.optString("webpage_url").ifBlank { o.optString("original_url") }
        }.takeIf { it.startsWith("http") }
    } catch (e: Exception) {
        null
    }

    private fun collectMedia(dir: File): List<File> =
        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in MEDIA_EXT }
            .sortedWith(compareBy<File>({ indexRe.find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }, { it.name.lowercase() }))
            .toList()

    private fun finish(context: Context, id: String, change: (DownloadJob) -> DownloadJob) {
        update(id, change)
        find(id)?.let { Notifications.done(context, it) }
    }

    private fun update(id: String, change: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun find(id: String): DownloadJob? = _jobs.value.firstOrNull { it.id == id }
}
