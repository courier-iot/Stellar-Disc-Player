package com.stellapp.disc.uplink

import org.json.JSONObject

/**
 * @param codec value for yt-dlp's --audio-format (audio) or the container (video)
 * @param bitrate whether the bitrate picker applies
 * @param embedArt whether cover art can be embedded reliably
 */
enum class DlFormat(
    val label: String,
    val isVideo: Boolean,
    val codec: String,
    val bitrate: Boolean,
    val embedArt: Boolean,
    val hint: String,
) {
    MP3("MP3", false, "mp3", true, true, "Plays everywhere. Re-encoded, so pick a high bitrate."),
    M4A("M4A", false, "m4a", true, true, "AAC audio. Often the original on YouTube, so no quality lost."),
    OPUS("OPUS", false, "opus", true, false, "Small files, great quality. Usually the original on YouTube."),
    ORIGINAL("ORIGINAL", false, "best", false, false, "Best audio stream exactly as the site serves it."),
    MP4("MP4", true, "mp4", false, true, "H.264 video in MP4. Plays on almost anything."),
    MKV("MKV · BEST", true, "mkv", false, true, "Highest quality streams (VP9/AV1 + Opus) in MKV. Older phones may struggle."),
}

/** Autopilot's three quality levels. */
enum class SimpleQuality(val label: String) { BEST("★ BEST"), BALANCED("◆ BALANCED"), SAVER("▽ DATA SAVER") }

/** What Autopilot actually sends to the engine for a choice. */
data class SimplePreset(val format: DlFormat, val kbps: Int, val maxHeight: Int, val blurb: String)

fun simplePreset(video: Boolean, q: SimpleQuality): SimplePreset = if (!video) {
    when (q) {
        SimpleQuality.BEST -> SimplePreset(DlFormat.M4A, 320, 0, "The original signal, nothing lost. About 4–5 MB a song.")
        SimpleQuality.BALANCED -> SimplePreset(DlFormat.MP3, 192, 0, "Plays on anything, even old car stereos. About 6 MB a song.")
        SimpleQuality.SAVER -> SimplePreset(DlFormat.MP3, 96, 0, "Light on storage and data. About 3 MB a song.")
    }
} else {
    when (q) {
        SimpleQuality.BEST -> SimplePreset(DlFormat.MP4, 0, 1080, "Full HD (1080p). Roughly 100–200 MB per 10 minutes.")
        SimpleQuality.BALANCED -> SimplePreset(DlFormat.MP4, 0, 720, "HD (720p). Roughly 50–90 MB per 10 minutes.")
        SimpleQuality.SAVER -> SimplePreset(DlFormat.MP4, 0, 480, "480p. Roughly 25–45 MB per 10 minutes.")
    }
}

sealed interface PlaylistTarget {
    data object None : PlaylistTarget
    data class Existing(val id: String) : PlaylistTarget
    data class New(val name: String) : PlaylistTarget
}

data class DownloadOptions(
    val url: String,
    val format: DlFormat,
    val audioKbps: Int,
    val maxHeight: Int,
    val wholePlaylist: Boolean,
    val embed: Boolean,
    val target: PlaylistTarget,
    val queueWhenDone: Boolean,
    val extraArgs: String,
    /** Embed subtitles into video downloads. */
    val subtitles: Boolean = false,
    val subLangs: String = "en.*",
    /** Skip things already downloaded, and remove near-identical copies of songs you have. */
    val skipExisting: Boolean = true,
)

/** A direct, temporary media link resolved by yt-dlp for streaming without downloading. */
data class StreamInfo(
    val url: String,
    val page: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnail: String?,
    val isVideo: Boolean,
    val mime: String?,
)

/** Remembered defaults for the Uplink screen. */
data class UplinkPrefs(
    val format: DlFormat = DlFormat.MP3,
    val audioKbps: Int = 320,
    val maxHeight: Int = 1080,
    val wholePlaylist: Boolean = true,
    val embed: Boolean = true,
    val queueWhenDone: Boolean = false,
    val subtitles: Boolean = true,
    val subLangs: String = "en.*",
    val skipExisting: Boolean = true,
    /** Autopilot (simple) or Manual control. */
    val simpleMode: Boolean = true,
    val simpleVideo: Boolean = false,
    val simpleQuality: SimpleQuality = SimpleQuality.BEST,
)

enum class JobStatus {
    QUEUED, PREPARING, DOWNLOADING, PROCESSING, SAVING, DONE, FAILED, CANCELLED;

    val active: Boolean get() = this == QUEUED || this == PREPARING || this == DOWNLOADING || this == PROCESSING || this == SAVING
}

data class DownloadJob(
    val id: String,
    val options: DownloadOptions,
    val status: JobStatus = JobStatus.QUEUED,
    /** 0..1 progress of the current item. */
    val progress: Float = 0f,
    val item: Int = 1,
    val itemCount: Int = 1,
    val title: String? = null,
    val message: String? = null,
    val savedIds: List<Long> = emptyList(),
) {
    val overallProgress: Float
        get() = if (itemCount > 1) (((item - 1).coerceAtLeast(0) + progress) / itemCount).coerceIn(0f, 1f)
        else progress.coerceIn(0f, 1f)
}

data class FinishedBatch(val songIds: List<Long>, val queue: Boolean)

/** Power state of the yt-dlp / FFmpeg engines behind Uplink. */
enum class EngineState { OFF, SPOOLING, ONLINE, COOLING, HOLDING }

/** Saved form of a download, so the queue survives the app being closed. */
fun DownloadOptions.toJson(): JSONObject = JSONObject()
    .put("url", url)
    .put("format", format.name)
    .put("kbps", audioKbps)
    .put("height", maxHeight)
    .put("playlist", wholePlaylist)
    .put("embed", embed)
    .put("target", when (val t = target) {
        PlaylistTarget.None -> "none"
        is PlaylistTarget.Existing -> "id:" + t.id
        is PlaylistTarget.New -> "new:" + t.name
    })
    .put("queue", queueWhenDone)
    .put("extra", extraArgs)
    .put("subs", subtitles)
    .put("subLangs", subLangs)
    .put("skip", skipExisting)

fun downloadOptionsFromJson(o: JSONObject): DownloadOptions? {
    val url = o.optString("url").ifBlank { return null }
    val target = o.optString("target", "none").let {
        when {
            it.startsWith("id:") -> PlaylistTarget.Existing(it.removePrefix("id:"))
            it.startsWith("new:") -> PlaylistTarget.New(it.removePrefix("new:"))
            else -> PlaylistTarget.None
        }
    }
    return DownloadOptions(
        url = url,
        format = runCatching { DlFormat.valueOf(o.optString("format")) }.getOrDefault(DlFormat.MP3),
        audioKbps = o.optInt("kbps", 320),
        maxHeight = o.optInt("height", 1080),
        wholePlaylist = o.optBoolean("playlist", true),
        embed = o.optBoolean("embed", true),
        target = target,
        queueWhenDone = o.optBoolean("queue", false),
        extraArgs = o.optString("extra", ""),
        subtitles = o.optBoolean("subs", false),
        subLangs = o.optString("subLangs", "en.*"),
        skipExisting = o.optBoolean("skip", true),
    )
}
