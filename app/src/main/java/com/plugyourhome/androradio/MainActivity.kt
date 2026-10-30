package com.plugyourhome.androradio

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.plugyourhome.androradio.ui.HomeScreen
import com.plugyourhome.androradio.ui.MainViewModel
import com.plugyourhome.androradio.ui.theme.AndroRadioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AndroRadioTheme {
                HomeScreen(viewModel)
            }
        }
    }

    /** Pendant une diffusion (DLNA ou Cast), les touches de volume pilotent l'appareil distant. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            KeyEvent.KEYCODE_VOLUME_MUTE -> 0
            else -> return super.dispatchKeyEvent(event)
        }
        val p = viewModel.player.state.value
        if (!p.isCasting && p.dlnaId == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) viewModel.onVolumeKey(direction)
        return true // on consomme aussi le relâchement : pas de volume du téléphone en parallèle
    }
}
