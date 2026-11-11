package com.stellapp.disc.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class MetaOverride(val title: String? = null, val artist: String? = null, val album: String? = null)

/**
 * The tag editor's storage. Edits live inside Stellar Disc and are applied on top of the
 * file's own tags. The music files themselves are never rewritten.
 */
object MetaStore {
    private const val FILE_NAME = "meta.json"
    private val lock = Any()
    private var file: File? = null
    private var coversDir: File? = null

    private val _overrides = MutableStateFlow<Map<Long, MetaOverride>>(emptyMap())
    val overrides: StateFlow<Map<Long, MetaOverride>> = _overrides.asStateFlow()

    private val _coverVersion = MutableStateFlow(0)
    /** Bumps whenever a custom cover changes, so artwork reloads. */
    val coverVersion: StateFlow<Int> = _coverVersion.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            coversDir = File(context.applicationContext.filesDir, "covers").apply { mkdirs() }
            _overrides.value = try {
                if (!f.exists()) emptyMap() else {
                    val o = JSONObject(f.readText())
                    o.keys().asSequence().mapNotNull { k ->
                        val id = k.toLongOrNull() ?: return@mapNotNull null
                        val m = o.getJSONObject(k)
                        id to MetaOverride(
                            title = m.optString("title").ifBlank { null },
                            artist = m.optString("artist").ifBlank { null },
                            album = m.optString("album").ifBlank { null },
                        )
                    }.toMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    fun apply(s: Song): Song {
        val o = _overrides.value[s.id] ?: return s
        return s.copy(title = o.title ?: s.title, artist = o.artist ?: s.artist, album = o.album ?: s.album)
    }

    fun set(id: Long, o: MetaOverride) = mutate { it + (id to o) }

    fun clear(id: Long) = mutate { it - id }

    fun coverFile(artKey: Long): File? = coversDir?.let { File(it, "$artKey.jpg") }

    fun hasCover(artKey: Long): Boolean = coverFile(artKey)?.exists() == true

    /** Saves a picked image (scaled to at most 1024px) as the cover for [artKey]. */
    suspend fun setCover(context: Context, artKey: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = coverFile(artKey) ?: return@withContext false
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            _coverVersion.update { it + 1 }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearCover(artKey: Long) {
        coverFile(artKey)?.delete()
        _coverVersion.update { it + 1 }
    }

    private fun mutate(change: (Map<Long, MetaOverride>) -> Map<Long, MetaOverride>) {
        synchronized(lock) {
            val next = change(_overrides.value)
            _overrides.value = next
            val f = file ?: return
            try {
                val o = JSONObject()
                next.forEach { (id, m) ->
                    o.put(id.toString(), JSONObject().put("title", m.title ?: "").put("artist", m.artist ?: "").put("album", m.album ?: ""))
                }
                f.writeText(o.toString())
            } catch (_: Exception) {
            }
        }
    }
}
