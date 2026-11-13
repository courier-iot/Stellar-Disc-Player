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
        // Indexed once, so each incoming track checks only same-named songs (not the whole library).
        val index = TagIndex(library, { it.title }, { it.artist }, { it.durationMs })

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
            val hit = index.find(r.title, r.artist, r.durationMs)
            // No fingerprint (a whole-library move): the same song at exactly the same size is the same file.
            if (hit != null && r.hash == null && r.size > 0 && hit.size == r.size) return@map TrackMatch(MatchKind.HAVE, hit)
            if (hit != null) TrackMatch(MatchKind.PROBABLY, hit) else TrackMatch(MatchKind.NEW, null)
        }
    }

    /**
     * True when two library entries are the same song: matching title/artist
     * and lengths within 3 seconds (both must be known).
     */
    fun isSameSong(a: Song, b: Song): Boolean {
        if (a.durationMs <= 0 || b.durationMs <= 0 || abs(a.durationMs - b.durationMs) > 3_000) return false
        return similar(keys(a.title, a.artist), keys(b.title, b.artist))
    }

    /**
     * Finds the first item (in list order) that [similar] to a title/artist with a close enough
     * length. Same answer as scanning the whole list, but looks up candidates by exact key.
     */
    internal class TagIndex<T>(
        private val items: List<T>,
        title: (T) -> String,
        artist: (T) -> String,
        private val duration: (T) -> Long,
    ) {
        private val tagKeys = items.map { keys(title(it), artist(it)) }
        private val byTitle = HashMap<String, MutableList<Int>>()
        private val byCombo = HashMap<String, MutableList<Int>>()

        init {
            tagKeys.forEachIndexed { i, k ->
                if (k.title.isNotEmpty()) {
                    byTitle.getOrPut(k.title) { ArrayList(1) }.add(i)
                    byCombo.getOrPut(k.combo) { ArrayList(1) }.add(i)
                }
            }
        }

        fun find(title: String, artist: String, durationMs: Long): T? {
            val rk = keys(title, artist)
            if (rk.title.isEmpty()) return null
            // similar() is true only when one of these exact keys lines up.
            val candidates = sortedSetOf<Int>()
            byTitle[rk.title]?.let { candidates.addAll(it) }
            byCombo[rk.title]?.let { candidates.addAll(it) }
            byTitle[rk.combo]?.let { candidates.addAll(it) }
            byCombo[rk.combo]?.let { candidates.addAll(it) }
            for (i in candidates) {
                if (similar(tagKeys[i], rk) && durationOk(duration(items[i]), durationMs)) return items[i]
            }
            return null
        }

        /** The old way, kept for tests: scan everything in order. */
        fun findByScan(title: String, artist: String, durationMs: Long): T? {
            val rk = keys(title, artist)
            return items.indices.firstOrNull { similar(tagKeys[it], rk) && durationOk(duration(items[it]), durationMs) }?.let { items[it] }
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
