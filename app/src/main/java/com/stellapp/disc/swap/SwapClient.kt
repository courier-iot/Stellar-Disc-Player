package com.stellapp.disc.swap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import android.content.Context
import android.net.ConnectivityManager
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

/** Talks to another phone's [SwapServer]. */
object SwapClient {

    /** Why the last [connect] failed, in plain words. Null if it worked. */
    @Volatile var lastProblem: String? = null
        private set

    /**
     * Android sends app traffic over mobile data when it thinks Wi-Fi has "no internet", which is
     * exactly what a phone hotspot without data looks like. This finds the network the other phone
     * actually lives on and pins the socket to it. Null means "let Android route it" (also right on
     * the phone that hosts the hotspot, whose hotspot isn't a normal network).
     */
    private fun binderFor(context: Context, host: String): ((Socket) -> Unit)? {
        return try {
            val target = InetAddress.getByName(host) as? Inet4Address ?: return null
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val network = cm.allNetworks.firstOrNull { n ->
                val lp = cm.getLinkProperties(n) ?: return@firstOrNull false
                lp.linkAddresses.any { la -> sameSubnet(la.address, la.prefixLength, target) } ||
                    lp.routes.any { r -> !r.isDefaultRoute && r.matches(target) }
            } ?: return null
            val pin: (Socket) -> Unit = { s -> network.bindSocket(s) }
            pin
        } catch (_: Exception) {
            null
        }
    }

    internal fun sameSubnet(addr: InetAddress, prefix: Int, target: Inet4Address): Boolean {
        if (addr !is Inet4Address || prefix !in 1..32) return false
        val a = addr.address
        val b = target.address
        var bits = prefix
        for (i in 0 until 4) {
            val take = bits.coerceIn(0, 8)
            if (take == 0) break
            val mask = (0xFF shl (8 - take)) and 0xFF
            if ((a[i].toInt() and mask) != (b[i].toInt() and mask)) return false
            bits -= 8
        }
        return true
    }

    /** Tries each address in the QR code. Returns the working base URL and the track list. */
    suspend fun connect(context: Context, p: Pairing): Pair<String, RemoteManifest>? = withContext(Dispatchers.IO) {
        lastProblem = null
        appContext = context.applicationContext
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
            LanHttp.get(host, port, "/file/${track.index}?t=$token", rangeFrom = have, bind = binder(host)).use { conn ->
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

    @Volatile private var appContext: Context? = null

    private fun binder(host: String): ((Socket) -> Unit)? = appContext?.let { binderFor(it, host) }

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
        LanHttp.get(host, port, pathAndQuery, connectTimeoutMs = 4_000, readTimeoutMs = 60_000, bind = binder(host)).use { r ->
            if (r.code != 200) throw IOException(if (r.code == 403) "the code didn't match (scan a fresh one)" else "the other phone said ${r.code}")
            return r.body.readBytes()
        }
    }
}
