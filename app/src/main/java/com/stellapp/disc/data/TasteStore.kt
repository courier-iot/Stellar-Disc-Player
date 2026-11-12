package com.stellapp.disc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln

/** What happened when a track stopped playing. */
enum class ListenKind { COMPLETE, PARTIAL, SKIP }

/** Everything the app has learned about one track. */
data class Taste(
    val plays: Int = 0,
    val completes: Int = 0,
    val skips: Int = 0,
    val lastPlayed: Long = 0L,
    val listenedMs: Long = 0L,
    /** Plays by time of day: morning, afternoon, evening, night. */
    val hours: List<Int> = listOf(0, 0, 0, 0),
    /** Recent listens as (time, weight), newest last, max 20. */
    val recent: List<Pair<Long, Float>> = emptyList(),
)

/** A generated mix for the home screen. */
data class Mix(val key: String, val title: String, val subtitle: String, val songs: List<Song>)

/**
 * The learning engine behind Regular picks and the generated mixes.
 * The playback service reports every listen: finished, partly played or skipped,
 * and at what time of day. Everything stays on the phone.
 */
object TasteStore {
    private const val FILE_NAME = "taste.json"
    private const val DAY = 24L * 60 * 60 * 1000
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var file: File? = null

    private val _tastes = MutableStateFlow<Map<Long, Taste>>(emptyMap())
    val tastes: StateFlow<Map<Long, Taste>> = _tastes.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            _tastes.value = try {
                if (f.exists()) parse(f.readText()) else emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    fun bucketOf(time: Long): Int {
        val h = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..10 -> 0
            in 11..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
    }

    val bucketNames = listOf("Morning starters", "Afternoon flow", "Evening glow", "Late night rotation")

    fun record(songId: Long, kind: ListenKind, listenedMs: Long) {
        if (songId < 0) return
        val now = System.currentTimeMillis()
        val snapshot: Map<Long, Taste>
        synchronized(lock) {
            val old = _tastes.value[songId] ?: Taste()
            val weight = when (kind) {
                ListenKind.COMPLETE -> 1f
                ListenKind.PARTIAL -> 0.5f
                ListenKind.SKIP -> -0.7f
            }
            val hours = old.hours.toMutableList()
            if (kind != ListenKind.SKIP) hours[bucketOf(now)] = hours[bucketOf(now)] + 1
            val t = old.copy(
                plays = old.plays + if (kind != ListenKind.SKIP) 1 else 0,
                completes = old.completes + if (kind == ListenKind.COMPLETE) 1 else 0,
                skips = old.skips + if (kind == ListenKind.SKIP) 1 else 0,
                lastPlayed = if (kind != ListenKind.SKIP) now else old.lastPlayed,
                listenedMs = old.listenedMs + listenedMs,
                hours = hours,
                recent = (old.recent + (now to weight)).takeLast(20),
            )
            snapshot = _tastes.value + (songId to t)
            _tastes.value = snapshot
        }
        save(snapshot)
    }

    val totalListens: Int get() = _tastes.value.values.sumOf { it.plays + it.skips }

    // ---------------- picks ----------------

    /** How much you like a song right now: recent listens count most, skips count against it. */
    private fun score(s: Song, t: Taste?, now: Long, bucket: Int, artistBoost: Map<String, Float>): Float {
        var v = 0f
        if (t != null) {
            t.recent.forEach { (time, w) -> v += w * exp(-(now - time).toDouble() / (21.0 * DAY)).toFloat() }
            v += 0.15f * ln(1f + t.completes)
            val heard = t.plays + t.skips
            if (heard > 0) v -= 0.35f * t.skips / heard
            val totalHours = t.hours.sum()
            if (totalHours > 0) v += 0.3f * t.hours[bucket] / totalHours
            if (now - t.lastPlayed < 45 * 60 * 1000L) v -= 0.8f // just heard it
        }
        v += artistBoost[s.artist] ?: 0f
        // A little daily-changing noise so picks don't freeze.
        val day = now / DAY
        v += (((s.id * 31 + day * 17) % 100).toFloat() / 100f) * 0.08f
        return v
    }

    private fun artistBoost(songs: List<Song>, tastes: Map<Long, Taste>, now: Long): Map<String, Float> {
        val raw = HashMap<String, Float>()
        songs.forEach { s ->
            val t = tastes[s.id] ?: return@forEach
            var v = 0f
            t.recent.forEach { (time, w) -> v += w * exp(-(now - time).toDouble() / (30.0 * DAY)).toFloat() }
            raw[s.artist] = (raw[s.artist] ?: 0f) + v
        }
        val max = raw.values.maxOrNull()?.takeIf { it > 0f } ?: return emptyMap()
        return raw.mapValues { (k, v) -> if (k.equals("Unknown artist", true)) 0f else 0.25f * (v / max).coerceAtLeast(0f) }
    }

    /** Regular picks: the hero first, then the rest. Falls back to new music before there's history. */
    fun picks(songs: List<Song>, count: Int = 13): List<Song> {
        val music = songs.filter { it.showsInMusic }
        if (music.isEmpty()) return emptyList()
        val tastes = _tastes.value
        val now = System.currentTimeMillis()
        val bucket = bucketOf(now)
        val boost = artistBoost(music, tastes, now)
        val learned = music.filter { tastes[it.id]?.plays ?: 0 > 0 }
        val ranked = music.sortedByDescending { score(it, tastes[it.id], now, bucket, boost) }
        return if (learned.size < 5) {
            // Not enough history yet: mix in recently added music.
            (ranked.take(count / 2) + music.sortedByDescending { it.dateAdded }).distinctBy { it.id }.take(count)
        } else {
            ranked.take(count)
        }
    }

    // ---------------- generated mixes ----------------

    fun mixes(songs: List<Song>): List<Mix> {
        val music = songs.filter { it.showsInMusic }
        if (music.isEmpty()) return emptyList()
        val tastes = _tastes.value
        val now = System.currentTimeMillis()
        val out = ArrayList<Mix>()
        fun t(s: Song) = tastes[s.id]

        val bucket = bucketOf(now)
        // Time of day: the current part of the day first.
        (listOf(bucket) + (0..3).filter { it != bucket }).forEach { b ->
            val list = music.filter { s ->
                val x = t(s) ?: return@filter false
                val total = x.hours.sum()
                total >= 2 && x.hours[b] * 2 >= total
            }.sortedByDescending { t(it)?.hours?.get(b) ?: 0 }
            if (list.size >= 3) out += Mix("mix:time$b", bucketNames[b], "What you play at this time of day", list.take(50))
        }

        val onRepeat = music.filter { s ->
            (t(s)?.recent?.count { (time, w) -> w > 0.9f && now - time < 30 * DAY } ?: 0) >= 2
        }.sortedByDescending { s -> t(s)?.recent?.count { (time, w) -> w > 0.9f && now - time < 30 * DAY } ?: 0 }
        if (onRepeat.size >= 3) out += Mix("mix:repeat", "On repeat", "Finished again and again this month", onRepeat.take(50))

        val most = music.filter { (t(it)?.plays ?: 0) >= 2 }.sortedByDescending { t(it)?.plays ?: 0 }
        if (most.size >= 3) out += Mix("mix:most", "Most listened to", "Your all-time heavy rotation", most.take(50))

        val forgotten = music.filter { s ->
            val x = t(s) ?: return@filter false
            x.plays >= 3 && now - x.lastPlayed > 45 * DAY
        }.sortedByDescending { t(it)?.plays ?: 0 }
        if (forgotten.size >= 3) out += Mix("mix:forgotten", "Forgotten in time", "Old favourites you haven't played in a while", forgotten.take(50))

        val least = music.filter { (t(it)?.plays ?: 0) in 1..2 }.sortedBy { t(it)?.lastPlayed ?: 0L }
        if (least.size >= 3) out += Mix("mix:least", "Least listened to", "Heard once or twice. Worth another go?", least.take(50))

        val never = music.filter { t(it) == null || (t(it)?.plays ?: 0) == 0 && (t(it)?.skips ?: 0) == 0 }
            .sortedBy { (it.id * 7919) % 1000 }
        if (never.size >= 3) out += Mix("mix:never", "Never played", "Tracks you've never pressed play on", never.take(50))

        val second = music.filter { s ->
            val x = t(s) ?: return@filter false
            x.skips >= 2 && x.skips > x.completes
        }.sortedByDescending { t(it)?.skips ?: 0 }
        if (second.size >= 3) out += Mix("mix:second", "Second chances", "Songs you usually skip", second.take(50))

        val added = music.filter { now / 1000 - it.dateAdded < 30 * 24 * 3600 }.sortedByDescending { it.dateAdded }
        if (added.isNotEmpty()) out += Mix("mix:added", "Fresh arrivals", "Added in the last 30 days", added.take(50))

        music.filter { it.genre.isNotBlank() }.groupBy { it.genre.trim() }
            .filter { it.value.size >= 5 }
            .entries.sortedByDescending { e -> e.value.sumOf { t(it)?.plays ?: 0 } * 10 + e.value.size }
            .take(4)
            .forEach { (genre, list) -> out += Mix("mix:genre:$genre", "$genre mix", "${list.size} tracks tagged $genre", list.shuffled().take(50)) }

        val topArtist = music.filter { !it.artist.equals("Unknown artist", true) }
            .groupBy { it.artist }
            .maxByOrNull { (_, list) -> list.sumOf { t(it)?.plays ?: 0 } }
        if (topArtist != null && topArtist.value.size >= 3 && topArtist.value.sumOf { t(it)?.plays ?: 0 } > 0) {
            out += Mix("mix:artist", "Deep dive: ${topArtist.key}", "Everything from your most played artist", topArtist.value.sortedWith(compareBy({ it.album.lowercase() }, { it.track })).take(50))
        }
        return out
    }

    // ---------------- storage ----------------

    /** One song's taste data as JSON (for backups). */
    fun exportEntry(id: Long): JSONObject? {
        val t = _tastes.value[id] ?: return null
        return JSONObject()
            .put("p", t.plays)
            .put("c", t.completes)
            .put("s", t.skips)
            .put("l", t.lastPlayed)
            .put("ms", t.listenedMs)
            .put("h", JSONArray(t.hours))
            .put("r", JSONArray().apply { t.recent.forEach { (time, w) -> put(JSONArray().put(time).put(w.toDouble())) } })
    }

    /** Restores taste data from a backup; songs with more local history keep theirs. */
    fun importEntries(entries: Map<Long, JSONObject>) {
        if (entries.isEmpty()) return
        val wrapper = JSONObject()
        entries.forEach { (id, o) -> wrapper.put(id.toString(), o) }
        val incoming = try { parse(wrapper.toString()) } catch (e: Exception) { return }
        val snapshot: Map<Long, Taste>
        synchronized(lock) {
            val next = _tastes.value.toMutableMap()
            incoming.forEach { (id, t) ->
                val old = next[id]
                if (old == null || old.plays + old.skips < t.plays + t.skips) next[id] = t
            }
            snapshot = next
            _tastes.value = snapshot
        }
        save(snapshot)
    }

    private fun save(snapshot: Map<Long, Taste>) {
        val f = file ?: return
        io.execute {
            try {
                val o = JSONObject()
                snapshot.forEach { (id, t) ->
                    o.put(
                        id.toString(),
                        JSONObject()
                            .put("p", t.plays)
                            .put("c", t.completes)
                            .put("s", t.skips)
                            .put("l", t.lastPlayed)
                            .put("ms", t.listenedMs)
                            .put("h", JSONArray(t.hours))
                            .put("r", JSONArray().apply { t.recent.forEach { (time, w) -> put(JSONArray().put(time).put(w.toDouble())) } })
                    )
                }
                f.writeText(o.toString())
            } catch (_: Exception) {
            }
        }
    }

    private fun parse(text: String): Map<Long, Taste> {
        val o = JSONObject(text)
        return o.keys().asSequence().mapNotNull { k ->
            val id = k.toLongOrNull() ?: return@mapNotNull null
            val x = o.getJSONObject(k)
            val h = x.optJSONArray("h")
            val r = x.optJSONArray("r")
            id to Taste(
                plays = x.optInt("p"),
                completes = x.optInt("c"),
                skips = x.optInt("s"),
                lastPlayed = x.optLong("l"),
                listenedMs = x.optLong("ms"),
                hours = if (h != null && h.length() == 4) (0 until 4).map { h.getInt(it) } else listOf(0, 0, 0, 0),
                recent = if (r == null) emptyList() else (0 until r.length()).map { i ->
                    val p = r.getJSONArray(i)
                    p.getLong(0) to p.getDouble(1).toFloat()
                },
            )
        }.toMap()
    }
}

/** Songs and playlists pinned to the home screen. */
object PinStore {
    private val lock = Any()
    private var prefs: android.content.SharedPreferences? = null

    private val _songs = MutableStateFlow<List<Long>>(emptyList())
    val songs: StateFlow<List<Long>> = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<List<String>>(emptyList())
    val playlists: StateFlow<List<String>> = _playlists.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences("pins", Context.MODE_PRIVATE)
            prefs = p
            _songs.value = p.getString("songs", "")!!.split(",").mapNotNull { it.toLongOrNull() }
            _playlists.value = p.getString("playlists", "")!!.split(",").filter { it.isNotBlank() }
        }
    }

    /** Pins songs (from a backup), keeping any already pinned first. */
    fun addSongs(ids: List<Long>) {
        val next = (_songs.value + ids).distinct()
        _songs.value = next
        prefs?.edit()?.putString("songs", next.joinToString(","))?.apply()
    }

    fun toggleSong(id: Long) {
        val next = if (id in _songs.value) _songs.value - id else listOf(id) + _songs.value
        _songs.value = next
        prefs?.edit()?.putString("songs", next.joinToString(","))?.apply()
    }

    fun togglePlaylist(id: String) {
        val next = if (id in _playlists.value) _playlists.value - id else listOf(id) + _playlists.value
        _playlists.value = next
        prefs?.edit()?.putString("playlists", next.joinToString(","))?.apply()
    }
}
