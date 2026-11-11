package com.stellapp.disc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class PlayStat(val count: Int, val lastPlayed: Long)

/** Play counts and last-played times, for the Recently played and Most played lists. */
object PlayStats {
    private const val FILE_NAME = "stats.json"
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var file: File? = null

    private val _stats = MutableStateFlow<Map<Long, PlayStat>>(emptyMap())
    val stats: StateFlow<Map<Long, PlayStat>> = _stats.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            _stats.value = try {
                if (!f.exists()) emptyMap() else {
                    val o = JSONObject(f.readText())
                    o.keys().asSequence().mapNotNull { k ->
                        val id = k.toLongOrNull() ?: return@mapNotNull null
                        val s = o.getJSONObject(k)
                        id to PlayStat(s.optInt("c", 0), s.optLong("t", 0L))
                    }.toMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    fun record(songId: Long) {
        if (songId < 0) return
        val snapshot: Map<Long, PlayStat>
        synchronized(lock) {
            val old = _stats.value[songId]
            snapshot = _stats.value + (songId to PlayStat((old?.count ?: 0) + 1, System.currentTimeMillis()))
            _stats.value = snapshot
        }
        val f = file ?: return
        io.execute {
            try {
                val o = JSONObject()
                snapshot.forEach { (id, s) -> o.put(id.toString(), JSONObject().put("c", s.count).put("t", s.lastPlayed)) }
                f.writeText(o.toString())
            } catch (_: Exception) {
            }
        }
    }
}
