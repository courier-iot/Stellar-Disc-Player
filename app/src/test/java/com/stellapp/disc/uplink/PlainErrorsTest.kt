package com.stellapp.disc.uplink

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainErrorsTest {
    @Test
    fun botCheck() {
        val s = PlainErrors.explain("ERROR: [youtube] abc: Sign in to confirm you're not a bot. Use --cookies-from-browser")
        assertTrue(s!!.contains("bot"))
    }

    @Test
    fun commonCases() {
        assertTrue(PlainErrors.explain("ERROR: [youtube] x: Private video. Sign in if you've been granted access")!!.contains("private"))
        assertTrue(PlainErrors.explain("ERROR: Unsupported URL: https://example.com/a")!!.contains("isn't supported"))
        assertTrue(PlainErrors.explain("OSError: [Errno 28] No space left on device")!!.contains("storage"))
        assertTrue(PlainErrors.explain("ERROR: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>")!!.contains("connection"))
        assertTrue(PlainErrors.explain("The uploader has not made this video available in your country")!!.contains("region"))
    }

    @Test
    fun unknownStaysRaw() {
        assertNull(PlainErrors.explain("Something nobody has seen before"))
        assertNull(PlainErrors.explain(null))
        assertNull(PlainErrors.explain("   "))
    }
}
