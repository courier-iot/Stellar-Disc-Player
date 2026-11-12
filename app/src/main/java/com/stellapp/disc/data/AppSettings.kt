package com.stellapp.disc.data

import android.content.SharedPreferences
import com.stellapp.disc.HeroMode
import com.stellapp.disc.ui.Backdrop
import com.stellapp.disc.ui.theme.ThemeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** The look-and-feel settings, saved in SharedPreferences. */
class AppSettings(private val prefs: SharedPreferences) {

    private val _accent = MutableStateFlow(prefs.getInt(KEY_ACCENT, 0))
    val accent: StateFlow<Int> = _accent.asStateFlow()

    private val _animate = MutableStateFlow(prefs.getBoolean(KEY_ANIMATE, true))
    val animate: StateFlow<Boolean> = _animate.asStateFlow()

    private val _lyricsAuto = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_AUTO, false))
    val lyricsAuto: StateFlow<Boolean> = _lyricsAuto.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _modernHome = MutableStateFlow(prefs.getBoolean(KEY_MODERN_HOME, true))
    val modernHome: StateFlow<Boolean> = _modernHome.asStateFlow()

    private val _backdrop = MutableStateFlow(
        runCatching { Backdrop.valueOf(prefs.getString(KEY_BACKDROP, null) ?: "SYNTHWAVE") }.getOrDefault(Backdrop.SYNTHWAVE)
    )
    val backdrop: StateFlow<Backdrop> = _backdrop.asStateFlow()

    private val _clearView = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_VIEW, false))
    val clearView: StateFlow<Boolean> = _clearView.asStateFlow()

    private val _heroMode = MutableStateFlow(
        runCatching { HeroMode.valueOf(prefs.getString(KEY_HERO_MODE, null) ?: "TOP_PICK") }.getOrDefault(HeroMode.TOP_PICK)
    )
    val heroMode: StateFlow<HeroMode> = _heroMode.asStateFlow()

    private val _btAutoplay = MutableStateFlow(prefs.getBoolean(KEY_BT_AUTOPLAY, false))
    /** Start playing when Bluetooth audio connects. Read by the playback service. */
    val btAutoplay: StateFlow<Boolean> = _btAutoplay.asStateFlow()

    private val _pureBlack = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK, false))
    /** True-black backgrounds for OLED screens. */
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    init {
        // The theme colours read this directly, so the very first frame is already right.
        ThemeState.pureBlack = _pureBlack.value
    }

    fun setPureBlack(on: Boolean) {
        _pureBlack.value = on
        ThemeState.pureBlack = on
        prefs.edit().putBoolean(KEY_PURE_BLACK, on).apply()
    }

    fun setBtAutoplay(on: Boolean) {
        _btAutoplay.value = on
        prefs.edit().putBoolean(KEY_BT_AUTOPLAY, on).apply()
    }

    fun setAccent(index: Int) {
        _accent.value = index
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    fun setAnimate(on: Boolean) {
        _animate.value = on
        prefs.edit().putBoolean(KEY_ANIMATE, on).apply()
    }

    fun setLyricsAuto(on: Boolean) {
        _lyricsAuto.value = on
        prefs.edit().putBoolean(KEY_LYRICS_AUTO, on).apply()
    }

    fun setOnboarded() {
        _onboarded.value = true
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun setModernHome(on: Boolean) {
        _modernHome.value = on
        prefs.edit().putBoolean(KEY_MODERN_HOME, on).apply()
    }

    fun setBackdrop(b: Backdrop) {
        _backdrop.value = b
        prefs.edit().putString(KEY_BACKDROP, b.name).apply()
    }

    fun setClearView(on: Boolean) {
        _clearView.value = on
        prefs.edit().putBoolean(KEY_CLEAR_VIEW, on).apply()
    }

    fun setHeroMode(m: HeroMode) {
        _heroMode.value = m
        prefs.edit().putString(KEY_HERO_MODE, m.name).apply()
    }

    /** These settings as backup JSON. */
    fun toJson(): JSONObject = JSONObject()
        .put("accent", _accent.value)
        .put("animate", _animate.value)
        .put("lyricsAuto", _lyricsAuto.value)
        .put("modernHome", _modernHome.value)
        .put("backdrop", _backdrop.value.name)
        .put("clearView", _clearView.value)
        .put("heroMode", _heroMode.value.name)
        .put("btAutoplay", _btAutoplay.value)
        .put("pureBlack", _pureBlack.value)

    /** Applies whatever a backup contains (older backups simply have fewer keys). */
    fun restore(o: JSONObject) {
        if (o.has("accent")) setAccent(o.optInt("accent", 0))
        if (o.has("animate")) setAnimate(o.optBoolean("animate", true))
        if (o.has("lyricsAuto")) setLyricsAuto(o.optBoolean("lyricsAuto", false))
        if (o.has("modernHome")) setModernHome(o.optBoolean("modernHome", true))
        if (o.has("backdrop")) runCatching { setBackdrop(Backdrop.valueOf(o.optString("backdrop"))) }
        if (o.has("clearView")) setClearView(o.optBoolean("clearView", false))
        if (o.has("heroMode")) runCatching { setHeroMode(HeroMode.valueOf(o.optString("heroMode"))) }
        if (o.has("btAutoplay")) setBtAutoplay(o.optBoolean("btAutoplay", false))
        if (o.has("pureBlack")) setPureBlack(o.optBoolean("pureBlack", false))
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_ANIMATE = "animate_bg"
        const val KEY_LYRICS_AUTO = "lyrics_auto"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_MODERN_HOME = "modern_home"
        const val KEY_BACKDROP = "player_backdrop"
        const val KEY_HERO_MODE = "hero_mode"
        const val KEY_CLEAR_VIEW = "clear_view"
        const val KEY_BT_AUTOPLAY = "bt_autoplay"
        const val KEY_PURE_BLACK = "pure_black"
    }
}
