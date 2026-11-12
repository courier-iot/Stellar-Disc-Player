package com.stellapp.disc.uplink

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.MusicRepository
import com.stellapp.disc.swap.SongMatcher
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

    private val _engine = MutableStateFlow(EngineState.OFF)
    /** Whether the yt-dlp engines are powered. They only run while Uplink is open or a job is in flight. */
    val engine: StateFlow<EngineState> = _engine.asStateFlow()
    @Volatile private var uplinkOpen = false

    private fun busy() = _jobs.value.any { it.status.active }

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
        savePending(app)
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (e: Exception) {
            // Android won't start a foreground service right now (app in the background, or the
            // daily background-download limit was reached). The job still runs while the app is
            // alive, and is saved so it resumes next time otherwise.
        }
        running[id] = scope.launch {
            runLock.withLock {
                if (find(id)?.status == JobStatus.QUEUED) process(app, id)
            }
            running.remove(id)
            savePending(app)
            // Downloads that outlived the Uplink screen: power down once the last one lands.
            powerDownIfIdle()
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

    // ---------- YouTube sign-in (cookies.txt) ----------

    fun hasCookies(context: Context): Boolean = CookieVault.has(context)

    /** See [CookieVault.verify]. */
    fun verifySignIn(context: Context): Boolean = CookieVault.verify(context)

    /** Copies a Netscape-format cookies.txt in. Returns an error message, or null when it worked. */
    suspend fun importCookies(context: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext "Couldn't read that file"
            val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            val looksRight = lines.any { it.split("\t").size >= 7 }
            if (!looksRight) return@withContext "That doesn't look like a cookies.txt file (Netscape format)"
            if (lines.none { it.contains("youtube.com") }) return@withContext "No YouTube cookies in that file. Export it while signed in to youtube.com"
            CookieVault.save(context, text)
            null
        } catch (e: Exception) {
            "Couldn't import: ${e.message ?: "unknown error"}"
        }
    }

    fun clearCookies(context: Context) {
        CookieVault.clear(context)
    }

    /** Saves cookies captured by the in-app sign-in screen. */
    fun saveCookies(context: Context, netscapeText: String) {
        CookieVault.save(context, netscapeText)
    }

    // ---------- engine options ----------

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE)

    /** The bundled QuickJS-NG runtime, if this build includes it. */
    fun jsRuntimePath(context: Context): String? {
        val dir = context.applicationContext.applicationInfo.nativeLibraryDir ?: return null
        return File(dir, "libqjs.so").takeIf { it.exists() }?.absolutePath
    }

    /** Fallback: let yt-dlp fetch its challenge-solver scripts from GitHub if its own copy is missing. */
    fun solverFromGithub(context: Context): Boolean = prefs(context).getBoolean("ejs_github", false)

    fun setSolverFromGithub(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("ejs_github", on).apply()
    }

    private val _solverMissing = MutableStateFlow(false)
    /** True once yt-dlp said it skipped the solver scripts (so the fallback might help). */
    val solverMissing: StateFlow<Boolean> = _solverMissing.asStateFlow()

    /** Only the options on a known-safe list are passed to yt-dlp (instead of just blocking risky ones). */
    fun strictOptions(context: Context): Boolean = prefs(context).getBoolean("ytdlp_strict", false)

    fun setStrictOptions(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("ytdlp_strict", on).apply()
        strictCache = on
    }

    @Volatile private var strictCache: Boolean? = null

    fun wifiOnly(context: Context): Boolean = prefs(context).getBoolean("dl_wifi_only", false)

    fun setWifiOnly(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("dl_wifi_only", on).apply()
    }

    private fun onUnmeteredNetwork(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun useNightly(context: Context): Boolean = prefs(context).getBoolean("ytdlp_nightly", false)

    fun setNightly(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("ytdlp_nightly", on).apply()
    }

    /** Options added to every yt-dlp run, set in Settings. */
    fun globalOptions(context: Context): String = prefs(context).getString("ytdlp_global", "") ?: ""

    fun setGlobalOptions(context: Context, text: String) {
        prefs(context).edit().putString("ytdlp_global", text).apply()
    }

    /** yt-dlp learned --js-runtimes in 2025.11.12. Older copies reject the option outright. */
    fun supportsJsRuntimes(version: String?): Boolean {
        if (version == null || version.contains("nightly", ignoreCase = true)) return version != null
        val parts = Regex("""(\d{4})\.(\d{1,2})\.(\d{1,2})""").find(version)?.groupValues ?: return false
        val y = parts[1].toInt()
        val m = parts[2].toInt()
        val d = parts[3].toInt()
        return y > 2025 || (y == 2025 && (m > 11 || (m == 11 && d >= 12)))
    }

    @Volatile private var autoUpdated = false

    /** The bundled yt-dlp is often older than the runtime support: update it once, automatically. */
    private suspend fun ensureModernYtDlp(context: Context) {
        if (autoUpdated || jsRuntimePath(context) == null || supportsJsRuntimes(_version.value)) return
        autoUpdated = true
        try {
            val app = context.applicationContext
            YoutubeDL.getInstance().updateYoutubeDL(app)
            _version.value = YoutubeDL.getInstance().version(app)
        } catch (_: Exception) {
        }
    }

    /** Sign-in, JavaScript runtime and always-on options, for every request. */
    /** Adds sign-in, runtime and always-on options. Returns the temporary cookies copy to delete afterwards. */
    private fun applyEngine(context: Context, request: YoutubeDLRequest): File? {
        strictCache = strictOptions(context)
        val cookies = CookieVault.tempPlainCopy(context)
        cookies?.let { request.addOption("--cookies", it.absolutePath) }
        jsRuntimePath(context)?.takeIf { supportsJsRuntimes(_version.value) }?.let { qjs ->
            request.addOption("--js-runtimes", "quickjs:$qjs")
            // The official yt-dlp build (which the app updates to) has the challenge-solver scripts
            // built in, so nothing is downloaded at run time. Fetching them from yt-dlp's GitHub is
            // only a fallback you can switch on in Settings.
            if (solverFromGithub(context)) request.addOption("--remote-components", "ejs:github")
        }
        parseExtra(globalOptions(context)).forEach { (k, v) -> if (v == null) request.addOption(k) else request.addOption(k, v) }
        return cookies
    }

    /** Options in [text] that would be dropped as unsafe. */
    fun unsafeOptions(context: Context, text: String): List<String> = YtdlpGuard.blockedIn(parseExtraRaw(text), strictOptions(context))

    /** Lets other features (like Swap) tell the library that new files arrived. */
    fun announce(batch: FinishedBatch) {
        _finished.tryEmit(batch)
    }

    // ---------- saved queue ----------

    private fun pendingFile(context: Context) = File(context.applicationContext.filesDir, "uplink_pending.json")
    @Volatile private var resumed = false

    /** Writes every download that hasn't finished yet, so they can resume after the app is closed. */
    private fun savePending(context: Context) {
        try {
            val arr = org.json.JSONArray()
            _jobs.value.filter { it.status.active }.forEach { arr.put(it.options.toJson()) }
            val f = pendingFile(context)
            if (arr.length() == 0) f.delete() else f.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    /** Re-queues downloads that were cut off last time. Returns how many. Runs once per app start. */
    fun resumePending(context: Context): Int {
        if (resumed) return 0
        resumed = true
        val f = pendingFile(context)
        if (!f.exists()) return 0
        val options = try {
            val arr = org.json.JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { downloadOptionsFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
        f.delete()
        options.forEach { enqueue(context, it) }
        return options.size
    }

    // ---------- engine power ----------

    /** Entering Uplink: bring the engines up (unpacks / checks yt-dlp + FFmpeg). */
    suspend fun spoolUp(context: Context): Boolean {
        uplinkOpen = true
        _engine.value = EngineState.SPOOLING
        val ok = ensureInit(context)
        _engine.value = if (ok) EngineState.ONLINE else EngineState.OFF
        return ok
    }

    /**
     * Leaving Uplink: shut the engines off so only the player is running. If downloads are still
     * going they keep the engines on until they finish. Returns how many are still running.
     */
    fun coolDown(): Int {
        uplinkOpen = false
        val left = _jobs.value.count { it.status.active }
        if (left > 0) {
            _engine.value = EngineState.HOLDING
        } else {
            _engine.value = EngineState.COOLING
            powerDownIfIdle()
        }
        return left
    }

    private fun powerDownIfIdle() {
        if (uplinkOpen || busy()) return
        initialized = false
        _engine.value = EngineState.OFF
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.status.active } }
    }

    /** Why the engines last failed to start, in plain words (null when they're fine). */
    @Volatile var initError: String? = null
        private set

    /** Memory page size in KB (4 on most phones, 16 on some newer ones). */
    fun pageSizeKb(): Long = try {
        android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) / 1024
    } catch (e: Throwable) {
        4
    }

    suspend fun ensureInit(context: Context): Boolean = initLock.withLock {
        if (initialized) return@withLock true
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                initialized = true
                initError = null
                _version.value = YoutubeDL.getInstance().version(app)
                if (_engine.value == EngineState.OFF) _engine.value = EngineState.ONLINE
                true
            } catch (e: Throwable) {
                // Native load failures are Errors, not Exceptions: catch everything so the player keeps working.
                initError = if (pageSizeKb() >= 16) {
                    "This phone uses 16 KB memory pages, which this build of the downloader doesn't support yet. " +
                        "Music playback is unaffected; downloads will work again once the downloader library is updated."
                } else {
                    "The downloader couldn't start: ${e.message?.lineSequence()?.firstOrNull()?.take(120) ?: e.javaClass.simpleName}"
                }
                false
            }
        }
    }

    /** Downloads the newest yt-dlp release. Sites change often, so this fixes most failures. */
    suspend fun updateYtDlp(context: Context): String = updateYtDlpInner(context).also { powerDownIfIdle() }

    private suspend fun updateYtDlpInner(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureInit(context)) return@withContext (initError ?: "The downloader couldn't start on this device.")
        try {
            val app = context.applicationContext
            if (useNightly(app)) {
                // yt-dlp updates itself to the newest nightly build.
                val request = YoutubeDLRequest(emptyList<String>())
                request.addOption("--update-to", "nightly")
                val out = YoutubeDL.getInstance().execute(request).out
                val line = out.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: "Done"
                _version.value = "nightly"
                "Nightly: $line"
            } else {
                val result = YoutubeDL.getInstance().updateYoutubeDL(app)?.toString() ?: ""
                _version.value = YoutubeDL.getInstance().version(app)
                if (result.contains("ALREADY", ignoreCase = true)) "Already up to date (${_version.value})"
                else "Updated to ${_version.value}"
            }
        } catch (e: Exception) {
            "Update failed: ${e.message?.lineSequence()?.firstOrNull()?.take(120) ?: "unknown error"}"
        }
    }

    /**
     * Asks yt-dlp for a direct media link so it can play right away without downloading.
     * Links like this expire after a few hours, so streams aren't kept in the saved queue.
     */
    suspend fun resolveStream(context: Context, url: String, video: Boolean): Result<StreamInfo> = withContext(Dispatchers.IO) {
        if (!ensureInit(context)) return@withContext Result.failure(IllegalStateException(initError ?: "The downloader couldn't start on this device."))
        ensureModernYtDlp(context)
        var cookies: File? = null
        try {
            val request = YoutubeDLRequest(url.trim())
            cookies = applyEngine(context, request)
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
        } finally {
            cookies?.delete()
        }
    }

    // ---------- the actual work ----------

    private suspend fun process(context: Context, id: String) {
        val o = find(id)?.options ?: return
        PlaylistStore.init(context)
        update(id) { it.copy(status = JobStatus.PREPARING, message = "Booting downloader… (first run takes a moment)") }

        if (!ensureInit(context)) {
            finish(context, id) { it.copy(status = JobStatus.FAILED, message = initError ?: "The downloader couldn't start on this device.") }
            return
        }

        val dir = File(context.cacheDir, "uplink/$id").apply {
            deleteRecursively()
            mkdirs()
        }
        update(id) { it.copy(message = "Checking yt-dlp is up to date…") }
        ensureModernYtDlp(context)
        val request = YoutubeDLRequest(o.url.trim())
        val cookies = applyEngine(context, request)
        buildArgs(o, dir).forEach { (key, value) ->
            if (value == null) request.addOption(key) else request.addOption(key, value)
        }

        if (o.skipExisting) request.addOption("--download-archive", ArchiveStore.prepare(context, o.format.isVideo))

        // Wi-Fi only: wait (without using mobile data) until an unmetered network is back.
        while (wifiOnly(context) && !onUnmeteredNetwork(context)) {
            if (find(id)?.status == JobStatus.CANCELLED) {
                cookies?.delete()
                dir.deleteRecursively()
                return
            }
            update(id) { it.copy(message = "Waiting for Wi-Fi… (Settings → Uplink engine → Wi-Fi only)") }
            kotlinx.coroutines.delay(5_000)
        }
        update(id) { it.copy(status = JobStatus.DOWNLOADING, message = "Opening channel…") }
        var lastError: String? = null
        var failure: String? = null
        var alreadyHave = 0
        try {
            YoutubeDL.getInstance().execute(request, processId = id) { progress, _, line ->
                if (line.startsWith("ERROR:")) lastError = line.removePrefix("ERROR:").trim()
                if (line.contains("already been recorded in the archive")) alreadyHave++
                handleLine(id, progress, line)
            }
        } catch (e: Exception) {
            failure = lastError ?: e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim() ?: "Download failed"
        } finally {
            cookies?.delete()
        }

        if (find(id)?.status == JobStatus.CANCELLED) {
            dir.deleteRecursively()
            return
        }

        val files = collectMedia(dir)
        if (files.isEmpty()) {
            dir.deleteRecursively()
            if (alreadyHave > 0) {
                finish(context, id) { it.copy(status = JobStatus.DONE, progress = 1f, message = "Already in your library · skipped $alreadyHave") }
            } else {
                finish(context, id) { it.copy(status = JobStatus.FAILED, message = failure ?: lastError ?: "Nothing was downloaded.") }
            }
            return
        }

        update(id) { it.copy(status = JobStatus.SAVING, progress = 1f, message = "Docking ${files.size} file(s) into your library…") }
        LinkStore.init(context)
        val ids = ArrayList<Long>()
        val archiveKeys = HashMap<Long, String>()
        for (f in files) {
            val savedId = MediaSaver.save(context, f, o.format.isVideo) ?: continue
            ids += savedId
            val info = readInfo(f)
            // Remember the source link so the track can be shared as a link later.
            val link = info?.first ?: if (!o.wholePlaylist || files.size == 1) o.url.trim() else null
            link?.let { LinkStore.put(savedId, it) }
            // Remember it for the download archive, so it's skipped next time.
            info?.second?.let {
                archiveKeys[savedId] = it
                ArchiveStore.put(context, savedId, it, o.format.isVideo)
            }
        }
        dir.deleteRecursively()

        // Near-identical copies of songs you already had: remove the new one, keep yours.
        val replaced = HashMap<Long, Long>()
        if (o.skipExisting && ids.isNotEmpty()) {
            update(id) { it.copy(message = "Checking for duplicates…") }
            val library = try { MusicRepository.loadSongs(context, allVideos = false) } catch (e: Exception) { emptyList() }
            val fresh = ids.toSet()
            library.filter { it.id in fresh }.forEach { new ->
                val twin = library.firstOrNull { it.id !in fresh && it.isVideo == new.isVideo && SongMatcher.isSameSong(it, new) }
                if (twin != null) {
                    try {
                        context.contentResolver.delete(new.uri, null, null)
                        replaced[new.id] = twin.id
                        // Your copy now stands in for that link, so it's skipped next time.
                        archiveKeys[new.id]?.let { key -> ArchiveStore.put(context, twin.id, key, o.format.isVideo) }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        val kept = ids.filter { it !in replaced }
        val forLists = ids.map { replaced[it] ?: it }.distinct()

        val playlistId = when (val t = o.target) {
            PlaylistTarget.None -> null
            is PlaylistTarget.Existing -> t.id
            is PlaylistTarget.New -> PlaylistStore.create(t.name)
        }
        if (playlistId != null && forLists.isNotEmpty()) PlaylistStore.addSongs(playlistId, forLists)

        val where = if (o.format.isVideo) "Movies" else "Music"
        val partial = buildString {
            if (replaced.isNotEmpty()) append(" · removed ${replaced.size} duplicate(s) you already had")
            if (alreadyHave > 0) append(" · skipped $alreadyHave already in library")
            if (failure != null) append(" · some items failed: $failure")
        }
        finish(context, id) {
            if (ids.isEmpty()) {
                it.copy(status = JobStatus.FAILED, message = "Downloaded, but couldn't save to $where/${com.stellapp.disc.AppInfo.FOLDER}.")
            } else {
                it.copy(
                    status = JobStatus.DONE,
                    savedIds = kept,
                    item = it.itemCount,
                    message = "Saved ${kept.size} to $where/${com.stellapp.disc.AppInfo.FOLDER}$partial",
                )
            }
        }
        if (ids.isNotEmpty()) _finished.tryEmit(FinishedBatch(forLists, o.queueWhenDone))
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
    private fun parseExtra(s: String): List<Pair<String, String?>> = YtdlpGuard.filter(parseExtraRaw(s), strictCache ?: false)

    private fun parseExtraRaw(s: String): List<Pair<String, String?>> {
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
        if (line.contains("challenge solver script", ignoreCase = true) && line.contains("skipped", ignoreCase = true)) {
            _solverMissing.value = true
        }
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

    /** From yt-dlp's .info.json: (source link, download-archive key like "youtube abc123"). */
    private fun readInfo(media: File): Pair<String?, String?>? = try {
        val info = File(media.parentFile, media.nameWithoutExtension + ".info.json")
        if (!info.exists()) null
        else JSONObject(info.readText()).let { o ->
            val link = o.optString("webpage_url").ifBlank { o.optString("original_url") }.takeIf { it.startsWith("http") }
            val extractor = o.optString("extractor_key").ifBlank { o.optString("extractor") }.lowercase()
            val vid = o.optString("id")
            link to (if (extractor.isNotBlank() && vid.isNotBlank()) "$extractor $vid" else null)
        }
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
