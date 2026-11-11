package com.stellapp.disc.swap

import android.content.Context
import android.webkit.MimeTypeMap
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * A tiny web server that offers one playlist to phones on the same Wi-Fi (or hotspot).
 * Every request must carry the one-time token from the QR code.
 */
class SwapServer(
    private val context: Context,
    private val name: String,
    private val tracks: List<Song>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private var manifestJson: ByteArray = ByteArray(0)
    private val token: String = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private val _sent = MutableStateFlow(0)
    /** Files sent so far. */
    val sent: StateFlow<Int> = _sent.asStateFlow()

    private val _peers = MutableStateFlow<Set<String>>(emptySet())
    /** Addresses of phones that have connected. */
    val peers: StateFlow<Set<String>> = _peers.asStateFlow()

    var lastError: String? = null
        private set

    /** Prepares the track list and starts listening. Returns the QR code text, or null on failure. */
    suspend fun start(): String? = withContext(Dispatchers.IO) {
        try {
            val ips = localIps()
            if (ips.isEmpty()) {
                lastError = "Connect to Wi-Fi or turn on your hotspot first."
                return@withContext null
            }
            LinkStore.init(context)
            manifestJson = buildManifest().toJson().toByteArray(Charsets.UTF_8)
            val socket = try {
                ServerSocket(PREFERRED_PORT)
            } catch (e: Exception) {
                ServerSocket(0)
            }
            server = socket
            scope.launch { acceptLoop(socket) }
            Pairing(socket.localPort, token, ips, name).encode()
        } catch (e: Exception) {
            lastError = "Couldn't start sharing: ${e.message ?: "unknown error"}"
            null
        }
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        scope.cancel()
    }

    private fun buildManifest(): RemoteManifest {
        val resolver = context.contentResolver
        val list = tracks.mapIndexed { i, s ->
            val hashed = QuickHash.ofUri(context, s.uri)
            val mime = try { resolver.getType(s.uri) } catch (e: Exception) { null }
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: if (s.isVideo) "mp4" else "mp3"
            RemoteTrack(
                index = i,
                title = s.title,
                artist = s.artist,
                album = s.album,
                durationMs = s.durationMs,
                size = hashed?.second ?: s.size,
                hash = hashed?.first,
                ext = ext,
                isVideo = s.isVideo,
                link = LinkStore.get(s.id),
            )
        }
        return RemoteManifest(name, list)
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (scope.isActive && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                break
            }
            scope.launch { handle(client) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 20_000
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                while (true) {
                    val header = readLine(input) ?: break
                    if (header.isEmpty()) break
                }
                val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    respond(out, 405, "text/plain", "GET only".toByteArray())
                    return
                }
                val target = parts[1]
                val path = target.substringBefore("?")
                val params = target.substringAfter("?", "").split("&").mapNotNull { kv ->
                    val p = kv.split("=", limit = 2)
                    if (p.size == 2) p[0] to URLDecoder.decode(p[1], "UTF-8") else null
                }.toMap()

                if (params["t"] != token) {
                    respond(out, 403, "text/plain", "Wrong code".toByteArray())
                    return
                }
                s.inetAddress?.hostAddress?.let { addr -> _peers.update { it + addr } }

                when {
                    path == "/manifest" -> respond(out, 200, "application/json; charset=utf-8", manifestJson)
                    path.startsWith("/file/") -> {
                        val song = path.removePrefix("/file/").toIntOrNull()?.let { tracks.getOrNull(it) }
                        if (song == null) respond(out, 404, "text/plain", "No such track".toByteArray())
                        else if (sendFile(out, song)) _sent.update { it + 1 }
                    }
                    else -> respond(out, 404, "text/plain", "Not found".toByteArray())
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sendFile(out: OutputStream, song: Song): Boolean {
        val pfd = context.contentResolver.openFileDescriptor(song.uri, "r") ?: run {
            respond(out, 404, "text/plain", "Missing file".toByteArray())
            return false
        }
        pfd.use { fd ->
            val length = fd.statSize
            val mime = try { context.contentResolver.getType(song.uri) } catch (e: Exception) { null }
                ?: "application/octet-stream"
            out.write(header(200, mime, length))
            FileInputStream(fd.fileDescriptor).use { it.copyTo(out, 64 * 1024) }
            out.flush()
        }
        return true
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        out.write(header(code, type, body.size.toLong()))
        out.write(body)
        out.flush()
    }

    private fun header(code: Int, type: String, length: Long): ByteArray {
        val reason = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val sb = StringBuilder()
            .append("HTTP/1.1 $code $reason\r\n")
            .append("Content-Type: $type\r\n")
        if (length >= 0) sb.append("Content-Length: $length\r\n")
        sb.append("Connection: close\r\n\r\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    companion object {
        const val PREFERRED_PORT = 47821

        /** Wi-Fi and hotspot addresses this phone can be reached on, best first. */
        fun localIps(): List<String> {
            val all = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            } catch (e: Exception) {
                emptyList()
            }.filter {
                try {
                    it.isUp && !it.isLoopback
                } catch (e: Exception) {
                    false
                }
            }
            val wifiLike = listOf("wlan", "ap", "swlan", "softap", "eth", "p2p", "rndis", "bt-pan")
            fun rank(name: String) = wifiLike.indexOfFirst { name.startsWith(it) }.let { if (it < 0) 99 else it }
            val ranked = all.sortedBy { rank(it.name) }
            val preferred = ranked.filter { rank(it.name) < 99 }.ifEmpty { ranked }
            return preferred.flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
        }
    }
}
