package com.stellapp.disc.uplink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stellapp.disc.MainActivity

object Notifications {
    const val CHANNEL = "uplink"
    const val PROGRESS_ID = 4201

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Uplink downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Download progress and results"
                }
            )
        }
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(context: Context, jobs: List<DownloadJob>): android.app.Notification {
        val job = jobs.firstOrNull { it.status.active }
        val waiting = jobs.count { it.status == JobStatus.QUEUED }
        val title = "Uplink · " + (job?.title ?: "receiving signal…")
        val detail = buildString {
            if (job != null && job.itemCount > 1) append("Item ${job.item} of ${job.itemCount} · ")
            append(
                when (job?.status) {
                    JobStatus.PROCESSING -> "processing"
                    JobStatus.SAVING -> "saving to library"
                    JobStatus.PREPARING -> "booting downloader"
                    else -> "downloading"
                }
            )
            if (waiting > 0) append(" · $waiting waiting")
        }
        val pct = ((job?.overallProgress ?: 0f) * 100).toInt()
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setProgress(100, pct, job == null || job.status != JobStatus.DOWNLOADING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .build()
    }

    fun swapProgress(context: Context, s: com.stellapp.disc.swap.SwapSession): android.app.Notification {
        val total = s.selectedCount.coerceAtLeast(1)
        val done = s.doneCount
        val item = s.items.getOrNull(s.current)
        val pct = (((done + (item?.progress ?: 0f)) / total) * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Swap · receiving \"${s.name}\"")
            .setContentText("${item?.track?.title ?: "Connecting…"} · ${(done + 1).coerceAtMost(total)} of $total")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .build()
    }

    fun done(context: Context, job: DownloadJob) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val ok = job.status == JobStatus.DONE
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "Transmission complete" else "Transmission failed")
            .setContentText(job.message ?: job.title ?: job.options.url)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(job.id.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}
