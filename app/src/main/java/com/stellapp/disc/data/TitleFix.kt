package com.stellapp.disc.data

/**
 * Cleans up titles that came from file names instead of tags, e.g.
 * "3 - Elley Duhe - MONEY ON THE DASH" with an unknown artist becomes
 * title "MONEY ON THE DASH", artist "Elley Duhe".
 */
object TitleFix {
    private val indexPrefix = Regex("""^\s*\d{1,4}\s*[-.]\s+""")
    private val topic = Regex("""\s+-\s+Topic$""", RegexOption.IGNORE_CASE)
    private val unknownArtists = setOf("unknown artist", "<unknown>", "video", "")

    fun clean(title: String, artist: String, fromApp: Boolean): Pair<String, String> {
        var t = title.trim()
        var a = artist.trim().replace(topic, "")
        val artistUnknown = a.lowercase() in unknownArtists
        // Playlist position ("3 - ") from our own downloads, or any file with no artist tag.
        if (fromApp || artistUnknown) t = t.replace(indexPrefix, "")
        if (artistUnknown) {
            val split = t.indexOf(" - ")
            if (split > 0 && split < t.length - 3) {
                a = t.substring(0, split).trim()
                t = t.substring(split + 3).trim()
            }
        }
        return t.ifBlank { title } to a.ifBlank { artist }
    }

    fun apply(s: Song): Song {
        val (t, a) = clean(s.title, s.artist, s.inAppFolder)
        return if (t == s.title && a == s.artist) s else s.copy(title = t, artist = a)
    }
}
