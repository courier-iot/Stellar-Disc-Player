package com.stellapp.disc

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.stellapp.disc.ui.DiscApp
import com.stellapp.disc.ui.LocalCalmMotion
import com.stellapp.disc.ui.rememberPowerSaveMode
import com.stellapp.disc.ui.theme.DiscTheme

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.handleShare(intent)
        setContent {
            val accent by vm.accentIndex.collectAsStateWithLifecycle()
            DiscTheme(accentIndex = accent) {
                // Battery Saver calms every animation in the app.
                val powerSave = rememberPowerSaveMode()
                CompositionLocalProvider(LocalCalmMotion provides powerSave) {
                    DiscApp(vm = vm, inPip = inPip.value, onEnterPip = { enterPip() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleShare(intent)
    }

    /** Leaving the app while a fullscreen video plays pops it into a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.videoFullscreen.value && vm.state.value.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    private fun enterPip(): Boolean {
        val s = vm.state.value
        val w = s.videoWidth.coerceAtLeast(1)
        val h = s.videoHeight.coerceAtLeast(1)
        val ratio = (w.toFloat() / h).let {
            when {
                s.videoWidth == 0 -> Rational(16, 9)
                it > 2.39f -> Rational(239, 100)
                it < 1f / 2.39f -> Rational(100, 239)
                else -> Rational(w, h)
            }
        }
        return try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        } catch (e: Exception) {
            false
        }
    }
}
