package com.stellapp.disc

import android.app.Application
import com.stellapp.disc.data.LinkStore
import com.stellapp.disc.data.MetaStore
import com.stellapp.disc.data.PinStore
import com.stellapp.disc.data.PlayStats
import com.stellapp.disc.data.TasteStore
import com.stellapp.disc.data.PlaylistStore
import com.stellapp.disc.playback.AudioFx
import com.stellapp.disc.playback.PlaybackExtras

class StellarDiscApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        com.stellapp.disc.uplink.CookieVault.cleanup(this)
        cleanUpLeftovers()
        PlaylistStore.init(this)
        LinkStore.init(this)
        MetaStore.init(this)
        PlayStats.init(this)
        TasteStore.init(this)
        PinStore.init(this)
        com.stellapp.disc.data.FavStore.init(this)
        AudioFx.init(this)
        PlaybackExtras.init(this)
    }

    /**
     * Every cold start: remove half-finished files from downloads or Swap transfers that were cut off
     * when the app was closed (nothing can be running yet, so it's all safe to delete).
     */
    private fun cleanUpLeftovers() {
        // These two are removed right away (deleting files is quick) so a resumed download can't race it.
        try {
            java.io.File(cacheDir, "uplink").deleteRecursively()
            java.io.File(cacheDir, "swap").deleteRecursively()
        } catch (_: Exception) {
        }
        Thread {
            try {
                // Old yt-dlp temp files that sometimes land straight in the cache folder.
                cacheDir.listFiles { f -> f.isFile && (f.name.endsWith(".part") || f.name.endsWith(".ytdl") || f.name.startsWith("ck_")) }
                    ?.forEach { it.delete() }
            } catch (_: Exception) {
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.stellapp.disc.ui.BackdropMemory.trim(level)
    }
}
