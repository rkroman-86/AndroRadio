package com.plugyourhome.androradio.playback

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Volume « multimédia » du téléphone (le même que les touches de volume), entre 0 et 1.
 * Suit aussi les changements faits ailleurs (touches, autre appli).
 */
class VolumeControl(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private val _level = MutableStateFlow(read())
    val level: StateFlow<Float> = _level.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            _level.value = read()
        }
    }

    init {
        appContext.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    private fun read(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max

    /** Règle le volume (0..1), arrondi au cran le plus proche du téléphone. */
    fun set(value: Float) {
        val steps = (value.coerceIn(0f, 1f) * max).roundToInt()
        _level.value = steps.toFloat() / max
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0) }
    }

    fun release() {
        appContext.contentResolver.unregisterContentObserver(observer)
    }
}
