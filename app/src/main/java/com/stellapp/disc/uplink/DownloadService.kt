package com.stellapp.disc.uplink

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import com.stellapp.disc.swap.SwapPhase
import com.stellapp.disc.swap.SwapReceiver
import com.stellapp.disc.swap.SwapSession
import kotlinx.coroutines.launch

/** Keeps the app alive while downloads run and shows a progress notification. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false
    private var lastPost = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, Notifications.PROGRESS_ID, build(UplinkManager.jobs.value, SwapReceiver.session.value), type)
        } catch (e: Exception) {
            // Android refused (e.g. the daily background-download allowance is used up). Downloads
            // carry on while the app is open, and unfinished ones resume next launch.
            stopSelf()
            return START_NOT_STICKY
        }

        if (!watching) {
            watching = true
            scope.launch {
                combine(UplinkManager.jobs, SwapReceiver.session) { jobs, swap -> jobs to swap }.collect { (jobs, swap) ->
                    val swapActive = swap?.phase == SwapPhase.TRANSFERRING
                    if (!swapActive && jobs.none { it.status.active }) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPost > 800) {
                            lastPost = now
                            try {
                                NotificationManagerCompat.from(this@DownloadService)
                                    .notify(Notifications.PROGRESS_ID, build(jobs, swap))
                            } catch (_: SecurityException) {
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(jobs: List<DownloadJob>, swap: SwapSession?) =
        if (swap?.phase == SwapPhase.TRANSFERRING) Notifications.swapProgress(this, swap)
        else Notifications.progress(this, jobs)

    /**
     * Android 15 limits background downloads to about 6 hours a day. When time's up we must stop
     * at once; unfinished downloads are saved and pick up again the next time the app opens.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
