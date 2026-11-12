package com.stellapp.disc.swap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /**
     * Downloads one track. If Wi-Fi blips, it waits a moment and carries on from where it
     * stopped (up to [RETRIES] times) instead of starting over.
     */
    suspend fun fetchFile(
        base: String,
        token: String,
        track: RemoteTrack,
        dest: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (dest.exists()) dest.delete()
        var attempt = 0
        while (true) {
            ensureActive()
            try {
                fetchOnce(base, token, track, dest, onProgress)
                return@withContext
            } catch (e: IOException) {
                attempt++
                if (attempt > RETRIES) throw e
                delay(1_500L * attempt)
            }
        }
    }

    private suspend fun fetchOnce(base: String, token: String, track: RemoteTrack, dest: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val have = if (dest.exists()) dest.length() else 0L
            val conn = URL("$base/file/${track.index}?t=$token").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 30_000
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            try {
                val code = conn.responseCode
                if (code != 200 && code != 206) throw IOException("The other phone said $code")
                val resuming = code == 206 && have > 0
                val total = if (resuming) have + conn.contentLengthLong.coerceAtLeast(0L) else (conn.contentLengthLong.takeIf { it > 0 } ?: track.size)
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(dest, resuming).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = if (resuming) have else 0L
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

    private const val RETRIES = 4

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
