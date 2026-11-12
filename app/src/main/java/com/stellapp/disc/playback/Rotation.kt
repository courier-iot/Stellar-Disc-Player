package com.stellapp.disc.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.random.Random

/**
 * The numbers behind the random rotation: the queue holds [SIZE] songs. Once you're past song
 * [KEEP_BEHIND] + 1, every new song drops the oldest one and adds a fresh one at the end, so it
 * keeps going forever without the queue ever growing.
 * Example: songs 1–150 queued; on song 51, #1 is removed and #151 is added.
 */
object RotationPlan {
    const val SIZE = 150
    const val KEEP_BEHIND = 49

    /** Songs to remove from the front when the current song is at [currentIndex]. */
    fun dropCount(currentIndex: Int): Int = (currentIndex - KEEP_BEHIND).coerceAtLeast(0)

    /** Songs to add at the end so the queue is back to [SIZE]. */
    fun addCount(queueSize: Int): Int = (SIZE - queueSize).coerceAtLeast(0)
}

/** A shuffled deck: deals every id once before reshuffling, so the whole library comes round. */
class RotationDeck(ids: List<Long>, private val random: Random = Random.Default) {
    private val all = ids.distinct()
    private val deck = ArrayDeque<Long>()

    val size: Int get() = all.size

    /** The next id that isn't in [exclude], or null if every song is excluded. */
    fun next(exclude: Set<Long>): Long? {
        if (all.isEmpty()) return null
        repeat(2) {
            while (deck.isNotEmpty()) {
                val id = deck.removeFirst()
                if (id !in exclude) return id
            }
            deck.addAll(all.shuffled(random))
        }
        return null
    }

    fun take(count: Int, exclude: Set<Long>): List<Long> {
        val out = ArrayList<Long>(count)
        val skip = exclude.toMutableSet()
        repeat(count) {
            val id = next(skip) ?: return out
            out += id
            skip += id
        }
        return out
    }
}

/**
 * The live rotation, shared by the app (which knows the library) and the playback service
 * (which owns the player). Both run on the main thread.
 */
object Rotation {
    private const val PREFS = "stellar_disc"
    private const val KEY = "rotation_active"

    private var pool: Map<Long, MediaItem> = emptyMap()
    private var deck: RotationDeck? = null
    var active: Boolean = false
        private set

    /** Starts a rotation over [library]; returns the first [RotationPlan.SIZE] items, [first] leading. */
    fun start(context: Context, first: MediaItem, library: List<MediaItem>): List<MediaItem> {
        setPool(library)
        active = true
        save(context)
        val firstId = first.mediaId.toLongOrNull() ?: -1L
        val ids = deck?.take(RotationPlan.SIZE - 1, setOf(firstId)) ?: emptyList()
        return listOf(first) + ids.mapNotNull { pool[it] }
    }

    fun stop(context: Context) {
        if (!active) return
        active = false
        save(context)
    }

    /** After a restart: picks the rotation back up once the library is loaded again. */
    fun resumeIfSaved(context: Context, library: List<MediaItem>) {
        if (active || !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)) {
            if (active) setPool(library)
            return
        }
        setPool(library)
        active = true
    }

    private fun setPool(library: List<MediaItem>) {
        pool = library.mapNotNull { item -> item.mediaId.toLongOrNull()?.let { it to item } }.toMap()
        deck = RotationDeck(pool.keys.toList())
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, active).apply()
    }

    /** Called by the playback service whenever the song changes. */
    fun onTrackChanged(player: Player) {
        if (!active || player.shuffleModeEnabled || pool.isEmpty()) return
        val drop = RotationPlan.dropCount(player.currentMediaItemIndex).coerceAtMost(player.mediaItemCount - 1)
        if (drop > 0) player.removeMediaItems(0, drop)
        val add = RotationPlan.addCount(player.mediaItemCount)
        if (add > 0) {
            val inQueue = HashSet<Long>()
            for (i in 0 until player.mediaItemCount) player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { inQueue += it }
            val ids = deck?.take(add, inQueue) ?: emptyList()
            val items = ids.mapNotNull { pool[it] }
            if (items.isNotEmpty()) player.addMediaItems(items)
        }
    }
}
