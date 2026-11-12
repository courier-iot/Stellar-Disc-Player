package com.stellapp.disc

import com.stellapp.disc.playback.RotationDeck
import com.stellapp.disc.playback.RotationPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RotationTest {

    @Test
    fun nothingIsDroppedForTheFirstFiftySongs() {
        for (i in 0..49) assertEquals("index $i", 0, RotationPlan.dropCount(i))
    }

    @Test
    fun theFiftyFirstSongDropsTheFirst() {
        assertEquals(1, RotationPlan.dropCount(50))
        assertEquals(1, RotationPlan.addCount(149))
    }

    /** Plays through 500 songs, checking the queue never grows and works exactly as described. */
    @Test
    fun slidingWindowKeepsTheQueueAtOneHundredFifty() {
        var next = 151L
        val queue = (1L..150L).toMutableList()
        var current = 0
        repeat(500) { step ->
            current++ // the next song starts
            val playing = queue[current]
            val drop = RotationPlan.dropCount(current)
            repeat(drop) { queue.removeAt(0) }
            current -= drop
            repeat(RotationPlan.addCount(queue.size)) { queue += next++ }
            assertEquals(playing, queue[current])
            assertEquals(150, queue.size)
            assertTrue("never more than 49 behind", current <= RotationPlan.KEEP_BEHIND)
            if (step == 49) {
                // We're on song 51: #1 is gone and #151 has been added.
                assertFalse(1L in queue)
                assertTrue(151L in queue)
                assertEquals(2L, queue.first())
            }
        }
    }

    @Test
    fun deckDealsEverySongBeforeRepeating() {
        val deck = RotationDeck((1L..20L).toList(), Random(3))
        val dealt = deck.take(20, emptySet())
        assertEquals(20, dealt.toSet().size)
    }

    @Test
    fun deckSkipsSongsAlreadyQueued() {
        val deck = RotationDeck((1L..5L).toList(), Random(1))
        val dealt = deck.take(10, setOf(1L, 2L))
        assertEquals(setOf(3L, 4L, 5L), dealt.toSet())
        assertEquals(3, dealt.size)
    }

    @Test
    fun emptyLibraryDealsNothing() {
        assertNull(RotationDeck(emptyList()).next(emptySet()))
    }
}
