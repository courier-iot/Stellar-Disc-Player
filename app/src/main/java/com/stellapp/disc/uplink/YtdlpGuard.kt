package com.stellapp.disc.uplink

/**
 * yt-dlp options that can run programs or load code. They're dropped from the "always-on"
 * options and the per-download advanced box, so a pasted tip can't turn into a security hole.
 */
object YtdlpGuard {
    private val blocked = listOf(
        "--exec", "--exec-before-download", "--netrc-cmd", "--plugin-dirs",
        "--config-location", "--config-locations", "--batch-file",
        "--external-downloader", "--downloader", "--use-postprocessor",
    )
    private val blockedShort = setOf("-a")

    /** True if [option] (e.g. "--exec" or "--exec=rm") is one of the blocked ones, or a shortening of one. */
    fun isBlocked(option: String): Boolean {
        val name = option.substringBefore("=").trim().lowercase()
        if (name in blockedShort) return true
        if (!name.startsWith("--")) return false
        if (blocked.any { name == it || name.startsWith("$it-") }) return true
        // yt-dlp accepts shortened long options, like "--exe" for "--exec".
        return name.length >= 5 && blocked.any { it.startsWith(name) }
    }

    /** Strict mode: only these well-known options get through (format, quality, metadata, filters, networking). */
    private val allowed = setOf(
        "-f", "--format", "-S", "--format-sort", "-x", "--extract-audio", "--audio-format", "--audio-quality",
        "--remux-video", "--recode-video", "--merge-output-format", "--prefer-free-formats",
        "--embed-thumbnail", "--no-embed-thumbnail", "--embed-metadata", "--no-embed-metadata", "--embed-chapters",
        "--embed-subs", "--write-subs", "--write-auto-subs", "--sub-langs", "--sub-format", "--convert-subs",
        "--convert-thumbnails", "--parse-metadata", "--replace-in-metadata", "--split-chapters",
        "--sponsorblock-remove", "--sponsorblock-mark", "--no-sponsorblock",
        "--no-playlist", "--yes-playlist", "-I", "--playlist-items", "--playlist-reverse", "--playlist-random",
        "--match-filters", "--match-filter", "--break-match-filters", "--download-sections", "--age-limit",
        "--min-filesize", "--max-filesize", "--date", "--datebefore", "--dateafter", "--max-downloads",
        "-r", "--limit-rate", "--retries", "--fragment-retries", "--extractor-retries", "-N", "--concurrent-fragments",
        "--socket-timeout", "--sleep-interval", "--min-sleep-interval", "--max-sleep-interval", "--sleep-requests",
        "--force-ipv4", "-4", "--force-ipv6", "-6", "--geo-bypass", "--xff", "--extractor-args",
        "--user-agent", "--referer", "--add-headers", "--add-header",
        "--no-mtime", "--restrict-filenames", "--windows-filenames", "--trim-filenames",
        "--no-part", "--no-continue", "--throttled-rate", "--http-chunk-size",
    )

    /** In strict mode, true when [option] isn't on the known-safe list. */
    fun notAllowed(option: String): Boolean = option.substringBefore("=").trim().lowercase().let { name ->
        name !in allowed && allowed.none { it.equals(name, ignoreCase = true) }
    }

    fun filter(args: List<Pair<String, String?>>, strict: Boolean = false): List<Pair<String, String?>> =
        args.filterNot { isBlocked(it.first) || (strict && notAllowed(it.first)) }

    fun blockedIn(args: List<Pair<String, String?>>, strict: Boolean = false): List<String> =
        args.map { it.first }.filter { isBlocked(it) || (strict && notAllowed(it)) }.distinct()
}
