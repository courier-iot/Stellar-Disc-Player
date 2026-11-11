package com.stellapp.disc.playback

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * Equalizer, bass boost and the real visualizer. All three hook into the player's
 * audio session, which the playback service hands over via [attach].
 */
object AudioFx {
    const val BARS = 32

    data class Band(val centerHz: Int, val level: Int)

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
    )

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var session = 0
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var viz: Visualizer? = null
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
            )
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

        if (_state.value.visualizer) startVisualizer()
    }

    fun setEnabled(on: Boolean) {
        prefs?.edit()?.putBoolean("eq_on", on)?.apply()
        try {
            eq?.enabled = on
            bassBoost?.enabled = on && _state.value.bass > 0
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
        eq = null
        bassBoost = null
        stopVisualizer()
    }
}
