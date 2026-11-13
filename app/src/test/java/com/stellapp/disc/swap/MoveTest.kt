package com.stellapp.disc.swap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveTest {
    private fun track(i: Int, video: Boolean = false, fav: Boolean = false, plays: Int = 0, size: Long = 100L) =
        RemoteTrack(index = i, title = "T$i", artist = "A", size = size, isVideo = video, fav = fav, plays = plays)

    @Test
    fun moveManifestRoundTrips() {
        val m = RemoteManifest(
            "Move from Phone",
            listOf(track(0, fav = true, plays = 7), track(1, video = true)),
            kind = RemoteManifest.KIND_MOVE,
            groups = listOf(RemoteGroup("Road trip", listOf(1, 0))),
        )
        val back = parseManifest(m.toJson())
        assertTrue(back.isMove)
        assertEquals(listOf(RemoteGroup("Road trip", listOf(1, 0))), back.groups)
        assertTrue(back.tracks[0].fav)
        assertEquals(7, back.tracks[0].plays)
        assertTrue(back.tracks[1].isVideo)
    }

    @Test
    fun oldSharesStillParseAsPlaylists() {
        val old = """{"app":"stellar-disc","v":1,"name":"Mix","tracks":[{"i":0,"title":"T"}]}"""
        val m = parseManifest(old)
        assertFalse(m.isMove)
        assertTrue(m.groups.isEmpty())
    }

    @Test
    fun fitOrderPutsTheChosenKindFirst() {
        val items = listOf(
            SwapItem(track(0, video = true, plays = 9), MatchKind.NEW, null, false),
            SwapItem(track(1, plays = 1), MatchKind.NEW, null, false),
            SwapItem(track(2, plays = 5), MatchKind.NEW, null, false),
            SwapItem(track(3, fav = true), MatchKind.NEW, null, false),
        )
        val idx = items.indices.toList()
        assertEquals(listOf(2, 1, 3, 0), idx.sortedWith(fitOrder(items, FitPriority.MUSIC)))
        assertEquals(0, idx.sortedWith(fitOrder(items, FitPriority.VIDEOS)).first())
        assertEquals(3, idx.sortedWith(fitOrder(items, FitPriority.FAVOURITES)).first())
    }
}
