package com.stellapp.disc.swap

import android.content.Context
import com.stellapp.disc.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The song checker. For each incoming track it decides:
 *  HAVE     - you have the exact same file, or the same source link
 *  PROBABLY - same song by title/artist (and length within 3s), but a different file
 *  NEW      - not in your library
 */
object SongMatcher {

    private data class Keys(val title: String, val artist: String, val combo: String)

    suspend fun check(
        context: Context,
        library: List<Song>,
        remote: List<RemoteTrack>,
        links: Map<Long, String>,
    ): List<TrackMatch> = withContext(Dispatchers.IO) {
        val bySize = library.filter { it.size > 0 }.groupBy { it.size }
        val byId = library.associateBy { it.id }
        val byLink = HashMap<String, Long>().apply {
            links.forEach { (id, url) -> put(normUrl(url), id) }
        }
        val hashCache = HashMap<Long, String?>()
        val prepared = library.map { it to keys(it.title, it.artist) }

        remote.map { r ->
            // 1. Exact same file
            if (r.hash != null && r.size > 0) {
                bySize[r.size]?.forEach { local ->
                    val h = hashCache.getOrPut(local.id) { QuickHash.ofUri(context, local.uri)?.first }
                    if (h == r.hash) return@map TrackMatch(MatchKind.HAVE, local)
                }
            }
            // 2. Same source link
            r.link?.let { l ->
                byLink[normUrl(l)]?.let { id -> byId[id] }?.let { return@map TrackMatch(MatchKind.HAVE, it) }
            }
            // 3. Same song, different file
            val rk = keys(r.title, r.artist)
            val hit = prepared.firstOrNull { (local, lk) -> similar(lk, rk) && durationOk(local.durationMs, r.durationMs) }?.first
            if (hit != null) TrackMatch(MatchKind.PROBABLY, hit) else TrackMatch(MatchKind.NEW, null)
        }
    }

    private fun keys(title: String, artist: String): Keys {
        val a = if (artist.isBlank() || artist.equals("Unknown artist", ignoreCase = true)) "" else norm(artist)
        val t = norm(title)
        return Keys(t, a, "$a $t".trim())
    }

    private fun similar(a: Keys, b: Keys): Boolean {
        if (a.title.isEmpty() || b.title.isEmpty()) return false
        val artistOk = a.artist.isEmpty() || b.artist.isEmpty() || a.artist == b.artist
        return (a.title == b.title && artistOk) || a.combo == b.title || b.combo == a.title || a.combo == b.combo
    }

    private fun durationOk(a: Long, b: Long): Boolean = a <= 0 || b <= 0 || abs(a - b) <= 3_000

    private val bracketRe = Regex("""[(\[{][^)\]}]*[)\]}]""")
    private val featRe = Regex("""\b(feat|ft|featuring)\b.*""")
    private val nonWordRe = Regex("""[^\p{L}\p{N}]+""")

    /** "Song Name (Official Video) [HD] feat. X" -> "song name" */
    fun norm(s: String): String = s.lowercase()
        .replace(bracketRe, " ")
        .replace(featRe, " ")
        .replace(nonWordRe, " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

    /** Makes youtu.be/x, youtube.com/watch?v=x and music.youtube.com links compare equal. */
    fun normUrl(url: String): String {
        val u = url.trim()
        Regex("""(?:youtu\.be/|[?&]v=|/shorts/)([A-Za-z0-9_-]{11})""").find(u)?.let { return "yt:" + it.groupValues[1] }
        return u.lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").removePrefix("m.")
            .substringBefore("#")
            .trimEnd('/')
    }
}
