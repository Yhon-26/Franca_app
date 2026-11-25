package com.grafito.finanzas.ui.components

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grafito.finanzas.R
import com.grafito.finanzas.data.UserPreferences
import kotlinx.coroutines.flow.catch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal enum class FrancaCue(val resource: Int, val volume: Float) {
    TAP(R.raw.franca_tap, 0.55f),
    SAVED(R.raw.franca_saved, 0.72f)
}

internal val LocalFrancaSounds = staticCompositionLocalOf<(FrancaCue) -> Unit> { {} }

// Both activities share the two samples so closing a review still lets its confirmation finish.
private var processSoundPlayer: FrancaSoundPlayer? = null

@Composable
internal fun ProvideFrancaSounds(content: @Composable () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val soundPreference = remember(application) {
        UserPreferences(application).interfaceSounds.catch { error ->
            if (error is IOException) emit(false) else throw error
        }
    }
    val enabled by soundPreference.collectAsStateWithLifecycle(initialValue = false)
    val player = remember(application, enabled) {
        if (enabled) processSoundPlayer ?: FrancaSoundPlayer(application).also { processSoundPlayer = it }
        else null
    }
    val play = remember(lifecycle, player, enabled) {
        { cue: FrancaCue ->
            if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player?.play(cue)
            Unit
        }
    }
    CompositionLocalProvider(LocalFrancaSounds provides play, content = content)
}

private class FrancaSoundPlayer(private val application: Application) {
    private val audio = application.getSystemService(AudioManager::class.java)
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build().apply {
        setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
    }
    private val samples = FrancaCue.entries.associateWith { pool.load(application, it.resource, 1) }
    private var lastTap = -100L

    fun play(cue: FrancaCue) {
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (cue == FrancaCue.TAP &&
            Settings.System.getInt(application.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return
        val sample = samples.getValue(cue)
        if (sample !in loaded) return
        val now = SystemClock.uptimeMillis()
        if (cue == FrancaCue.TAP) {
            if (now - lastTap < 75) return
            lastTap = now
        }
        pool.play(sample, cue.volume, cue.volume, if (cue == FrancaCue.SAVED) 2 else 1, 0, 1f)
    }
}
