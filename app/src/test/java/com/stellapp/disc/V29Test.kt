package com.stellapp.disc

import com.stellapp.disc.playback.UndoPlacement
import com.stellapp.disc.swap.SwapCrypto
import com.stellapp.disc.uplink.YtdlpGuard
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SwapCryptoTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun sealedListRoundTrips() {
        val plain = "{\"app\":\"stellar-disc\"}".toByteArray()
        assertArrayEquals(plain, SwapCrypto.open(key, SwapCrypto.seal(key, plain)))
    }

    @Test(expected = Exception::class)
    fun tamperedListIsRejected() {
        val sealed = SwapCrypto.seal(key, "hello".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        SwapCrypto.open(key, sealed)
    }

    /** A transfer that breaks off at any byte and resumes must decrypt to the original file. */
    @Test
    fun resumingMidFileDecryptsCorrectly() {
        val file = Random(7).nextBytes(10_000)
        val whole = SwapCrypto.fileCipher(key, 3, 0).update(file)
        for (cut in listOf(1, 15, 16, 17, 4097, 9999)) {
            val first = SwapCrypto.fileCipher(key, 3, 0).update(whole.copyOfRange(0, cut))
            val rest = SwapCrypto.fileCipher(key, 3, cut.toLong()).update(whole.copyOfRange(cut, whole.size))
            assertArrayEquals("cut at $cut", file, first + rest)
        }
    }

    @Test
    fun differentTracksUseDifferentKeystreams() {
        val zeros = ByteArray(64)
        val a = SwapCrypto.fileCipher(key, 1, 0).update(zeros)
        val b = SwapCrypto.fileCipher(key, 2, 0).update(zeros)
        assertFalse(a.contentEquals(b))
    }
}

class UndoPlacementTest {
    @Test
    fun goesBackAfterItsOldNeighbour() {
        // "b" was removed from a, b, c; then "x" was added at the front.
        assertEquals(2, UndoPlacement.position(listOf("x", "a", "c"), before = "a", after = "c", originalIndex = 1))
    }

    @Test
    fun fallsBackToTheOtherNeighbourThenTheIndex() {
        assertEquals(0, UndoPlacement.position(listOf("c", "d"), before = "a", after = "c", originalIndex = 1))
        assertEquals(1, UndoPlacement.position(listOf("q"), before = "a", after = "c", originalIndex = 5))
    }
}

class StrictOptionsTest {
    @Test
    fun strictOnlyLetsKnownOptionsThrough() {
        val args = listOf("-f" to "ba", "--embed-metadata" to null, "--mystery-option" to "1", "--exec" to "x")
        assertEquals(listOf("-f" to "ba", "--embed-metadata" to null), YtdlpGuard.filter(args, strict = true))
        assertEquals(3, YtdlpGuard.filter(args, strict = false).size)
        assertTrue("--mystery-option" in YtdlpGuard.blockedIn(args, strict = true))
    }
}
