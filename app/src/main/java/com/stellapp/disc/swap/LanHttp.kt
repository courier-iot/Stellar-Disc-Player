package com.stellapp.disc.swap

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A tiny HTTP/1.1 GET client for talking to another phone's [SwapServer] over the local network.
 *
 * Why not HttpURLConnection: Android blocks plain `http://` for apps targeting Android 9+, so
 * every Swap connection was refused before it left the phone. Swap data is already end-to-end
 * encrypted (see [SwapCrypto]), so a direct socket is safe and needs no app-wide exception.
 */
internal object LanHttp {

    class Response(
        val code: Int,
        /** Header names in lower case. */
        val headers: Map<String, String>,
        val body: InputStream,
        private val socket: Socket,
    ) : Closeable {
        /** Content-Length, or -1 if the server didn't say. */
        val contentLength: Long get() = headers["content-length"]?.trim()?.toLongOrNull() ?: -1L

        override fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Sends `GET [pathAndQuery]` to [host]:[port]. [rangeFrom] > 0 asks for the rest of a file.
     * The caller must close the response.
     */
    fun get(
        host: String,
        port: Int,
        pathAndQuery: String,
        rangeFrom: Long = 0L,
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 30_000,
    ): Response {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            socket.tcpNoDelay = true
            val request = StringBuilder()
                .append("GET ").append(pathAndQuery).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append(':').append(port).append("\r\n")
                .apply { if (rangeFrom > 0) append("Range: bytes=").append(rangeFrom).append("-\r\n") }
                .append("Connection: close\r\n\r\n")
                .toString()
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val status = readLine(input) ?: throw IOException("The other phone closed the connection")
            // "HTTP/1.1 200 OK"
            val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Unexpected reply: $status")
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val length = headers["content-length"]?.trim()?.toLongOrNull() ?: -1L
            val body = if (length >= 0) Limited(input, length) else input
            return Response(code, headers, body, socket)
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            throw e as? IOException ?: IOException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) throw IOException("Header line too long")
        }
    }

    /** Stops after [left] bytes, so a body is never read past its stated length. */
    private class Limited(input: InputStream, private var left: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (left <= 0) return -1
            val b = super.read()
            if (b >= 0) left--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }

        override fun available(): Int = minOf(super.available().toLong(), left).toInt()
    }
}
