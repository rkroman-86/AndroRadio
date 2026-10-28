package com.plugyourhome.androradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.plugyourhome.androradio.ui.AndroRadioScreen
import com.plugyourhome.androradio.ui.MainViewModel
import com.plugyourhome.androradio.ui.theme.AndroRadioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AndroRadioTheme {
                AndroRadioScreen(viewModel)
            }
        }
    }
}
