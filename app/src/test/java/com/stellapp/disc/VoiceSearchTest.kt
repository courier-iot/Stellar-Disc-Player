package com.stellapp.disc

import com.stellapp.disc.data.VoiceSearch
import com.stellapp.disc.data.VoiceSearch.Focus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Play requests from Stella and other assistants find the right songs. */
class VoiceSearchTest {
    private data class S(val title: String, val artist: String, val album: String, val track: Int = 0)

    private val lib = listOf(
        S("Bohemian Rhapsody", "Queen", "A Night at the Opera", 11),
        S("Love of My Life", "Queen", "A Night at the Opera", 9),
        S("Don't Stop Me Now", "Queen", "Jazz", 12),
        S("One More Time", "Daft Punk", "Discovery", 1),
        S("Digital Love", "Daft Punk", "Discovery", 3),
        S("Aerodynamic", "Daft Punk", "Discovery", 2),
        S("Starlight", "Muse", "Black Holes and Revelations", 2),
    )
    private val lists = listOf("Gym" to listOf(lib[3], lib[2]))

    private fun pick(q: String, f: Focus = Focus.ANY) =
        VoiceSearch.pick(lib, lists, q, f, { it.title }, { it.artist }, { it.album }, { it.track }, Random(1))

    @Test
    fun songByTitleThenMoreByTheArtist() {
        val h = pick("play Bohemian Rhapsody")
        assertEquals("Bohemian Rhapsody", h.items.first().title)
        assertTrue(h.items.drop(1).all { it.artist == "Queen" })
        assertEquals(3, h.items.size)
    }

    @Test
    fun titleAndArtistTogether() {
        assertEquals("Digital Love", pick("digital love daft punk").items.first().title)
        assertEquals("Starlight", pick("Starlight by Muse").items.first().title)
    }

    @Test
    fun artistGetsAllTheirSongs() {
        val h = pick("play some Daft Punk")
        assertEquals(3, h.items.size)
        assertTrue(h.items.all { it.artist == "Daft Punk" })
        assertEquals(3, pick("songs by daft punk", Focus.ARTIST).items.size)
    }

    @Test
    fun albumPlaysInTrackOrder() {
        val h = pick("Discovery", Focus.ALBUM)
        assertEquals(listOf(1, 2, 3), h.items.map { it.track })
        assertEquals(listOf(1, 2, 3), pick("play the album discovery").items.map { it.track })
    }

    @Test
    fun playlistsByName() {
        assertEquals(lists[0].second, pick("play my gym playlist").items)
        assertEquals(lists[0].second, pick("gym").items)
    }

    @Test
    fun emptyOrVagueMeansShuffleEverything() {
        assertEquals(lib.size, pick("").items.size)
        assertEquals(lib.size, pick("play some music").items.size)
        assertEquals(lib.size, pick("play music on Stellar Disc").items.size)
    }

    @Test
    fun noMatchIsEmpty() {
        assertTrue(pick("zzz unknown").items.isEmpty())
    }
}
