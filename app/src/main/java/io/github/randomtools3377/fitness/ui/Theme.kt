package io.github.randomtools3377.fitness.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.R
import io.github.randomtools3377.fitness.core.Brightness
import io.github.randomtools3377.fitness.core.CardSize
import io.github.randomtools3377.fitness.core.FontChoice
import io.github.randomtools3377.fitness.core.Schemes
import io.github.randomtools3377.fitness.core.ThemeMode
import io.github.randomtools3377.fitness.core.ThemeSettings

/** Whether this phone can match the app's colours to the wallpaper (Android 12 and later). */
val canMatchWallpaper: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The brightness the app is showing now (0.17.0). */
val LocalBrightness = staticCompositionLocalOf { Brightness.LIGHT }

/** How roomy cards are (0.17.0): the padding inside them and the space between their rows. */
data class CardSpacing(val padding: Dp, val gap: Dp)

val LocalCardSpacing = staticCompositionLocalOf { CardSpacing(16.dp, 8.dp) }

fun spacingOf(size: CardSize) = when (size) {
    CardSize.COMPACT -> CardSpacing(10.dp, 4.dp)
    CardSize.COMFORTABLE -> CardSpacing(16.dp, 8.dp)
    CardSize.LARGE -> CardSpacing(22.dp, 12.dp)
}

private fun Int.color() = Color(this)

/** Every colour role built from one colour (copied from the checklist app). */
fun schemeOf(seed: Int, brightness: Brightness): ColorScheme {
    val s = Schemes.of(seed, brightness)
    val base = if (brightness == Brightness.LIGHT) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = s.primary.color(), onPrimary = s.onPrimary.color(), primaryContainer = s.primaryContainer.color(),
        onPrimaryContainer = s.onPrimaryContainer.color(), inversePrimary = s.inversePrimary.color(),
        secondary = s.secondary.color(), onSecondary = s.onSecondary.color(), secondaryContainer = s.secondaryContainer.color(),
        onSecondaryContainer = s.onSecondaryContainer.color(),
        tertiary = s.tertiary.color(), onTertiary = s.onTertiary.color(), tertiaryContainer = s.tertiaryContainer.color(),
        onTertiaryContainer = s.onTertiaryContainer.color(),
        error = s.error.color(), onError = s.onError.color(), errorContainer = s.errorContainer.color(), onErrorContainer = s.onErrorContainer.color(),
        background = s.background.color(), onBackground = s.onBackground.color(),
        surface = s.surface.color(), onSurface = s.onSurface.color(), surfaceVariant = s.surfaceVariant.color(),
        onSurfaceVariant = s.onSurfaceVariant.color(), surfaceTint = s.primary.color(),
        inverseSurface = s.inverseSurface.color(), inverseOnSurface = s.inverseOnSurface.color(),
        outline = s.outline.color(), outlineVariant = s.outlineVariant.color(), scrim = s.scrim.color(),
        surfaceDim = s.surfaceDim.color(), surfaceBright = s.surfaceBright.color(),
        surfaceContainerLowest = s.surfaceContainerLowest.color(), surfaceContainerLow = s.surfaceContainerLow.color(),
        surfaceContainer = s.surfaceContainer.color(), surfaceContainerHigh = s.surfaceContainerHigh.color(),
        surfaceContainerHighest = s.surfaceContainerHighest.color(),
    )
}

/** The wallpaper's dark colours on pure black. */
private fun ColorScheme.onBlack(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0B0B0B), surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF202020),
)

private val Rounded = FontFamily(Font(R.font.nunito_regular), Font(R.font.nunito_bold, FontWeight.Bold))
private val Clear = FontFamily(Font(R.font.atkinson_regular), Font(R.font.atkinson_bold, FontWeight.Bold))
private val Serif = FontFamily(Font(R.font.lora_regular), Font(R.font.lora_bold, FontWeight.Bold))

/** Every text style in the chosen font. */
private fun typographyOf(font: FontChoice): Typography {
    val base = Typography()
    val family = when (font) {
        FontChoice.PHONE -> return base
        FontChoice.ROUNDED -> Rounded
        FontChoice.CLEAR -> Clear
        FontChoice.SERIF -> Serif
    }
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/**
 * Light, dark, pure black or following the phone; the phone's wallpaper colours on Android 12 and
 * later unless a colour is picked, which the whole set of colours is then built from (0.17.0).
 */
@Composable
fun FitnessTheme(theme: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val brightness = when (theme.mode) {
        ThemeMode.LIGHT -> Brightness.LIGHT
        ThemeMode.DARK -> Brightness.DARK
        ThemeMode.BLACK -> Brightness.BLACK
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) Brightness.DARK else Brightness.LIGHT
    }
    val dark = brightness != Brightness.LIGHT
    val context = LocalContext.current
    val seed = theme.seed
    val colors = if (seed == null && canMatchWallpaper) {
        when (brightness) {
            Brightness.LIGHT -> dynamicLightColorScheme(context)
            Brightness.DARK -> dynamicDarkColorScheme(context)
            Brightness.BLACK -> dynamicDarkColorScheme(context).onBlack()
        }
    } else {
        schemeOf(seed ?: io.github.randomtools3377.fitness.core.Accent.TERRACOTTA.seed, brightness)
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
        LocalBrightness provides brightness,
        LocalCardSpacing provides spacingOf(theme.cardSize),
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * theme.textScale),
    ) {
        MaterialTheme(colorScheme = colors, typography = typographyOf(theme.font), content = content)
    }
}

/** The colours charts draw with: the theme's, or colour-blind-friendly ones (0.14.0). */
data class ChartColors(val main: Color, val over: Color, val under: Color, val good: Color, val line: Color, val dots: Color, val shade: Color)

val LocalChartColors = androidx.compose.runtime.staticCompositionLocalOf {
    ChartColors(Color(0xFFB5462F), Color(0xFF7A5900), Color(0xFFC0F0A4), Color(0xFF3F6A2B), Color(0xFF3F6A2B), Color(0xFF857370), Color(0xFFFFDAD2))
}
