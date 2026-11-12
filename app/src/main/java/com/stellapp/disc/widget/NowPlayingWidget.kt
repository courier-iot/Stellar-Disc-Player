package com.stellapp.disc.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.layout.height
import androidx.glance.layout.fillMaxWidth
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.stellapp.disc.MainActivity
import com.stellapp.disc.PlaybackService
import com.stellapp.disc.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Neon mini player for the home screen. Resizes between three layouts:
 * small (art + play), wide (the classic strip with ♥) and large (big art, full controls).
 */
class NowPlayingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetUpdater.read(context)
        val art = withContext(Dispatchers.IO) { WidgetUpdater.art(context) }
        provideContent {
            val size = LocalSize.current
            when {
                size.height >= LARGE.height -> Large(snap, art)
                size.width >= WIDE.width -> Wide(snap, art)
                else -> Small(snap, art)
            }
        }
    }

    @Composable
    private fun ArtImage(art: Bitmap?, size: androidx.compose.ui.unit.Dp) {
        Image(
            provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = GlanceModifier.size(size).cornerRadius(10.dp),
        )
    }

    @Composable
    private fun Titles(s: WidgetUpdater.Snapshot, modifier: GlanceModifier = GlanceModifier, big: Boolean = false) {
        Column(modifier = modifier) {
            Text(
                text = s.title,
                maxLines = if (big) 2 else 1,
                style = TextStyle(color = ColorProvider(Color(0xFF00F0FF)), fontSize = if (big) 16.sp else 14.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = s.artist,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(0xFF8C8FB5)), fontSize = 12.sp),
            )
        }
    }

    @Composable
    private fun Ctl(res: Int, label: String, size: androidx.compose.ui.unit.Dp, action: androidx.glance.action.Action) {
        Image(
            provider = ImageProvider(res),
            contentDescription = label,
            modifier = GlanceModifier.size(size).padding(6.dp).clickable(action),
        )
    }

    @Composable
    private fun Fav(s: WidgetUpdater.Snapshot) {
        val fav = s.fav ?: return
        Ctl(
            if (fav) R.drawable.ic_w_fav else R.drawable.ic_w_fav_off,
            if (fav) "Remove from favourites" else "Add to favourites",
            36.dp,
            actionRunCallback<FavAction>(),
        )
    }

    @Composable
    private fun PlayPause(s: WidgetUpdater.Snapshot, size: androidx.compose.ui.unit.Dp = 44.dp) {
        Ctl(
            if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play,
            if (s.playing) "Pause" else "Play",
            size,
            actionRunCallback<TogglePlayAction>(),
        )
    }

    private fun frame() = GlanceModifier
        .fillMaxSize()
        .background(Color(0xF00E0B1F))
        .cornerRadius(18.dp)
        .padding(10.dp)
        .clickable(actionStartActivity<MainActivity>())

    @Composable
    private fun Small(s: WidgetUpdater.Snapshot, art: Bitmap?) {
        Row(modifier = frame(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            ArtImage(art, 44.dp)
            Spacer(GlanceModifier.width(6.dp))
            PlayPause(s)
        }
    }

    @Composable
    private fun Wide(s: WidgetUpdater.Snapshot, art: Bitmap?) {
        Row(modifier = frame(), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(art, 52.dp)
            Spacer(GlanceModifier.width(10.dp))
            Titles(s, GlanceModifier.defaultWeight())
            Fav(s)
            Ctl(R.drawable.ic_w_prev, "Previous", 36.dp, actionRunCallback<PrevAction>())
            PlayPause(s)
            Ctl(R.drawable.ic_w_next, "Next", 36.dp, actionRunCallback<NextAction>())
        }
    }

    @Composable
    private fun Large(s: WidgetUpdater.Snapshot, art: Bitmap?) {
        Column(modifier = frame(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(art, 96.dp)
                Spacer(GlanceModifier.width(12.dp))
                Titles(s, GlanceModifier.defaultWeight(), big = true)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (s.fav != null) {
                    Fav(s)
                    Spacer(GlanceModifier.width(8.dp))
                }
                Ctl(R.drawable.ic_w_prev, "Previous", 44.dp, actionRunCallback<PrevAction>())
                Spacer(GlanceModifier.width(8.dp))
                PlayPause(s, 56.dp)
                Spacer(GlanceModifier.width(8.dp))
                Ctl(R.drawable.ic_w_next, "Next", 44.dp, actionRunCallback<NextAction>())
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 48.dp)
        private val WIDE = DpSize(220.dp, 48.dp)
        private val LARGE = DpSize(220.dp, 150.dp)
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

/** Connects to the playback service for a moment to run one command. */
private suspend fun withController(context: Context, block: (MediaController) -> Unit) = withContext(Dispatchers.Main) {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val c = MediaController.Builder(context, token).buildAsync().await()
    try {
        block(c)
        delay(400)
    } finally {
        c.release()
    }
}

class TogglePlayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context) { c ->
            if (c.isPlaying) {
                c.pause()
            } else {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context) { it.seekToNext() }
    }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context) { it.seekToPrevious() }
    }
}

/** ♥ from the widget. Works even when nothing is connected to the player right now. */
class FavAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = WidgetUpdater.favouriteId(context) ?: return
        com.stellapp.disc.data.FavStore.init(context)
        com.stellapp.disc.data.FavStore.toggle(id)
        WidgetUpdater.refresh(context)
    }
}

/** Stores what the widget shows and asks it to redraw. Called by the playback service. */
object WidgetUpdater {
    /** [fav] is null when the song can't be favourited (a stream, or nothing loaded). */
    data class Snapshot(val title: String, val artist: String, val playing: Boolean, val fav: Boolean? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var last: Pair<Snapshot, String>? = null

    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun read(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            title = p.getString("title", null) ?: "Stellar Disc",
            artist = p.getString("artist", null) ?: "Tap to open",
            playing = p.getBoolean("playing", false),
            fav = favouriteId(context)?.let { com.stellapp.disc.data.FavStore.isFav(it) },
        )
    }

    /** The library id of the song on the widget, if it can be favourited. */
    fun favouriteId(context: Context): Long? =
        prefs(context).getLong("fav_id", -1L).takeIf { it >= 0 }

    /** Redraw with fresh ♥ state. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                NowPlayingWidget().updateAll(app)
            } catch (_: Exception) {
            }
        }
    }

    fun art(context: Context): Bitmap? {
        val f = File(context.filesDir, "widget_art.png")
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun push(context: Context, p: Player) {
        val m = p.mediaMetadata
        val item = p.currentMediaItem
        val scheme = item?.localConfiguration?.uri?.scheme
        val favId = if (scheme == "http" || scheme == "https") null else item?.mediaId?.toLongOrNull()?.takeIf { it >= 0 }
        val snap = Snapshot(
            title = m.title?.toString() ?: "Stellar Disc",
            artist = m.artist?.toString() ?: "",
            playing = p.isPlaying,
            fav = favId?.let { com.stellapp.disc.data.FavStore.isFav(it) },
        )
        val artUri = m.artworkUri?.toString() ?: ""
        if (last == snap to artUri) return
        val artChanged = last?.second != artUri
        last = snap to artUri
        val app = context.applicationContext
        prefs(app).edit()
            .putString("title", snap.title)
            .putString("artist", snap.artist)
            .putBoolean("playing", snap.playing)
            .putLong("fav_id", favId ?: -1L)
            .apply()
        scope.launch {
            if (artChanged) saveArt(app, artUri)
            try {
                NowPlayingWidget().updateAll(app)
            } catch (_: Exception) {
            }
        }
    }

    fun pushStopped(context: Context) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean("playing", false).apply()
        last = null
        scope.launch {
            try {
                NowPlayingWidget().updateAll(app)
            } catch (_: Exception) {
            }
        }
    }

    private fun saveArt(context: Context, uri: String) {
        val f = File(context.filesDir, "widget_art.png")
        try {
            if (uri.isBlank() || uri.startsWith("http")) {
                f.delete()
                return
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            val bmp = context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            if (bmp == null) f.delete() else f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
            f.delete()
        }
    }
}
