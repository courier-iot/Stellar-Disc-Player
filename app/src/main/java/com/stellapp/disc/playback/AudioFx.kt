package com.stellapp.disc.playback

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/**
 * Equalizer, bass boost and the real visualizer. All three hook into the player's
 * audio session, which the playback service hands over via [attach].
 */
object AudioFx {
    const val BARS = 32

    data class Band(val centerHz: Int, val level: Int)

    /** Reverb rooms, in PresetReverb's order (0 = off). */
    val REVERBS = listOf("Off", "Small room", "Medium room", "Large room", "Medium hall", "Large hall", "Plate")

    /** A sound you saved yourself: every slider, plus speed and pitch. */
    data class SoundPreset(
        val name: String,
        val levels: List<Int>,
        val bass: Int,
        val virtualizer: Int,
        val loudness: Int,
        val reverb: Int,
        val balance: Int,
        val speed: Float,
        val pitch: Float,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("name", name)
            .put("levels", JSONArray(levels))
            .put("bass", bass)
            .put("virt", virtualizer)
            .put("loud", loudness)
            .put("reverb", reverb)
            .put("balance", balance)
            .put("speed", speed.toDouble())
            .put("pitch", pitch.toDouble())

        companion object {
            fun from(o: JSONObject): SoundPreset {
                val arr = o.optJSONArray("levels") ?: JSONArray()
                return SoundPreset(
                    name = o.optString("name", "My sound"),
                    levels = (0 until arr.length()).map { arr.optInt(it) },
                    bass = o.optInt("bass"),
                    virtualizer = o.optInt("virt"),
                    loudness = o.optInt("loud"),
                    reverb = o.optInt("reverb"),
                    balance = o.optInt("balance"),
                    speed = o.optDouble("speed", 1.0).toFloat(),
                    pitch = o.optDouble("pitch", 1.0).toFloat(),
                )
            }
        }
    }

    data class EqState(
        val supported: Boolean = false,
        val enabled: Boolean = false,
        val bands: List<Band> = emptyList(),
        val minLevel: Int = -1500,
        val maxLevel: Int = 1500,
        val presets: List<String> = emptyList(),
        /** -1 = custom. */
        val preset: Int = -1,
        val bassSupported: Boolean = false,
        val bass: Int = 0,
        val visualizer: Boolean = false,
        /** Volume levelling (Android 9+): evens out loud and quiet tracks. */
        val leveling: Boolean = false,
        val levelingSupported: Boolean = android.os.Build.VERSION.SDK_INT >= 28,
        val virtualizerSupported: Boolean = false,
        /** 0..1000 */
        val virtualizer: Int = 0,
        val loudnessSupported: Boolean = false,
        /** Extra gain in millibels, 0..1000 (+10 dB). */
        val loudness: Int = 0,
        val reverbSupported: Boolean = false,
        /** Index into [REVERBS]. */
        val reverb: Int = 0,
        /** -100 (all left) .. 100 (all right). */
        val balance: Int = 0,
        val balanceSupported: Boolean = android.os.Build.VERSION.SDK_INT >= 28,
        /** Your saved sounds. */
        val customs: List<SoundPreset> = emptyList(),
        /** Name of the saved sound in use, null once anything is changed by hand. */
        val custom: String? = null,
    )

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var session = 0
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var viz: Visualizer? = null
    private var leveler: android.media.audiofx.DynamicsProcessing? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var reverbFx: PresetReverb? = null
    private val smooth = FloatArray(BARS)

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private val _spectrum = MutableStateFlow<FloatArray?>(null)
    /** 0..1 levels for [BARS] bars, or null when the real visualizer is off. */
    val spectrum: StateFlow<FloatArray?> = _spectrum.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences("audiofx", Context.MODE_PRIVATE)
        prefs = p
        _state.update {
            it.copy(
                enabled = p.getBoolean("eq_on", false),
                visualizer = p.getBoolean("viz_on", false),
                bass = p.getInt("bass", 0),
                preset = p.getInt("preset", -1),
                leveling = p.getBoolean("level_on", false),
                virtualizer = p.getInt("virt", 0),
                loudness = p.getInt("loud", 0),
                reverb = p.getInt("reverb", 0),
                balance = p.getInt("balance", 0),
                customs = readCustoms(p),
                custom = p.getString("custom", null),
            )
        }
    }

    private fun readCustoms(p: SharedPreferences): List<SoundPreset> = try {
        val arr = JSONArray(p.getString("customs", "[]") ?: "[]")
        (0 until arr.length()).map { SoundPreset.from(arr.getJSONObject(it)) }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun writeCustoms(list: List<SoundPreset>) {
        prefs?.edit()?.putString("customs", JSONArray(list.map { it.toJson() }).toString())?.apply()
    }

    /** Hand-made change: no saved sound is "in use" any more. */
    private fun dropCustom() {
        if (_state.value.custom != null) {
            prefs?.edit()?.remove("custom")?.apply()
            _state.update { it.copy(custom = null) }
        }
    }

    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (sessionId == session && eq != null) return
        releaseEffects()
        session = sessionId
        val p = prefs

        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val range = e.bandLevelRange
            val presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            val preset = p?.getInt("preset", -1) ?: -1
            val saved = p?.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (preset in presets.indices) {
                e.usePreset(preset.toShort())
            } else if (saved != null && saved.size == e.numberOfBands.toInt()) {
                saved.forEachIndexed { i, level -> e.setBandLevel(i.toShort(), level.toShort()) }
            }
            e.enabled = _state.value.enabled
            _state.update {
                it.copy(
                    supported = true,
                    bands = readBands(e),
                    minLevel = range[0].toInt(),
                    maxLevel = range[1].toInt(),
                    presets = presets,
                    preset = if (preset in presets.indices) preset else -1,
                )
            }
        } catch (t: Throwable) {
            eq = null
            _state.update { it.copy(supported = false) }
        }

        try {
            val b = BassBoost(0, sessionId)
            if (b.strengthSupported) {
                b.setStrength(_state.value.bass.toShort())
                b.enabled = _state.value.enabled && _state.value.bass > 0
                bassBoost = b
                _state.update { it.copy(bassSupported = true) }
            } else {
                b.release()
            }
        } catch (_: Throwable) {
        }

        try {
            val v = Virtualizer(0, sessionId)
            if (v.strengthSupported) {
                v.setStrength(_state.value.virtualizer.toShort())
                v.enabled = _state.value.enabled && _state.value.virtualizer > 0
                virt = v
                _state.update { it.copy(virtualizerSupported = true) }
            } else {
                v.release()
            }
        } catch (_: Throwable) {
        }

        try {
            val l = LoudnessEnhancer(sessionId)
            l.setTargetGain(_state.value.loudness)
            l.enabled = _state.value.enabled && _state.value.loudness > 0
            loud = l
            _state.update { it.copy(loudnessSupported = true) }
        } catch (_: Throwable) {
        }

        try {
            val r = PresetReverb(0, sessionId)
            r.preset = _state.value.reverb.coerceIn(0, REVERBS.lastIndex).toShort()
            r.enabled = _state.value.enabled && _state.value.reverb > 0
            reverbFx = r
            _state.update { it.copy(reverbSupported = true) }
        } catch (_: Throwable) {
        }

        if (_state.value.visualizer) startVisualizer()
        if (needsDynamics()) startLeveler()
    }

    fun setEnabled(on: Boolean) {
        prefs?.edit()?.putBoolean("eq_on", on)?.apply()
        val st = _state.value
        try {
            eq?.enabled = on
            bassBoost?.enabled = on && st.bass > 0
            virt?.enabled = on && st.virtualizer > 0
            loud?.enabled = on && st.loudness > 0
            reverbFx?.enabled = on && st.reverb > 0
        } catch (_: Throwable) {
        }
        _state.update { it.copy(enabled = on) }
    }

    fun setBand(index: Int, level: Int) {
        val e = eq ?: return
        try {
            e.setBandLevel(index.toShort(), level.toShort())
        } catch (_: Throwable) {
            return
        }
        val bands = readBands(e)
        prefs?.edit()?.putString("levels", bands.joinToString(",") { it.level.toString() })?.putInt("preset", -1)?.apply()
        _state.update { it.copy(bands = bands, preset = -1) }
        dropCustom()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
        } catch (_: Throwable) {
            return
        }
        val bands = readBands(e)
        prefs?.edit()?.putInt("preset", index)?.putString("levels", bands.joinToString(",") { it.level.toString() })?.apply()
        _state.update { it.copy(bands = bands, preset = index) }
        dropCustom()
    }

    fun resetFlat() {
        val e = eq ?: return
        try {
            for (i in 0 until e.numberOfBands.toInt()) e.setBandLevel(i.toShort(), 0)
        } catch (_: Throwable) {
        }
        val bands = readBands(e)
        prefs?.edit()?.putInt("preset", -1)?.putString("levels", bands.joinToString(",") { it.level.toString() })?.apply()
        _state.update { it.copy(bands = bands, preset = -1) }
    }

    fun setBass(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        try {
            bassBoost?.setStrength(s.toShort())
            bassBoost?.enabled = _state.value.enabled && s > 0
        } catch (_: Throwable) {
        }
        prefs?.edit()?.putInt("bass", s)?.apply()
        _state.update { it.copy(bass = s) }
        dropCustom()
    }

    /** Surround widening, 0..1000. Strongest on headphones. */
    fun setVirtualizer(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        try {
            virt?.setStrength(s.toShort())
            virt?.enabled = _state.value.enabled && s > 0
        } catch (_: Throwable) {
        }
        prefs?.edit()?.putInt("virt", s)?.apply()
        _state.update { it.copy(virtualizer = s) }
        dropCustom()
    }

    /** Extra loudness in millibels, 0..1000 (up to +10 dB). */
    fun setLoudness(gainMb: Int) {
        val g = gainMb.coerceIn(0, 1000)
        try {
            loud?.setTargetGain(g)
            loud?.enabled = _state.value.enabled && g > 0
        } catch (_: Throwable) {
        }
        prefs?.edit()?.putInt("loud", g)?.apply()
        _state.update { it.copy(loudness = g) }
        dropCustom()
    }

    fun setReverb(index: Int) {
        val i = index.coerceIn(0, REVERBS.lastIndex)
        try {
            reverbFx?.preset = i.toShort()
            reverbFx?.enabled = _state.value.enabled && i > 0
        } catch (_: Throwable) {
        }
        prefs?.edit()?.putInt("reverb", i)?.apply()
        _state.update { it.copy(reverb = i) }
        dropCustom()
    }

    /** Left/right balance, -100..100. Works whether or not the equalizer is on. */
    @Synchronized
    fun setBalance(value: Int) {
        val b = value.coerceIn(-100, 100)
        prefs?.edit()?.putInt("balance", b)?.apply()
        _state.update { it.copy(balance = b) }
        dropCustom()
        if (needsDynamics()) {
            if (leveler == null) startLeveler() else applyDynamics(leveler!!)
        } else {
            stopLeveler()
        }
    }

    /** Saves everything as a named sound. Speed and pitch come from the player. */
    fun saveCustom(name: String, speed: Float, pitch: Float) {
        val st = _state.value
        val clean = name.trim().ifBlank { "My sound" }.take(24)
        val preset = SoundPreset(clean, st.bands.map { it.level }, st.bass, st.virtualizer, st.loudness, st.reverb, st.balance, speed, pitch)
        val list = st.customs.filter { !it.name.equals(clean, true) } + preset
        writeCustoms(list)
        prefs?.edit()?.putString("custom", clean)?.apply()
        _state.update { it.copy(customs = list, custom = clean) }
    }

    fun deleteCustom(name: String) {
        val list = _state.value.customs.filter { it.name != name }
        writeCustoms(list)
        if (_state.value.custom == name) prefs?.edit()?.remove("custom")?.apply()
        _state.update { it.copy(customs = list, custom = if (it.custom == name) null else it.custom) }
    }

    /** Loads a saved sound's effects (the caller sets speed and pitch on the player). */
    fun applyCustom(p: SoundPreset) {
        val e = eq
        if (e != null && p.levels.size == e.numberOfBands.toInt()) {
            try {
                p.levels.forEachIndexed { i, level -> e.setBandLevel(i.toShort(), level.toShort()) }
            } catch (_: Throwable) {
            }
            val bands = readBands(e)
            prefs?.edit()?.putInt("preset", -1)?.putString("levels", bands.joinToString(",") { it.level.toString() })?.apply()
            _state.update { it.copy(bands = bands, preset = -1) }
        }
        setBass(p.bass)
        setVirtualizer(p.virtualizer)
        setLoudness(p.loudness)
        setReverb(p.reverb)
        setBalance(p.balance)
        if (!_state.value.enabled) setEnabled(true)
        prefs?.edit()?.putString("custom", p.name)?.apply()
        _state.update { it.copy(custom = p.name) }
    }

    /** Everything back to neutral (keeps your saved sounds). */
    fun resetAll() {
        resetFlat()
        setBass(0)
        setVirtualizer(0)
        setLoudness(0)
        setReverb(0)
        setBalance(0)
    }

    /** Volume levelling on/off. */
    @Synchronized
    fun setLeveling(on: Boolean) {
        prefs?.edit()?.putBoolean("level_on", on)?.apply()
        _state.update { it.copy(leveling = on) }
        val dp = leveler
        when {
            !needsDynamics() -> stopLeveler()
            dp == null -> startLeveler()
            else -> applyDynamics(dp)
        }
    }

    /** One dynamics processor carries both the leveller and the balance. */
    private fun needsDynamics(): Boolean = _state.value.leveling || _state.value.balance != 0

    /**
     * A gentle compressor plus a limiter on the whole mix: quiet tracks come up, loud ones are
     * held back, so volume stays similar from song to song.
     */
    private fun startLeveler() {
        if (android.os.Build.VERSION.SDK_INT < 28 || session <= 0 || leveler != null) return
        try {
            val dp = android.media.audiofx.DynamicsProcessing.Config.Builder(
                android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, false, 0, true, 1, false, 0, true,
            ).build().let { android.media.audiofx.DynamicsProcessing(0, session, it) }
            applyDynamics(dp)
            dp.enabled = true
            leveler = dp
        } catch (t: Throwable) {
            leveler = null
            _state.update { it.copy(levelingSupported = false, balanceSupported = false) }
        }
    }

    private fun applyDynamics(dp: android.media.audiofx.DynamicsProcessing) {
        if (android.os.Build.VERSION.SDK_INT < 28) return
        val st = _state.value
        val on = st.leveling
        // Balance turns the other side down (never up, so nothing clips).
        val b = st.balance / 100f
        val leftDb = if (b > 0f) toDb(1f - b) else 0f
        val rightDb = if (b < 0f) toDb(1f + b) else 0f
        try {
            for (ch in 0 until 2) {
                val band = dp.getMbcBandByChannelIndex(ch, 0)
                band.setEnabled(on)
                band.setCutoffFrequency(20_000f)
                band.setAttackTime(15f)
                band.setReleaseTime(350f)
                band.setRatio(3.5f)
                band.setThreshold(-28f)
                band.setKneeWidth(8f)
                band.setNoiseGateThreshold(-90f)
                band.setExpanderRatio(1f)
                band.setPreGain(0f)
                band.setPostGain(if (on) 9f else 0f)
                dp.setMbcBandByChannelIndex(ch, 0, band)
                val lim = dp.getLimiterByChannelIndex(ch)
                lim.setEnabled(on)
                lim.setAttackTime(1f)
                lim.setReleaseTime(60f)
                lim.setRatio(10f)
                lim.setThreshold(-1.5f)
                lim.setPostGain(0f)
                dp.setLimiterByChannelIndex(ch, lim)
                dp.setInputGainbyChannel(ch, if (ch == 0) leftDb else rightDb)
            }
        } catch (_: Throwable) {
        }
    }

    private fun toDb(gain: Float): Float = if (gain <= 0.001f) -60f else (20f * log10(gain)).coerceAtLeast(-60f)

    private fun stopLeveler() {
        try { leveler?.release() } catch (_: Throwable) {}
        leveler = null
    }

    /** Turns the real visualizer on/off. Needs the microphone permission to be granted first. */
    fun setVisualizer(on: Boolean) {
        prefs?.edit()?.putBoolean("viz_on", on)?.apply()
        _state.update { it.copy(visualizer = on) }
        if (on) startVisualizer() else stopVisualizer()
    }

    @Synchronized
    fun release() {
        releaseEffects()
        session = 0
    }

    private fun readBands(e: Equalizer): List<Band> =
        (0 until e.numberOfBands.toInt()).map { i ->
            Band(e.getCenterFreq(i.toShort()) / 1000, e.getBandLevel(i.toShort()).toInt())
        }

    private fun startVisualizer() {
        if (viz != null || session <= 0) return
        val ctx = appContext ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            val v = Visualizer(session)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) process(fft)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            v.enabled = true
            viz = v
        } catch (_: Throwable) {
            viz = null
        }
    }

    private fun stopVisualizer() {
        viz?.let {
            try {
                it.enabled = false
                it.release()
            } catch (_: Throwable) {
            }
        }
        viz = null
        _spectrum.value = null
    }

    private fun process(fft: ByteArray) {
        val n = fft.size / 2
        if (n < 4) return
        val out = FloatArray(BARS)
        val maxMag = ln(1f + 128f * 1.42f)
        for (b in 0 until BARS) {
            val lo = n.toDouble().pow(b.toDouble() / BARS).toInt().coerceIn(1, n - 1)
            val hi = n.toDouble().pow((b + 1).toDouble() / BARS).toInt().coerceIn(lo + 1, n)
            var peak = 0f
            for (k in lo until hi) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                val mag = hypot(re, im)
                if (mag > peak) peak = mag
            }
            val v = (ln(1f + peak) / maxMag).coerceIn(0f, 1f)
            smooth[b] = maxOf(v, smooth[b] * 0.82f)
            out[b] = smooth[b]
        }
        _spectrum.value = out
    }

    private fun releaseEffects() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bassBoost?.release() } catch (_: Throwable) {}
        try { virt?.release() } catch (_: Throwable) {}
        try { loud?.release() } catch (_: Throwable) {}
        try { reverbFx?.release() } catch (_: Throwable) {}
        eq = null
        bassBoost = null
        virt = null
        loud = null
        reverbFx = null
        stopVisualizer()
        stopLeveler()
    }
}
