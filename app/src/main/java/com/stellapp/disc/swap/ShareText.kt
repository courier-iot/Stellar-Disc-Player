package com.stellapp.disc.swap

import com.stellapp.disc.data.Song

/**
 * The link-list format. Human readable, so it still makes sense pasted into a chat:
 *
 *   Stellar Disc playlist: Night Drive
 *   Midnight City — M83 | https://youtu.be/dX3k_QDnzHE
 */
object ShareText {
    const val HEADER = "Stellar Disc playlist:"
    private val urlRe = Regex("""https?://\S+""")

    fun build(name: String, items: List<Pair<Song, String>>): String = buildString {
        appendLine("$HEADER $name")
        items.forEach { (s, url) ->
            val artist = if (s.artist.equals("Unknown artist", ignoreCase = true)) "" else " — ${s.artist}"
            appendLine("${s.title.replace("|", "/")}$artist | $url")
        }
    }.trim()

    fun looksLikeList(text: String): Boolean =
        text.contains(HEADER) || urlRe.findAll(text).count() >= 2

    fun parse(text: String): RemoteManifest? {
        val lines = text.lines()
        val name = lines.firstOrNull { it.trim().startsWith(HEADER) }
            ?.trim()?.removePrefix(HEADER)?.trim()?.ifBlank { null } ?: "Shared links"
        val tracks = lines.mapNotNull { line ->
            val m = urlRe.find(line) ?: return@mapNotNull null
            val label = line.substring(0, m.range.first).trim().trimEnd('|').trim()
            val parts = label.split(" — ", limit = 2)
            RemoteTrack(
                index = 0,
                title = parts.getOrNull(0)?.trim()?.ifBlank { null } ?: m.value,
                artist = parts.getOrNull(1)?.trim() ?: "",
                link = m.value,
            )
        }
            .distinctBy { SongMatcher.normUrl(it.link ?: "") }
            .mapIndexed { i, t -> t.copy(index = i) }
        return if (tracks.isEmpty()) null else RemoteManifest(name, tracks)
    }
}
