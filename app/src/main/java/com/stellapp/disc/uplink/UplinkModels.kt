package com.stellapp.disc.uplink

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
    FLAC("FLAC", false, "flac", false, false, "Lossless container. Only truly lossless if the source is."),
    WAV("WAV", false, "wav", false, false, "Uncompressed and huge. No tags or cover art."),
    ORIGINAL("ORIGINAL", false, "best", false, false, "Best audio stream exactly as the site serves it."),
    MP4("MP4", true, "mp4", false, true, "H.264 video in MP4. Plays on almost anything."),
    MKV("MKV · BEST", true, "mkv", false, true, "Highest quality streams (VP9/AV1 + Opus) in MKV. Older phones may struggle."),
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
