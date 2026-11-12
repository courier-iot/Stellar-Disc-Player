package com.stellapp.disc.swap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

/** Talks to another phone's [SwapServer]. */
object SwapClient {

    /** Why the last [connect] failed, in plain words. Null if it worked. */
    @Volatile var lastProblem: String? = null
        private set

    /** Tries each address in the QR code. Returns the working base URL and the track list. */
    suspend fun connect(p: Pairing): Pair<String, RemoteManifest>? = withContext(Dispatchers.IO) {
        lastProblem = null
        val problems = mutableListOf<String>()
        for (ip in p.ips) {
            val base = "http://$ip:${p.port}"
            try {
                val raw = getBytes(base, "/manifest?t=${p.token}")
                val key = p.keyBytes()
                val text = if (key != null) String(SwapCrypto.open(key, raw), Charsets.UTF_8) else String(raw, Charsets.UTF_8)
                return@withContext base to parseManifest(text)
            } catch (e: Exception) {
                problems += "$ip: ${explain(e)}"
            }
        }
        lastProblem = problems.joinToString("\n").ifEmpty { "The QR code has no addresses in it." }
        null
    }

    private fun explain(e: Exception): String = when (e) {
        is SocketTimeoutException -> "no answer (different network, or the router keeps devices apart)"
        is NoRouteToHostException -> "not reachable from this network"
        is ConnectException -> "refused (their share screen may have closed)"
        is javax.crypto.AEADBadTagException -> "the code didn't match (scan a fresh one)"
        else -> e.message ?: e.javaClass.simpleName
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
        key: ByteArray?,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (dest.exists()) dest.delete()
        var attempt = 0
        while (true) {
            ensureActive()
            try {
                fetchOnce(base, token, track, dest, key, onProgress)
                return@withContext
            } catch (e: IOException) {
                attempt++
                if (attempt > RETRIES) throw e
                delay(1_500L * attempt)
            }
        }
    }

    private suspend fun fetchOnce(base: String, token: String, track: RemoteTrack, dest: File, key: ByteArray?, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val have = if (dest.exists()) dest.length() else 0L
            val (host, port) = hostPort(base)
            LanHttp.get(host, port, "/file/${track.index}?t=$token", rangeFrom = have).use { conn ->
                val code = conn.code
                if (code != 200 && code != 206) throw IOException("The other phone said $code")
                val resuming = code == 206 && have > 0
                val total = if (resuming) have + conn.contentLength.coerceAtLeast(0L) else (conn.contentLength.takeIf { it > 0 } ?: track.size)
                conn.body.let { input ->
                    java.io.FileOutputStream(dest, resuming).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = if (resuming) have else 0L
                        val cipher = key?.let { SwapCrypto.fileCipher(it, track.index, read) }
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            if (cipher != null) out.write(cipher.update(buf, 0, n)) else out.write(buf, 0, n)
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
            }
        }

    private const val RETRIES = 4

    /** "http://192.168.1.5:47821" → ("192.168.1.5", 47821). */
    internal fun hostPort(base: String): Pair<String, Int> {
        val hp = base.substringAfter("://").substringBefore("/")
        val host = hp.substringBeforeLast(":")
        val port = hp.substringAfterLast(":").toIntOrNull() ?: throw IOException("Bad address: $base")
        return host to port
    }

    private fun getBytes(base: String, pathAndQuery: String): ByteArray {
        val (host, port) = hostPort(base)
        // The other phone fingerprints its files before it answers, so allow a long first read.
        LanHttp.get(host, port, pathAndQuery, connectTimeoutMs = 4_000, readTimeoutMs = 60_000).use { r ->
            if (r.code != 200) throw IOException(if (r.code == 403) "the code didn't match (scan a fresh one)" else "the other phone said ${r.code}")
            return r.body.readBytes()
        }
    }
}
