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

/** A light and a dark scheme for each accent colour (0.10.0). */
private fun schemes(accent: io.github.randomtools3377.fitness.core.Accent) = when (accent) {
    io.github.randomtools3377.fitness.core.Accent.GREEN -> lightColorScheme(
        primary = Color(0xFF2E6B30), onPrimary = Color.White, primaryContainer = Color(0xFFB1F2A9), onPrimaryContainer = Color(0xFF002204),
        secondary = Color(0xFF52634F), secondaryContainer = Color(0xFFD5E8CF), onSecondaryContainer = Color(0xFF101F0F),
    ) to darkColorScheme(
        primary = Color(0xFF96D58F), onPrimary = Color(0xFF00390A), primaryContainer = Color(0xFF12521A), onPrimaryContainer = Color(0xFFB1F2A9),
        secondary = Color(0xFFB9CCB4), secondaryContainer = Color(0xFF3B4B39), onSecondaryContainer = Color(0xFFD5E8CF),
    )
    io.github.randomtools3377.fitness.core.Accent.BLUE -> lightColorScheme(
        primary = Color(0xFF2B5EA7), onPrimary = Color.White, primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E),
        secondary = Color(0xFF555F71), secondaryContainer = Color(0xFFD9E3F8), onSecondaryContainer = Color(0xFF121C2B),
    ) to darkColorScheme(
        primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF003063), primaryContainer = Color(0xFF07468D), onPrimaryContainer = Color(0xFFD6E3FF),
        secondary = Color(0xFFBDC7DC), secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFD9E3F8),
    )
    io.github.randomtools3377.fitness.core.Accent.PURPLE -> lightColorScheme(
        primary = Color(0xFF6C4EA2), onPrimary = Color.White, primaryContainer = Color(0xFFEBDCFF), onPrimaryContainer = Color(0xFF260059),
        secondary = Color(0xFF635B70), secondaryContainer = Color(0xFFE9DEF8), onSecondaryContainer = Color(0xFF1F182A),
    ) to darkColorScheme(
        primary = Color(0xFFD4BBFF), onPrimary = Color(0xFF3C1C70), primaryContainer = Color(0xFF543588), onPrimaryContainer = Color(0xFFEBDCFF),
        secondary = Color(0xFFCDC2DB), secondaryContainer = Color(0xFF4B4358), onSecondaryContainer = Color(0xFFE9DEF8),
    )
    else -> Light to Dark
}

/**
 * Light or dark as chosen, following the phone to start; the phone's wallpaper colours on
 * Android 12 and later unless another accent is picked (0.10.0).
 */
@Composable
fun FitnessTheme(theme: io.github.randomtools3377.fitness.core.ThemeSettings = io.github.randomtools3377.fitness.core.ThemeSettings(), content: @Composable () -> Unit) {
    val dark = when (theme.mode) {
        io.github.randomtools3377.fitness.core.ThemeMode.LIGHT -> false
        io.github.randomtools3377.fitness.core.ThemeMode.DARK -> true
        io.github.randomtools3377.fitness.core.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = if (theme.accent == io.github.randomtools3377.fitness.core.Accent.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val (light, darkScheme) = schemes(theme.accent)
        if (dark) darkScheme else light
    }
    val chart = if (theme.colourBlind) {
        // Okabe–Ito colours: told apart with any kind of colour blindness (0.14.0).
        ChartColors(
            main = Color(0xFF0072B2), over = Color(0xFFE69F00), under = Color(0xFF56B4E9), good = Color(0xFF0072B2),
            line = if (dark) Color(0xFFF0F0F0) else Color(0xFF000000), dots = Color(0xFF999999), shade = Color(0x55CC79A7),
        )
    } else {
        ChartColors(
            main = colors.primary, over = colors.tertiary, under = colors.secondaryContainer, good = colors.secondary,
            line = colors.secondary, dots = colors.outline, shade = colors.tertiaryContainer,
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalChartColors provides chart,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * theme.textScale),
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** The colours charts draw with: the theme's, or colour-blind-friendly ones (0.14.0). */
data class ChartColors(val main: Color, val over: Color, val under: Color, val good: Color, val line: Color, val dots: Color, val shade: Color)

val LocalChartColors = androidx.compose.runtime.staticCompositionLocalOf {
    ChartColors(Color(0xFFB5462F), Color(0xFF7A5900), Color(0xFFC0F0A4), Color(0xFF3F6A2B), Color(0xFF3F6A2B), Color(0xFF857370), Color(0xFFFFDAD2))
}
