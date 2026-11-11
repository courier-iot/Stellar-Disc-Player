package com.stellapp.disc.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Remembers where each Uplink download came from (song id -> source link),
 * so playlists can be shared as a list of links.
 */
object LinkStore {
    private const val FILE_NAME = "links.json"
    private val lock = Any()
    private var file: File? = null
    private val links = HashMap<Long, String>()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            try {
                if (f.exists()) {
                    val o = JSONObject(f.readText())
                    o.keys().forEach { k -> k.toLongOrNull()?.let { links[it] = o.getString(k) } }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun get(songId: Long): String? = synchronized(lock) { links[songId] }

    fun all(): Map<Long, String> = synchronized(lock) { HashMap(links) }

    fun put(songId: Long, url: String) {
        synchronized(lock) {
            links[songId] = url
            save()
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            val o = JSONObject()
            links.forEach { (k, v) -> o.put(k.toString(), v) }
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }
}
