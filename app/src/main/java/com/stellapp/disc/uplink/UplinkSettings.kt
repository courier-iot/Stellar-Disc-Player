package com.stellapp.disc.uplink

import android.content.SharedPreferences

/** The Uplink screen's remembered choices (format, bitrate, playlist mode…). */
class UplinkSettings(private val prefs: SharedPreferences) {

    fun load(): UplinkPrefs = UplinkPrefs(
        format = runCatching { DlFormat.valueOf(prefs.getString(KEY_DL_FORMAT, DlFormat.MP3.name)!!) }.getOrDefault(DlFormat.MP3),
        audioKbps = prefs.getInt(KEY_DL_KBPS, 320),
        maxHeight = prefs.getInt(KEY_DL_HEIGHT, 1080),
        wholePlaylist = prefs.getBoolean(KEY_DL_PLAYLIST, true),
        embed = prefs.getBoolean(KEY_DL_EMBED, true),
        queueWhenDone = prefs.getBoolean(KEY_DL_QUEUE, false),
        subtitles = prefs.getBoolean(KEY_DL_SUBS, true),
        subLangs = prefs.getString(KEY_DL_SUBLANGS, "en.*") ?: "en.*",
        skipExisting = prefs.getBoolean(KEY_DL_SKIP, true),
        simpleMode = prefs.getBoolean(KEY_SIMPLE, true),
        simpleVideo = prefs.getBoolean(KEY_SIMPLE_VIDEO, false),
        simpleQuality = runCatching { SimpleQuality.valueOf(prefs.getString(KEY_SIMPLE_Q, null) ?: "BEST") }.getOrDefault(SimpleQuality.BEST),
    )

    /** Remembers the Autopilot / Manual choices. */
    fun saveSimple(simpleMode: Boolean, video: Boolean, quality: SimpleQuality) {
        prefs.edit()
            .putBoolean(KEY_SIMPLE, simpleMode)
            .putBoolean(KEY_SIMPLE_VIDEO, video)
            .putString(KEY_SIMPLE_Q, quality.name)
            .apply()
    }

    fun save(options: DownloadOptions) {
        prefs.edit()
            .putString(KEY_DL_FORMAT, options.format.name)
            .putInt(KEY_DL_KBPS, options.audioKbps)
            .putInt(KEY_DL_HEIGHT, options.maxHeight)
            .putBoolean(KEY_DL_PLAYLIST, options.wholePlaylist)
            .putBoolean(KEY_DL_EMBED, options.embed)
            .putBoolean(KEY_DL_QUEUE, options.queueWhenDone)
            .putBoolean(KEY_DL_SUBS, options.subtitles)
            .putString(KEY_DL_SUBLANGS, options.subLangs)
            .putBoolean(KEY_DL_SKIP, options.skipExisting)
            .apply()
    }

    private companion object {
        const val KEY_DL_FORMAT = "dl_format"
        const val KEY_DL_KBPS = "dl_kbps"
        const val KEY_DL_HEIGHT = "dl_height"
        const val KEY_DL_PLAYLIST = "dl_playlist"
        const val KEY_DL_EMBED = "dl_embed"
        const val KEY_DL_QUEUE = "dl_queue"
        const val KEY_DL_SUBS = "dl_subs"
        const val KEY_DL_SUBLANGS = "dl_sublangs"
        const val KEY_DL_SKIP = "dl_skip_existing"
        const val KEY_SIMPLE = "dl_simple_mode"
        const val KEY_SIMPLE_VIDEO = "dl_simple_video"
        const val KEY_SIMPLE_Q = "dl_simple_quality"
    }
}
