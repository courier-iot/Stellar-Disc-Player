package com.stellapp.disc.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Songs you've hearted, newest first. Shown as the "Favourites" playlist. */
object FavStore {
    private val lock = Any()
    private var prefs: SharedPreferences? = null

    private val _ids = MutableStateFlow<List<Long>>(emptyList())
    val ids: StateFlow<List<Long>> = _ids.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences("favourites", Context.MODE_PRIVATE)
            prefs = p
            _ids.value = p.getString("ids", "")!!.split(",").mapNotNull { it.toLongOrNull() }
        }
    }

    fun isFav(id: Long) = id in _ids.value

    fun toggle(id: Long): Boolean {
        val now = id !in _ids.value
        set(if (now) listOf(id) + _ids.value else _ids.value - id)
        return now
    }

    fun set(list: List<Long>) {
        _ids.value = list.distinct()
        prefs?.edit()?.putString("ids", _ids.value.joinToString(","))?.apply()
    }

    fun remove(ids: Collection<Long>) {
        if (ids.any { it in _ids.value }) set(_ids.value - ids.toSet())
    }
}
