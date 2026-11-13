package com.stellapp.disc.swap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The indexed matcher must pick exactly what the old full scan picked, only faster. */
class MatcherSpeedTest {
    private data class S(val title: String, val artist: String, val dur: Long)

    private val words = listOf("star", "night", "drive", "neon", "rain", "echo", "gold", "fire", "blue", "moon")

    private fun lib(n: Int, seed: Int): List<S> {
        val r = Random(seed)
        return List(n) {
            val t = (1..(1 + r.nextInt(3))).joinToString(" ") { words[r.nextInt(words.size)] }
            val title = when (r.nextInt(5)) {
                0 -> "$t (Official Video)"
                1 -> "$t feat. Someone"
                else -> t
            }
            val artist = if (r.nextInt(6) == 0) "" else "Artist ${r.nextInt(40)}"
            S(title, artist, 120_000L + r.nextInt(120) * 1_000L)
        }
    }

    @Test
    fun sameAnswersAsTheFullScan() {
        val local = lib(1500, 1)
        val remote = lib(800, 2) + local.take(200).map { it.copy(title = "${it.artist} - ${it.title}".trim()) }
        val index = SongMatcher.TagIndex(local, { it.title }, { it.artist }, { it.dur })
        var hits = 0
        remote.forEach { r ->
            val a = index.find(r.title, r.artist, r.dur)
            val b = index.findByScan(r.title, r.artist, r.dur)
            assertEquals("Mismatch for $r", b, a)
            if (a != null) hits++
        }
        assertTrue("the test should exercise real matches", hits > 50)
    }

    @Test
    fun bigLibrariesAreQuick() {
        val local = lib(5000, 3)
        val remote = lib(5000, 4)
        val start = System.nanoTime()
        val index = SongMatcher.TagIndex(local, { it.title }, { it.artist }, { it.dur })
        remote.forEach { index.find(it.title, it.artist, it.dur) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("5,000 × 5,000 took $ms ms", ms < 5_000)
    }
}
