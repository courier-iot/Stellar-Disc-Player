package com.stellapp.disc.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.stellapp.disc.data.MetaStore
import com.stellapp.disc.data.MusicRepository
import com.stellapp.disc.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Album art is cached per album so scrolling stays smooth. */
object ArtCache {
    private val cache = LruCache<Long, ImageBitmap>(96)
    private val missing: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @Volatile private var seenVersion = -1

    /** Custom covers changed: forget everything cached. */
    private fun checkVersion() {
        val v = MetaStore.coverVersion.value
        if (v != seenVersion) {
            seenVersion = v
            cache.evictAll()
            missing.clear()
        }
    }

    fun peek(key: Long): ImageBitmap? {
        checkVersion()
        return cache.get(key)
    }

    suspend fun load(context: Context, song: Song): ImageBitmap? = withContext(Dispatchers.IO) {
        if (song.uri == Uri.EMPTY || song.isStream) return@withContext null
        if (!song.isVideo && song.albumId < 0 && !MetaStore.hasCover(song.artKey)) return@withContext null
        val key = song.artKey
        checkVersion()
        cache.get(key)?.let { return@withContext it }
        if (key in missing) return@withContext null

        val custom = MetaStore.coverFile(key)?.takeIf { it.exists() }
        val bmp = try {
            if (custom != null) {
                BitmapFactory.decodeFile(custom.absolutePath)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
            } else if (song.isVideo) {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver, song.id, MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            } else {
                context.contentResolver.openInputStream(MusicRepository.albumArtUri(song.albumId))
                    ?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        }

        if (bmp == null) {
            missing.add(key)
            null
        } else {
            bmp.asImageBitmap().also { cache.put(key, it) }
        }
    }
}

@Composable
fun rememberAlbumArt(song: Song?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val key = song?.artKey ?: Long.MIN_VALUE
    val version by MetaStore.coverVersion.collectAsState()
    val art by produceState<ImageBitmap?>(initialValue = song?.let { ArtCache.peek(it.artKey) }, key, version) {
        value = if (song == null) null else ArtCache.load(context, song)
    }
    return art
}
