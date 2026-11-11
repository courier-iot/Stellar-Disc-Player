package com.stellapp.disc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val downloadUrl: String, val pageUrl: String)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data object Skipped : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** Checks the latest GitHub release of the app repo. */
object UpdateChecker {
    private const val KEY_LAST = "update_last_check"
    private const val DAY = 24L * 60 * 60 * 1000
    private val versionRe = Regex("""v?(\d+\.\d+\.\d+)""")

    suspend fun check(context: Context, force: Boolean): UpdateResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("stellar_disc", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST, 0L) < DAY) return@withContext UpdateResult.Skipped
        prefs.edit().putLong(KEY_LAST, now).apply()

        val conn = try {
            URL("https://api.github.com/repos/${AppInfo.REPO}/releases/latest").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext UpdateResult.Failed("Couldn't reach GitHub")
        }
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", AppInfo.USER_AGENT)
            if (conn.responseCode == 404) return@withContext UpdateResult.Failed("No releases published yet")
            if (conn.responseCode != 200) return@withContext UpdateResult.Failed("GitHub said ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            var apkUrl: String? = null
            var apkName = ""
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        apkName = name
                        break
                    }
                }
            }
            val version = versionRe.find(apkName)?.groupValues?.get(1)
                ?: versionRe.find(o.optString("name"))?.groupValues?.get(1)
                ?: versionRe.find(o.optString("tag_name"))?.groupValues?.get(1)
                ?: return@withContext UpdateResult.Failed("Couldn't read the release version")
            val page = o.optString("html_url").ifBlank { "${AppInfo.REPO_URL}/releases" }
            if (isNewer(version, BuildConfig.VERSION_NAME)) {
                UpdateResult.Available(UpdateInfo(version, apkUrl ?: page, page))
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Failed("Couldn't check: ${e.message ?: "network error"}")
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
