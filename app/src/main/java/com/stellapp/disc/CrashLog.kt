package com.stellapp.disc

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.util.Date
import kotlin.system.exitProcess

/** Saves the last crash to a file so the user can report it on the next launch. */
object CrashLog {
    private const val FILE_NAME = "crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val file = File(app.filesDir, FILE_NAME)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText(
                    buildString {
                        appendLine("Stellar Disc v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Time: ${Date()}")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(trace)
                    }
                )
            } catch (_: Throwable) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()?.ifBlank { null }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /** A "new issue" link on GitHub with the crash log filled in. */
    fun issueUrl(report: String): String {
        val title = "Crash report (v${BuildConfig.VERSION_NAME})"
        val body = "**What were you doing when it crashed?**\n\n\n**Crash log**\n```\n" + report.take(4000) + "\n```"
        return "${AppInfo.SUPPORT_URL}/new?title=" + URLEncoder.encode(title, "UTF-8") + "&body=" + URLEncoder.encode(body, "UTF-8")
    }
}
