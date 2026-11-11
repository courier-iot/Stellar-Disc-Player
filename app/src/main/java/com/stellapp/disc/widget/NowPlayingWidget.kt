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

/** Neon mini player for the home screen. */
class NowPlayingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetUpdater.read(context)
        val art = withContext(Dispatchers.IO) { WidgetUpdater.art(context) }
        provideContent { Content(snap, art) }
    }

    @Composable
    private fun Content(s: WidgetUpdater.Snapshot, art: Bitmap?) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xF00E0B1F))
                .cornerRadius(18.dp)
                .padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = GlanceModifier.size(52.dp).cornerRadius(10.dp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = s.title,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color(0xFF00F0FF)), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = s.artist,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color(0xFF8C8FB5)), fontSize = 12.sp),
                )
            }
            Image(
                provider = ImageProvider(R.drawable.ic_w_prev),
                contentDescription = "Previous",
                modifier = GlanceModifier.size(36.dp).padding(6.dp).clickable(actionRunCallback<PrevAction>()),
            )
            Image(
                provider = ImageProvider(if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
                contentDescription = if (s.playing) "Pause" else "Play",
                modifier = GlanceModifier.size(44.dp).padding(6.dp).clickable(actionRunCallback<TogglePlayAction>()),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_w_next),
                contentDescription = "Next",
                modifier = GlanceModifier.size(36.dp).padding(6.dp).clickable(actionRunCallback<NextAction>()),
            )
        }
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

/** Stores what the widget shows and asks it to redraw. Called by the playback service. */
object WidgetUpdater {
    data class Snapshot(val title: String, val artist: String, val playing: Boolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var last: Pair<Snapshot, String>? = null

    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun read(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            title = p.getString("title", null) ?: "Stellar Disc",
            artist = p.getString("artist", null) ?: "Tap to open",
            playing = p.getBoolean("playing", false),
        )
    }

    fun art(context: Context): Bitmap? {
        val f = File(context.filesDir, "widget_art.png")
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun push(context: Context, p: Player) {
        val m = p.mediaMetadata
        val snap = Snapshot(
            title = m.title?.toString() ?: "Stellar Disc",
            artist = m.artist?.toString() ?: "",
            playing = p.isPlaying,
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
