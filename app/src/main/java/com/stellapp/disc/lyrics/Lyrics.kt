package com.stellapp.disc.lyrics

import android.content.Context
import android.net.Uri
import com.stellapp.disc.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

/** Either [synced] (time-stamped lines) or [plain] text. [source] says where they came from. */
data class Lyrics(val synced: List<LyricLine>?, val plain: String?, val source: String)

/** One result in the manual lyrics search. [text] is the LRC or plain text to save. */
data class LyricsHit(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val synced: Boolean,
    val text: String,
    val source: String,
)

object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val tag = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(text: String, source: String): Lyrics {
        val lines = ArrayList<LyricLine>()
        val plain = StringBuilder()
        text.lines().forEach { raw ->
            val line = raw.trim()
            val stamps = stamp.findAll(line).toList()
            if (stamps.isNotEmpty()) {
                val words = line.substring(stamps.last().range.last + 1).trim()
                stamps.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    lines += LyricLine(min * 60_000 + sec * 1000 + ms, words)
                }
            } else if (!tag.matches(line)) {
                plain.appendLine(raw)
            }
        }
        return if (lines.isNotEmpty()) {
            Lyrics(lines.sortedBy { it.timeMs }, null, source)
        } else {
            Lyrics(null, plain.toString().trim().ifBlank { null }, source)
        }
    }
}

/** Saved lyrics: pasted, imported or found online. One .lrc/.txt per song. */
object LyricsStore {
    private fun dir(context: Context) = File(context.filesDir, "lyrics").apply { mkdirs() }

    fun load(context: Context, songId: Long): String? =
        File(dir(context), "$songId.lrc").takeIf { it.exists() }?.readText()?.ifBlank { null }

    fun save(context: Context, songId: Long, text: String) {
        File(dir(context), "$songId.lrc").writeText(text)
    }

    fun delete(context: Context, songId: Long) {
        File(dir(context), "$songId.lrc").delete()
    }
}

/** Reads lyrics stored inside MP3 files (the ID3v2 USLT frame). */
object Id3Lyrics {

    fun read(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val input = DataInputStream(raw.buffered())
            val header = ByteArray(10)
            input.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return@use null
            val version = header[3].toInt()
            if (version < 3) return@use null
            val size = syncsafe(header, 6)
            if (size <= 0 || size > 16 * 1024 * 1024) return@use null
            val tag = ByteArray(size)
            input.readFully(tag)

            var pos = 0
            if (header[5].toInt() and 0x40 != 0) {
                pos = if (version >= 4) syncsafe(tag, 0) else int32(tag, 0) + 4
            }
            while (pos + 10 <= size) {
                if (tag[pos].toInt() == 0) break
                val id = String(tag, pos, 4, Charsets.US_ASCII)
                val frameSize = if (version >= 4) syncsafe(tag, pos + 4) else int32(tag, pos + 4)
                pos += 10
                if (frameSize <= 0 || pos + frameSize > size) break
                if (id == "USLT") return@use decodeUslt(tag, pos, frameSize)
                pos += frameSize
            }
            null
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeUslt(b: ByteArray, off: Int, len: Int): String? {
        if (len < 5) return null
        val enc = b[off].toInt()
        var p = off + 4 // encoding byte + 3-letter language
        val end = off + len
        val wide = enc == 1 || enc == 2
        // Skip the content descriptor (null-terminated).
        if (wide) {
            while (p + 1 < end && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2
            p += 2
        } else {
            while (p < end && b[p].toInt() != 0) p++
            p += 1
        }
        if (p >= end) return null
        val charset: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(b, p, end - p, charset).trim('\u0000', ' ', '\n', '\r').ifBlank { null }
    }

    private fun syncsafe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0x7f shl 21) or (b[o + 1].toInt() and 0x7f shl 14) or (b[o + 2].toInt() and 0x7f shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun int32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff shl 24) or (b[o + 1].toInt() and 0xff shl 16) or (b[o + 2].toInt() and 0xff shl 8) or (b[o + 3].toInt() and 0xff)
}

/** Looks lyrics up on lrclib.net, a free and open lyrics database. Only called when allowed. */
object LrcLib {
    private val bracket = Regex("""\s*[(\[][^)\]]*[)\]]""")

    suspend fun find(title: String, artist: String, album: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            lookup(title, artist, album, durationMs)
        } catch (e: Exception) {
            null
        }
    }

    private fun lookup(title: String, artist: String, album: String, durationMs: Long): String? {
        val cleanTitle = title.replace(bracket, "").trim()
        val cleanArtist = if (artist.equals("Unknown artist", true)) "" else artist
        // Exact lookup first, then a search.
        val exact = buildString {
            append("https://lrclib.net/api/get?track_name=").append(enc(cleanTitle))
            append("&artist_name=").append(enc(cleanArtist))
            if (album.isNotBlank() && !album.equals("Unknown album", true)) append("&album_name=").append(enc(album))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000)
        }
        get(exact)?.let { body -> pick(JSONObject(body))?.let { return it } }
        val search = "https://lrclib.net/api/search?track_name=${enc(cleanTitle)}" +
            if (cleanArtist.isNotBlank()) "&artist_name=${enc(cleanArtist)}" else ""
        get(search)?.let { body ->
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                pick(arr.getJSONObject(i))?.let { return it }
            }
        }
        return null
    }

    /** Every match for a free search, for the manual picker (synced ones first). */
    suspend fun search(title: String, artist: String): List<LyricsHit> = withContext(Dispatchers.IO) {
        try {
            val t = title.trim()
            val a = artist.trim().takeUnless { it.equals("Unknown artist", true) } ?: ""
            val url = if (a.isBlank()) "https://lrclib.net/api/search?q=${enc(t)}"
            else "https://lrclib.net/api/search?track_name=${enc(t)}&artist_name=${enc(a)}"
            var body = get(url)
            // Nothing for that exact pair: try it as one loose search.
            if (a.isNotBlank() && (body == null || JSONArray(body).length() == 0)) body = get("https://lrclib.net/api/search?q=${enc("$a $t")}")
            if (body == null) return@withContext emptyList()
            val arr = JSONArray(body)
            val out = ArrayList<LyricsHit>()
            for (i in 0 until minOf(arr.length(), 25)) {
                val o = arr.getJSONObject(i)
                val text = pick(o) ?: continue
                val synced = o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }
                out += LyricsHit(
                    title = o.optString("trackName"),
                    artist = o.optString("artistName"),
                    album = o.optString("albumName").takeIf { it != "null" } ?: "",
                    durationSec = o.optDouble("duration", 0.0).toInt(),
                    synced = synced,
                    text = text,
                    source = "lrclib.net",
                )
            }
            out.sortedByDescending { it.synced }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun pick(o: JSONObject): String? =
        o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            ?: o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

    internal fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", AppInfo.USER_AGENT)
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    internal fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/**
 * The backup: lyrics.ovh, a free lyrics API (plain text only, no timestamps). Asked only when
 * lrclib.net has nothing, and only when looking online is allowed.
 */
object LyricsOvh {
    private val bracket = Regex("""\s*[(\[][^)\]]*[)\]]""")

    suspend fun find(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        try {
            val t = title.replace(bracket, "").trim()
            val a = artist.trim()
            if (t.isBlank() || a.isBlank() || a.equals("Unknown artist", true)) return@withContext null
            // The path takes the words as path segments, so spaces are %20, not +.
            val url = "https://api.lyrics.ovh/v1/${LrcLib.enc(a).replace("+", "%20")}/${LrcLib.enc(t).replace("+", "%20")}"
            val body = LrcLib.get(url) ?: return@withContext null
            JSONObject(body).optString("lyrics")
                .replace("\r\n", "\n")
                .trim()
                .takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) {
            null
        }
    }
}
