package com.stellapp.disc

import com.stellapp.disc.data.Backup
import com.stellapp.disc.data.TitleFix
import com.stellapp.disc.swap.ShareText
import com.stellapp.disc.swap.SongMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleFixTest {
    @Test
    fun splitsArtistOutOfFileNameTitles() {
        assertEquals("MONEY ON THE DASH" to "Elley Duhe", TitleFix.clean("3 - Elley Duhe - MONEY ON THE DASH", "Unknown artist", fromApp = true))
    }

    @Test
    fun stripsTopicSuffix() {
        assertEquals("Song" to "Band", TitleFix.clean("Song", "Band - Topic", fromApp = false))
    }

    @Test
    fun leavesTaggedSongsAlone() {
        assertEquals("1999" to "Prince", TitleFix.clean("1999", "Prince", fromApp = false))
    }
}

class SongMatcherTest {
    @Test
    fun youtubeLinksCompareEqual() {
        val a = SongMatcher.normUrl("https://youtu.be/dX3k_QDnzHE")
        assertEquals(a, SongMatcher.normUrl("https://www.youtube.com/watch?v=dX3k_QDnzHE&t=10"))
        assertEquals(a, SongMatcher.normUrl("https://music.youtube.com/watch?v=dX3k_QDnzHE"))
    }

    @Test
    fun otherLinksIgnoreSchemeAndWww() {
        assertEquals(SongMatcher.normUrl("https://www.example.com/a/"), SongMatcher.normUrl("http://example.com/a#x"))
    }

    @Test
    fun titleNormalisingDropsJunk() {
        assertEquals("song name", SongMatcher.norm("Song Name (Official Video) [HD] feat. Someone"))
    }
}

class ShareTextTest {
    private val sample = """
        Stellar Disc playlist: Night Drive
        Midnight City — M83 | https://youtu.be/dX3k_QDnzHE
        Same again — M83 | https://www.youtube.com/watch?v=dX3k_QDnzHE
        Nightcall — Kavinsky | https://youtu.be/MV_3Dpw-BRY
    """.trimIndent()

    @Test
    fun recognisesLists() {
        assertTrue(ShareText.looksLikeList(sample))
        assertFalse(ShareText.looksLikeList("just one https://example.com link"))
    }

    @Test
    fun parsesAndRemovesDuplicateLinks() {
        val m = ShareText.parse(sample)
        assertNotNull(m)
        m!!
        assertEquals("Night Drive", m.name)
        assertEquals(2, m.tracks.size)
        assertEquals("Midnight City", m.tracks[0].title)
        assertEquals("M83", m.tracks[0].artist)
        assertEquals(1, m.tracks[1].index)
    }

    @Test
    fun noLinksMeansNoList() {
        assertNull(ShareText.parse("nothing here"))
    }
}

class M3uTest {
    @Test
    fun readsExtendedEntries() {
        val text = """
            #EXTM3U
            #EXTINF:215,Daft Punk - Digital Love
            /storage/emulated/0/Music/digital.mp3
            C:\Music\Plain File.flac
        """.trimIndent()
        val e = Backup.parseM3u(text)
        assertEquals(2, e.size)
        assertEquals(Backup.M3uEntry("Digital Love", "Daft Punk", 215_000, "/storage/emulated/0/Music/digital.mp3"), e[0])
        assertEquals("Plain File", e[1].title)
        assertEquals(0L, e[1].durationMs)
    }
}
