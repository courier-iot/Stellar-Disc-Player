package com.stellapp.disc.data

import android.content.Context
import kotlin.random.Random

/**
 * "Play Bohemian Rhapsody", "play some Daft Punk", "play my Gym playlist": turns what a voice assistant
 * (Stella, Google Assistant, a car) asks for into songs from your library. Works on any item type so the
 * matching can be unit-tested without Android.
 */
object VoiceSearch {

    /** What was found: the songs in play order, and a short description ("songs by Queen"). */
    data class Hit<T>(val items: List<T>, val what: String)

    /** Android's "focus" hints that come with a play request (MediaStore.EXTRA_MEDIA_FOCUS). */
    enum class Focus { ANY, SONG, ARTIST, ALBUM, PLAYLIST }

    fun focusOf(value: String?): Focus = when {
        value == null -> Focus.ANY
        value.contains("artist") -> Focus.ARTIST
        value.contains("album") -> Focus.ALBUM
        value.contains("playlist") -> Focus.PLAYLIST
        value.contains("audio") -> Focus.SONG
        else -> Focus.ANY
    }

    private val LEAD = Regex("^(?:please\\s+)?(?:play|put on|start|shuffle|listen to)?\\s*(?:me\\s+)?(?:some\\s+)?" +
        "(?:(?:the\\s+)?(?:song|track|album|playlist)\\s+|(?:songs?|music|tracks|anything|something)\\s+(?:by|from)\\s+)?")
    private val TAIL = Regex("\\s+(?:on|in|with|from)\\s+stellar\\s*disc.*$|\\s+please$|\\s+for me$")

    private val FILLER = setOf("by", "the", "from", "feat", "ft", "and")

    fun norm(s: String): String =
        s.lowercase().replace('&', ' ').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    /** The request with "play", "some", "songs by", "on Stellar Disc" and the like taken off. */
    fun cleanQuery(raw: String): String {
        var q = raw.lowercase().trim()
        q = TAIL.replace(q, "")
        q = LEAD.replaceFirst(q, "")
        val n = norm(q)
        return if (n in setOf("music", "songs", "something", "anything", "my music", "my library", "everything")) "" else n
    }

    fun <T> pick(
        items: List<T>,
        playlists: List<Pair<String, List<T>>>,
        rawQuery: String,
        focusIn: Focus,
        title: (T) -> String,
        artist: (T) -> String,
        album: (T) -> String,
        track: (T) -> Int,
        random: Random = Random.Default,
    ): Hit<T> {
        var q = cleanQuery(rawQuery)
        var focus = focusIn
        if (q.endsWith(" playlist")) {          // "my gym playlist"
            q = q.removeSuffix(" playlist").removePrefix("my ").trim()
            focus = Focus.PLAYLIST
        }
        if (items.isEmpty()) return Hit(emptyList(), "nothing (the library is empty)")
        if (q.isEmpty()) return Hit(items.shuffled(random), "your library on shuffle")
        val words = q.split(' ').filter { it !in FILLER }
        // "starlight by muse": a title and an artist
        val byParts = q.split(" by ", limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() }

        // Playlists first when asked for one, or when the name matches exactly
        if (focus == Focus.PLAYLIST || focus == Focus.ANY) {
            val exact = playlists.firstOrNull { norm(it.first) == q }
            val loose = if (focus == Focus.PLAYLIST) playlists.firstOrNull { norm(it.first).contains(q) || q.contains(norm(it.first)) } else null
            (exact ?: loose)?.let { (name, songs) -> if (songs.isNotEmpty()) return Hit(songs, "your $name playlist") }
        }

        fun byArtist(name: String) = items.filter { norm(artist(it)) == name }
        fun byAlbum(name: String) = items.filter { norm(album(it)) == name }.sortedBy { track(it) }

        when (focus) {
            Focus.ARTIST -> {
                val name = items.map { norm(artist(it)) }.firstOrNull { it == q }
                    ?: items.map { norm(artist(it)) }.firstOrNull { it.contains(q) }
                if (name != null) return Hit(byArtist(name).shuffled(random), "songs by ${artist(byArtist(name).first())}")
            }
            Focus.ALBUM -> {
                val name = items.map { norm(album(it)) }.firstOrNull { it == q }
                    ?: items.map { norm(album(it)) }.firstOrNull { it.contains(q) }
                if (name != null) return Hit(byAlbum(name), "the album ${album(byAlbum(name).first())}")
            }
            else -> {}
        }

        // Score every song: title matches beat artist and album matches
        var best: T? = null
        var bestScore = 0
        var bestKind = ""
        for (s in items) {
            val t = norm(title(s))
            val a = norm(artist(s))
            val al = norm(album(s))
            val both = "$t $a"
            val (score, kind) = when {
                t == q -> 100 to "song"
                "$t $a" == q || "$a $t" == q -> 98 to "song"
                byParts != null && t == byParts[0] && a.contains(byParts[1]) -> 96 to "song"
                t.isNotEmpty() && (t.startsWith(q) || (q.length >= 4 && t.contains(q))) -> 80 to "song"
                focus != Focus.SONG && a == q -> 75 to "artist"
                words.size > 1 && words.all { w -> both.split(' ').contains(w) } -> 70 to "song"
                focus != Focus.SONG && al == q -> 60 to "album"
                focus != Focus.SONG && q.length >= 3 && a.contains(q) -> 45 to "artist"
                focus != Focus.SONG && q.length >= 3 && al.contains(q) -> 40 to "album"
                else -> 0 to ""
            }
            if (score > bestScore) {
                best = s
                bestScore = score
                bestKind = kind
            }
        }
        val b = best ?: return Hit(emptyList(), "nothing matching \"$rawQuery\"")
        return when (bestKind) {
            "artist" -> {
                val name = norm(artist(b))
                Hit(byArtist(name).shuffled(random), "songs by ${artist(b)}")
            }
            "album" -> Hit(byAlbum(norm(album(b))), "the album ${album(b)}")
            else -> {
                // The song, then more by the same artist so the music keeps going
                val more = items.filter { it !== b && norm(artist(it)) == norm(artist(b)) && artist(b).isNotBlank() }
                Hit(listOf(b) + more.shuffled(random), "${title(b)} by ${artist(b)}")
            }
        }
    }

    /** Looks up a play request in this phone's music library (hidden folders and videos left out). */
    suspend fun find(context: Context, rawQuery: String, focusHint: String?): Hit<Song> {
        PlaylistStore.init(context)
        MetaStore.init(context)
        val hidden = context.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE)
            .getStringSet("hidden_folders", emptySet()) ?: emptySet()
        val songs = MusicRepository.loadSongs(context, allVideos = false)
            .filter { !it.isVideo && it.showsInMusic && it.folder !in hidden }
            .map { MetaStore.apply(it) }
        val byId = songs.associateBy { it.id }
        val lists = PlaylistStore.playlists.value.map { p -> p.name to p.songIds.mapNotNull { byId[it] } }
        return pick(songs, lists, rawQuery, focusOf(focusHint), { it.title }, { it.artist }, { it.album }, { it.track })
    }
}
