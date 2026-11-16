package com.stellapp.disc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Shares what you listen to with Hub Connect S (Stellar Hub's phone app), so your resident learns your taste
 *  and can listen along. Off until you turn it on in Settings. Only the song title, artist and whether you
 *  finished or skipped it are sent, straight to that one app on this phone: nothing leaves the phone from here. */
object HubShare {
    private const val PKG = "com.stellarhub.hubconnects"
    private const val ACTION = "com.stellapp.disc.LISTEN"
    private const val PREFS = "hub_share"

    fun enabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    fun hubInstalled(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    /** event: play (a song started), complete (played through) or skip. */
    fun send(ctx: Context, event: String, title: String?, artist: String?, durationMs: Long = 0) {
        if (title.isNullOrBlank() || !enabled(ctx)) return
        try {
            ctx.sendBroadcast(Intent(ACTION).setPackage(PKG)
                .putExtra("event", event)
                .putExtra("title", title.take(120))
                .putExtra("artist", (artist ?: "").take(80))
                .putExtra("duration", durationMs / 1000)
                .putExtra("ts", System.currentTimeMillis() / 1000))
        } catch (e: Exception) {
            // Hub Connect S isn't installed, or Android refused: listening sharing is best-effort.
        }
    }
}
