package com.stellapp.disc.swap

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.concurrent.thread

/** The direct LAN client that replaced HttpURLConnection (which Android blocks for http://). */
class LanHttpTest {

    private val payload = ByteArray(200_000) { (it * 31).toByte() }

    /** A one-request server that answers like SwapServer does. Returns its port and the request it saw. */
    private fun serveOnce(extraAfterBody: Boolean = false, status: Int = 0): Pair<Int, () -> List<String>> {
        val server = ServerSocket(0)
        val seen = mutableListOf<String>()
        val t = thread {
            server.accept().use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    seen += line
                }
                val range = seen.firstOrNull { it.startsWith("Range:") }
                    ?.let { Regex("""bytes=(\d+)-""").find(it)?.groupValues?.get(1)?.toInt() } ?: 0
                val body = payload.copyOfRange(range, payload.size)
                val code = if (status != 0) status else if (range > 0) 206 else 200
                val out = s.getOutputStream()
                out.write(
                    ("HTTP/1.1 $code X\r\nContent-Type: application/octet-stream\r\nContent-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                )
                out.write(body)
                if (extraAfterBody) out.write("JUNK".toByteArray())
                out.flush()
            }
            server.close()
        }
        return server.localPort to { t.join(5_000); seen }
    }

    @Test
    fun fetchesWholeBody() {
        val (port, seen) = serveOnce()
        LanHttp.get("127.0.0.1", port, "/manifest?t=abc").use { r ->
            assertEquals(200, r.code)
            assertEquals(payload.size.toLong(), r.contentLength)
            assertArrayEquals(payload, r.body.readBytes())
        }
        val req = seen()
        assertEquals("GET /manifest?t=abc HTTP/1.1", req.first())
        assertTrue(req.any { it == "Connection: close" })
    }

    @Test
    fun resumesWithRange() {
        val (port, seen) = serveOnce()
        LanHttp.get("127.0.0.1", port, "/file/0?t=abc", rangeFrom = 12_345).use { r ->
            assertEquals(206, r.code)
            assertArrayEquals(payload.copyOfRange(12_345, payload.size), r.body.readBytes())
        }
        assertTrue(seen().contains("Range: bytes=12345-"))
    }

    @Test
    fun neverReadsPastContentLength() {
        val (port, _) = serveOnce(extraAfterBody = true)
        LanHttp.get("127.0.0.1", port, "/x").use { r ->
            assertArrayEquals(payload, r.body.readBytes())
        }
    }

    @Test
    fun reportsStatusCodes() {
        val (port, _) = serveOnce(status = 403)
        LanHttp.get("127.0.0.1", port, "/manifest?t=wrong").use { r -> assertEquals(403, r.code) }
    }

    @Test
    fun splitsBaseAddress() {
        assertEquals("192.168.1.5" to 47821, SwapClient.hostPort("http://192.168.1.5:47821"))
    }
}
