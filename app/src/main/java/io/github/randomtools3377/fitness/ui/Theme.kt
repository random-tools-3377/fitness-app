package io.github.randomtools3377.fitness.ui

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

private val Light = lightColorScheme(
    primary = Color(0xFFB5462F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0600),
    secondary = Color(0xFF3F6A2B),
    secondaryContainer = Color(0xFFC0F0A4),
    onSecondaryContainer = Color(0xFF062100),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF621200),
    primaryContainer = Color(0xFF8F2F1A),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFA5D48A),
    secondaryContainer = Color(0xFF285116),
    onSecondaryContainer = Color(0xFFC0F0A4),
)

/** Uses the phone's wallpaper colours on Android 12 and later. */
@Composable
fun FitnessTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
