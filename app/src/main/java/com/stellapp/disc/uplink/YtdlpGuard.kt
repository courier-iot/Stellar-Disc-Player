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

    fun filter(args: List<Pair<String, String?>>): List<Pair<String, String?>> = args.filterNot { isBlocked(it.first) }

    fun blockedIn(args: List<Pair<String, String?>>): List<String> = args.map { it.first }.filter { isBlocked(it) }.distinct()
}
