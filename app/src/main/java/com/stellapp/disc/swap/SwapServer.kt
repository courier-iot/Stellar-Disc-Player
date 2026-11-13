package com.stellapp.disc.swap

import android.content.Context
import android.webkit.MimeTypeMap
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
/** What a "move to a new phone" share adds on top of a normal one. */
class MoveExtras(
    /** Builds the backup file (playlists, favourites, play counts, settings). */
    val backup: () -> String,
    val playlists: List<Pair<String, List<Long>>>,
    val favIds: Set<Long>,
    val plays: Map<Long, Int>,
)

class SwapServer(
    private val context: Context,
    private val name: String,
    private val tracks: List<Song>,
    /** Non-null when the whole library is moving to a new phone. */
    private val move: MoveExtras? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private var manifestJson: ByteArray = ByteArray(0)
    private val token: String = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    /** Encrypts everything sent; only the QR code carries it. */
    private val key: ByteArray = SwapCrypto.newKey()

    private val _sent = MutableStateFlow(0)
    /** Files sent so far. */
    val sent: StateFlow<Int> = _sent.asStateFlow()

    private val _peers = MutableStateFlow<Set<String>>(emptySet())
    /** Addresses of phones that have connected. */
    val peers: StateFlow<Set<String>> = _peers.asStateFlow()

    var lastError: String? = null
        private set

    private val _expired = MutableStateFlow(false)
    /** True once sharing switched itself off after [IDLE_LIMIT_MS] with nothing happening. */
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    @Volatile private var lastActivity = System.currentTimeMillis()
    private val activeTransfers = java.util.concurrent.atomic.AtomicInteger(0)
    /** Wrong-code attempts per address; after [MAX_BAD_TRIES] that address is ignored. */
    private val badTries = java.util.concurrent.ConcurrentHashMap<String, Int>()

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
            lastActivity = System.currentTimeMillis()
            scope.launch { acceptLoop(socket) }
            // Switch sharing off by itself if nobody uses it for a while.
            scope.launch {
                while (isActive && !socket.isClosed) {
                    delay(15_000)
                    if (activeTransfers.get() == 0 && System.currentTimeMillis() - lastActivity > IDLE_LIMIT_MS) {
                        _expired.value = true
                        stop()
                    }
                }
            }
            val key64 = android.util.Base64.encodeToString(key, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            Pairing(socket.localPort, token, ips, name, key64).encode()
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
            // Fingerprinting thousands of files would keep the new phone waiting; a move matches
            // by size and tags instead (see SongMatcher).
            val hashed = if (move != null) null else QuickHash.ofUri(context, s.uri)
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
                fav = move?.favIds?.contains(s.id) == true,
                plays = move?.plays?.get(s.id) ?: 0,
            )
        }
        if (move == null) return RemoteManifest(name, list)
        val indexOf = tracks.withIndex().associate { (i, s) -> s.id to i }
        val groups = move.playlists.mapNotNull { (n, ids) ->
            val idx = ids.mapNotNull { indexOf[it] }
            if (idx.isEmpty()) null else RemoteGroup(n, idx)
        }
        return RemoteManifest(name, list, kind = RemoteManifest.KIND_MOVE, groups = groups)
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
                val addr = s.inetAddress?.hostAddress ?: "?"
                if ((badTries[addr] ?: 0) >= MAX_BAD_TRIES) return
                val requestLine = readLine(input) ?: return
                var rangeFrom = 0L
                while (true) {
                    val header = readLine(input) ?: break
                    if (header.isEmpty()) break
                    // "Range: bytes=12345-" lets the other phone pick up a transfer where it broke off.
                    if (header.startsWith("Range:", ignoreCase = true)) {
                        rangeFrom = Regex("""bytes=(\d+)-""").find(header)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    }
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

                val given = (params["t"] ?: "").toByteArray()
                if (!java.security.MessageDigest.isEqual(given, token.toByteArray())) {
                    badTries.merge(addr, 1) { a, b -> a + b }
                    respond(out, 403, "text/plain", "Wrong code".toByteArray())
                    return
                }
                lastActivity = System.currentTimeMillis()
                _peers.update { it + addr }

                when {
                    path == "/manifest" -> respond(out, 200, "application/octet-stream", SwapCrypto.seal(key, manifestJson))
                    path == "/backup" && move != null -> {
                        val text = try { move.backup() } catch (e: Exception) { "" }
                        respond(out, 200, "application/octet-stream", SwapCrypto.seal(key, text.toByteArray(Charsets.UTF_8)))
                    }
                    path.startsWith("/file/") -> {
                        val song = path.removePrefix("/file/").toIntOrNull()?.let { tracks.getOrNull(it) }
                        if (song == null) respond(out, 404, "text/plain", "No such track".toByteArray())
                        else {
                            activeTransfers.incrementAndGet()
                            try {
                                val index = path.removePrefix("/file/").toInt()
                                if (sendFile(out, song, rangeFrom, index)) _sent.update { it + 1 }
                            } finally {
                                activeTransfers.decrementAndGet()
                                lastActivity = System.currentTimeMillis()
                            }
                        }
                    }
                    else -> respond(out, 404, "text/plain", "Not found".toByteArray())
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sendFile(out: OutputStream, song: Song, from: Long, index: Int): Boolean {
        val pfd = context.contentResolver.openFileDescriptor(song.uri, "r") ?: run {
            respond(out, 404, "text/plain", "Missing file".toByteArray())
            return false
        }
        pfd.use { fd ->
            val length = fd.statSize
            val mime = try { context.contentResolver.getType(song.uri) } catch (e: Exception) { null }
                ?: "application/octet-stream"
            val start = from.coerceIn(0L, length)
            out.write(header(if (start > 0) 206 else 200, mime, length - start, if (start > 0) "bytes $start-${length - 1}/$length" else null))
            FileInputStream(fd.fileDescriptor).use { input ->
                var skip = start
                while (skip > 0) {
                    val n = input.skip(skip)
                    if (n <= 0) break
                    skip -= n
                }
                val cipher = SwapCrypto.fileCipher(key, index, start)
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(cipher.update(buf, 0, n))
                }
            }
            out.flush()
        }
        return true
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        out.write(header(code, type, body.size.toLong()))
        out.write(body)
        out.flush()
    }

    private fun header(code: Int, type: String, length: Long, contentRange: String? = null): ByteArray {
        val reason = when (code) {
            200 -> "OK"
            206 -> "Partial Content"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val sb = StringBuilder()
            .append("HTTP/1.1 $code $reason\r\n")
            .append("Content-Type: $type\r\n")
        if (length >= 0) sb.append("Content-Length: $length\r\n")
        if (contentRange != null) sb.append("Content-Range: $contentRange\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
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
        /** Sharing turns itself off after 10 minutes with no requests. */
        const val IDLE_LIMIT_MS = 10 * 60 * 1000L
        const val MAX_BAD_TRIES = 5

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
            // Every private address, Wi-Fi and hotspot ones first. Interface names differ by phone
            // maker, so nothing is thrown away: the other phone simply tries them in order.
            return all.sortedBy { rank(it.name) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
                .take(4)
        }
    }
}
