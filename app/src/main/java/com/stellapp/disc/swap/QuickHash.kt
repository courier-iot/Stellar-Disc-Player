package com.stellapp.disc.swap

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * A fast file fingerprint: SHA-256 of the size plus the first and last 64 KB.
 * Identical files always match; different files practically never do.
 * Reading 128 KB instead of the whole file keeps the song checker quick.
 */
object QuickHash {
    private const val CHUNK = 64 * 1024L

    fun of(channel: FileChannel, size: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(size.toString().toByteArray())
        readAt(channel, 0L, minOf(CHUNK, size), md)
        if (size > CHUNK) {
            val start = maxOf(CHUNK, size - CHUNK)
            readAt(channel, start, size - start, md)
        }
        return md.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    fun ofUri(context: Context, uri: Uri): Pair<String, Long>? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val size = pfd.statSize
                of(fis.channel, size) to size
            }
        }
    } catch (e: Exception) {
        null
    }

    fun ofFile(file: File): String = FileInputStream(file).use { of(it.channel, file.length()) }

    private fun readAt(channel: FileChannel, position: Long, length: Long, md: MessageDigest) {
        if (length <= 0) return
        val buf = ByteBuffer.allocate(length.toInt())
        var pos = position
        while (buf.hasRemaining()) {
            val n = channel.read(buf, pos)
            if (n <= 0) break
            pos += n
        }
        buf.flip()
        md.update(buf)
    }
}
