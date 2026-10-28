package com.plugyourhome.androradio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4412B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0700),
    secondary = Color(0xFF6E5850),
    secondaryContainer = Color(0xFFF7DDD4),
    onSecondaryContainer = Color(0xFF271712),
    tertiary = Color(0xFF6A5E2F),
    background = Color(0xFFFFF8F6),
    surface = Color(0xFFFFF8F6),
    surfaceContainer = Color(0xFFF8ECE8),
    surfaceContainerHigh = Color(0xFFF2E6E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A2),
    onPrimary = Color(0xFF611404),
    primaryContainer = Color(0xFF8E2A16),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE7BDB2),
    secondaryContainer = Color(0xFF5C3F37),
    onSecondaryContainer = Color(0xFFFFDAD2),
    tertiary = Color(0xFFD7C68D),
    background = Color(0xFF1A110F),
    surface = Color(0xFF1A110F),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
)

@Composable
fun AndroRadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
