package com.stellapp.disc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stellapp.disc.ui.theme.Void
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Where the centre disc sits on screen, so photo backdrops can line up with it. */
data class DiscAnchor(val center: Offset, val radius: Float)

enum class PhotoFit {
    /** Fill the screen, cropping the sides. */
    COVER,
    /** Fit the screen width, centred on the disc, with [PhotoSpec.bg] above and below. */
    FIT_WIDTH,
    /** Scale so the feature (e.g. the eclipsed Moon) is exactly the disc's size and sits behind it. */
    ANCHOR,
}

enum class PhotoFx { NONE, SPACE, RAIN, NEON, GRAIN, ZOOM, CORONA, GLARE }

/**
 * A real image backdrop. [full] is the phone-size image, [thumb] the small Settings preview.
 * [drift] (0..1) slowly pans across any extra width. Feature values are in [full]'s pixels.
 */
data class PhotoSpec(
    val full: Int,
    val thumb: Int,
    val fit: PhotoFit = PhotoFit.COVER,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val drift: Float = 0f,
    val fx: PhotoFx = PhotoFx.NONE,
    val bg: Color = Color.Black,
    val featureX: Float = 0f,
    val featureY: Float = 0f,
    val featureR: Float = 1f,
)

private object PhotoCache {
    val full = LruCache<String, ImageBitmap>(2)
    val thumbs = LruCache<String, ImageBitmap>(40)
}

/** Lets the app drop cached backdrop images when Android is short on memory. */
object BackdropMemory {
    fun trim(level: Int) {
        PhotoCache.thumbs.evictAll()
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) PhotoCache.full.evictAll()
    }
}

/** Photos have no transparency, so 16-bit colour halves their memory with no visible change. */
private fun lean() = BitmapFactory.Options().apply {
    inScaled = false
    inPreferredConfig = Bitmap.Config.RGB_565
}

/** Loads an image off the main thread, keeping a couple of recent ones in memory. */
@Composable
internal fun rememberPhoto(key: String, thumbnail: Boolean, load: (Context) -> Bitmap?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val cache = if (thumbnail) PhotoCache.thumbs else PhotoCache.full
    var img by remember(key) { mutableStateOf(cache.get(key)) }
    LaunchedEffect(key) {
        if (img == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    load(context)?.asImageBitmap()
                } catch (e: Throwable) {
                    null
                }
            }
            if (loaded != null) cache.put(key, loaded)
            img = loaded
        }
    }
    return img
}

internal fun decodeResource(context: Context, id: Int): Bitmap? =
    BitmapFactory.decodeResource(context.resources, id, lean())

/** The "Your photo" backdrop: one picture copied into the app's own storage. */
object CustomBackdrop {
    private val _version = MutableStateFlow(-1L)
    /** Changes whenever a new photo is picked; 0 means there's no photo yet. */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun file(context: Context): File = File(context.applicationContext.filesDir, "backdrop_custom.jpg")

    fun refresh(context: Context) {
        val f = file(context)
        _version.value = if (f.exists() && f.length() > 0) f.lastModified() else 0L
    }

    /** Copies the picked image in, shrunk to phone size (and turned the right way up). */
    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext
            val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                    val long = max(info.size.width, info.size.height)
                    if (long > 2400) {
                        val k = 2400f / long
                        decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
                app.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@withContext false
            }
            file(app).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            refresh(app)
            true
        } catch (e: Throwable) {
            false
        }
    }

    internal fun decodeThumb(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 400) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, lean().apply { inSampleSize = sample })
    }

    internal fun decodeFull(context: Context): Bitmap? {
        val f = file(context)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, lean()) else null
    }
}

/** Draws a photo backdrop (plus its little bit of life: rain, glints, flicker…) and a legibility scrim. */
internal fun DrawScope.drawPhotoBackdrop(
    img: ImageBitmap,
    spec: PhotoSpec,
    anchor: DiscAnchor?,
    t: Float,
    thumbnail: Boolean,
    stars: List<Star>,
) {
    val w = size.width
    val h = size.height
    val a = anchor ?: DiscAnchor(Offset(w / 2f, h * 0.38f), w * 0.30f)
    val iw = img.width.toFloat()
    val ih = img.height.toFloat()
    drawRect(spec.bg)

    if (thumbnail) {
        // Thumbnails are pre-cropped to the right shape.
        val s = max(w / iw, h / ih)
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((w - iw * s) / 2).roundToInt(), ((h - ih * s) / 2).roundToInt()), IntSize((iw * s).roundToInt(), (ih * s).roundToInt()), filterQuality = FilterQuality.Medium)
        return
    }

    val zoom = if (spec.fx == PhotoFx.ZOOM) 1.06f + 0.05f * sin(t * 0.5f) else 1f
    var s: Float
    var left: Float
    var top: Float
    when (spec.fit) {
        PhotoFit.COVER -> {
            s = max(w / iw, h / ih) * zoom
            val slackX = iw * s - w
            val slackY = ih * s - h
            left = if (spec.drift > 0f && slackX > 1f) {
                -slackX * (0.5f + 0.5f * sin(t * 0.03f) * spec.drift)
            } else {
                (w / 2f - spec.focusX * iw * s).coerceIn(-slackX, 0f)
            }
            top = (a.center.y - spec.focusY * ih * s).coerceIn(-slackY, 0f)
        }
        PhotoFit.FIT_WIDTH -> {
            s = w / iw * zoom
            left = (w - iw * s) / 2f
            top = a.center.y - spec.focusY * ih * s
        }
        PhotoFit.ANCHOR -> {
            s = a.radius * 1.02f / spec.featureR
            left = a.center.x - spec.featureX * s
            top = a.center.y - spec.featureY * s
        }
    }
    val dw = iw * s
    val dh = ih * s
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(left.roundToInt(), top.roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.High)

    // Blend the picture's edges into the background so there's no hard line.
    if (spec.fit != PhotoFit.COVER) {
        val band = min(dw, dh) * 0.14f
        drawRect(Brush.verticalGradient(listOf(spec.bg, Color.Transparent), startY = top, endY = top + band), Offset(0f, top), Size(w, band))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, spec.bg), startY = top + dh - band, endY = top + dh), Offset(0f, top + dh - band), Size(w, band))
        if (dw < w + 2f) {
            drawRect(Brush.horizontalGradient(listOf(spec.bg, Color.Transparent), startX = left, endX = left + band), Offset(left, 0f), Size(band, h))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, spec.bg), startX = left + dw - band, endX = left + dw), Offset(left + dw - band, 0f), Size(band, h))
        }
    }

    when (spec.fx) {
        PhotoFx.SPACE -> {
            // A few stars glint now and then, and the odd shooting star.
            for (st in stars.take(28)) {
                val g = (0.5f + 0.5f * sin(t * (0.8f + st.z * 1.6f) + st.x * 50f)).let { it * it * it }
                if (g < 0.05f) continue
                val c = Offset(st.x * w, st.y * h)
                val r = (1f + st.z * 1.5f).dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * g), Color.Transparent), center = c, radius = r * 5), r * 5, c)
                drawLine(Color.White.copy(alpha = 0.6f * g), Offset(c.x - r * 4 * g, c.y), Offset(c.x + r * 4 * g, c.y), 1f)
                drawLine(Color.White.copy(alpha = 0.6f * g), Offset(c.x, c.y - r * 4 * g), Offset(c.x, c.y + r * 4 * g), 1f)
            }
            drawShootingStar(t * 0.8f, Color.White)
        }
        PhotoFx.RAIN, PhotoFx.NEON -> {
            if (spec.fx == PhotoFx.NEON) {
                // Signs that stutter now and then.
                val buzz = sin(t * 7.3f) * sin(t * 2.1f + 1f)
                if (buzz > 0.82f) drawRect(Color.Black.copy(alpha = 0.10f))
            }
            val rain = Random(77)
            repeat(if (spec.fx == PhotoFx.RAIN) 80 else 40) {
                val x = rain.nextFloat() * w
                val y = ((rain.nextFloat() + t * (0.5f + rain.nextFloat() * 0.4f)) % 1f) * h
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x, y), Offset(x - 3.dp.toPx(), y + 16.dp.toPx()), 1f)
            }
        }
        PhotoFx.GRAIN -> {
            val flicker = if (sin(t * 5.1f) * sin(t * 1.3f) > 0.9f) 0.08f else 0f
            if (flicker > 0f) drawRect(Color.Black.copy(alpha = flicker))
            val r = Random((t * 24f).toInt())
            val dot = 1.5f.dp.toPx()
            repeat(450) {
                val c = if (r.nextBoolean()) Color.White else Color.Black
                drawRect(c.copy(alpha = 0.05f + r.nextFloat() * 0.05f), Offset(r.nextFloat() * w, r.nextFloat() * h), Size(dot, dot))
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = Offset(w / 2f, h * 0.45f), radius = max(w, h) * 0.75f))
        }
        PhotoFx.ZOOM -> {
            val p = 0.5f + 0.5f * sin(t * 0.5f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f * p), Color.Transparent), center = a.center, radius = w * 0.6f), w * 0.6f, a.center)
        }
        PhotoFx.CORONA -> {
            val p = 0.5f + 0.5f * sin(t * 0.9f)
            val rr = a.radius * (1.5f + 0.15f * p)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFBFEFFF).copy(alpha = 0.10f + 0.12f * p), Color.Transparent), center = a.center, radius = rr), rr, a.center)
        }
        PhotoFx.GLARE -> {
            // Light sliding across the window glass.
            val p = (t * 0.04f) % 1f
            val x = -w * 0.5f + p * w * 2f
            drawLine(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Offset(x + w * 0.6f, h), w * 0.12f, StrokeCap.Butt)
        }
        PhotoFx.NONE -> Unit
    }

    // Keep the controls readable over bright photos.
    drawRect(Brush.verticalGradient(listOf(Void.copy(alpha = 0.55f), Color.Transparent), startY = 0f, endY = h * 0.14f), Offset.Zero, Size(w, h * 0.14f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Void.copy(alpha = 0.72f)), startY = h * 0.55f, endY = h), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
}
