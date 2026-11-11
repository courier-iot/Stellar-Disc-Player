package com.stellapp.disc.swap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Talks to another phone's [SwapServer]. */
object SwapClient {

    /** Tries each address in the QR code. Returns the working base URL and the track list. */
    suspend fun connect(p: Pairing): Pair<String, RemoteManifest>? = withContext(Dispatchers.IO) {
        for (ip in p.ips) {
            val base = "http://$ip:${p.port}"
            try {
                val text = getText("$base/manifest?t=${p.token}")
                return@withContext base to parseManifest(text)
            } catch (_: Exception) {
            }
        }
        null
    }

    suspend fun fetchFile(
        base: String,
        token: String,
        track: RemoteTrack,
        dest: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val conn = URL("$base/file/${track.index}?t=$token").openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IOException("The other phone said ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: track.size
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 256 * 1024) {
                            lastReport = read
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    if (total > 0 && read < total) throw IOException("Transfer cut off")
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }

    private fun getText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 2_500
        conn.readTimeout = 60_000 // the other phone fingerprints its files first
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
