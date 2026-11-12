package com.stellapp.disc

import com.stellapp.disc.playback.LongTrackMemory
import com.stellapp.disc.uplink.LinkKind
import com.stellapp.disc.uplink.YtdlpGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtdlpGuardTest {
    @Test
    fun blocksOptionsThatRunPrograms() {
        assertTrue(YtdlpGuard.isBlocked("--exec"))
        assertTrue(YtdlpGuard.isBlocked("--exec=rm -rf x"))
        assertTrue(YtdlpGuard.isBlocked("--exec-before-download"))
        assertTrue(YtdlpGuard.isBlocked("--EXE")) // shortened and in caps
        assertTrue(YtdlpGuard.isBlocked("-a"))
        assertTrue(YtdlpGuard.isBlocked("--downloader"))
        assertTrue(YtdlpGuard.isBlocked("--config-location"))
    }

    @Test
    fun allowsNormalOptions() {
        assertFalse(YtdlpGuard.isBlocked("--embed-thumbnail"))
        assertFalse(YtdlpGuard.isBlocked("--download-archive"))
        assertFalse(YtdlpGuard.isBlocked("--sponsorblock-remove"))
        assertFalse(YtdlpGuard.isBlocked("-f"))
        assertFalse(YtdlpGuard.isBlocked("--no-exec"))
    }

    @Test
    fun filterKeepsTheRest() {
        val args = listOf("--exec" to "x", "-f" to "ba", "--embed-metadata" to null)
        assertEquals(listOf("-f" to "ba", "--embed-metadata" to null), YtdlpGuard.filter(args))
        assertEquals(listOf("--exec"), YtdlpGuard.blockedIn(args))
    }
}

class LinkKindTest {
    @Test
    fun youtube() {
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://www.youtube.com/playlist?list=PL123"))
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://music.youtube.com/playlist?list=OLAK5uy"))
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://music.youtube.com/browse/MPREb_abc"))
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://www.youtube.com/@someband/videos"))
        assertEquals(LinkKind.SINGLE, LinkKind.of("https://youtu.be/dX3k_QDnzHE"))
        assertEquals(LinkKind.SINGLE, LinkKind.of("https://www.youtube.com/watch?v=dX3k_QDnzHE"))
        assertEquals(LinkKind.SINGLE, LinkKind.of("https://youtube.com/shorts/abcdefghijk"))
        assertEquals(LinkKind.SINGLE_IN_LIST, LinkKind.of("https://www.youtube.com/watch?v=dX3k_QDnzHE&list=PL123"))
    }

    @Test
    fun soundcloudAndBandcamp() {
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://soundcloud.com/artist/sets/summer"))
        assertEquals(LinkKind.SINGLE, LinkKind.of("https://soundcloud.com/artist/a-song"))
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://soundcloud.com/artist"))
        assertEquals(LinkKind.COLLECTION, LinkKind.of("https://band.bandcamp.com/album/record"))
        assertEquals(LinkKind.SINGLE, LinkKind.of("https://band.bandcamp.com/track/song"))
    }

    @Test
    fun unknownStaysUnknown() {
        assertEquals(LinkKind.UNKNOWN, LinkKind.of("not a link"))
        assertEquals(LinkKind.UNKNOWN, LinkKind.of("https://example.com/video/123"))
    }
}

class LongTrackMemoryTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun onlyLongTracksMidwayAreKept() {
        assertTrue(LongTrackMemory.worthKeeping(hour, 10 * 60 * 1000L))
        assertFalse(LongTrackMemory.worthKeeping(4 * 60 * 1000L, 60_000L)) // a normal song
        assertFalse(LongTrackMemory.worthKeeping(hour, 5_000L)) // barely started
        assertFalse(LongTrackMemory.worthKeeping(hour, hour - 5_000L)) // basically done
    }
}

class CrashScrubTest {
    @Test
    fun removesPathsLinksAndFileNames() {
        val raw = "java.io.FileNotFoundException: /storage/emulated/0/Music/My Song.mp3 (No such file)\n" +
            "at content://media/external/audio/media/42\n" +
            "loading https://lrclib.net/api/get?track_name=secret"
        val clean = CrashLog.scrub(raw)
        assertFalse(clean.contains("emulated"))
        assertFalse(clean.contains("media/42"))
        assertFalse(clean.contains("secret"))
        assertTrue(clean.contains("FileNotFoundException"))
    }

    @Test
    fun keepsStackFrames() {
        val frame = "at com.stellapp.disc.PlaybackService.onCreate(PlaybackService.kt:120)"
        assertEquals(frame, CrashLog.scrub(frame))
    }
}
