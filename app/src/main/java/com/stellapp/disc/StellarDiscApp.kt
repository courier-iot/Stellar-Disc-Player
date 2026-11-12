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
        PlaylistStore.init(this)
        LinkStore.init(this)
        MetaStore.init(this)
        PlayStats.init(this)
        TasteStore.init(this)
        PinStore.init(this)
        AudioFx.init(this)
        PlaybackExtras.init(this)
    }
}
