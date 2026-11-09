package io.github.randomtools3377.fitness.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Colours for the app's look (0.17.0, copied from the checklist app), as ARGB numbers (0xFFRRGGBB). Any colour the user picks becomes a
 * whole set of colours built from it, the way Material You builds one from the wallpaper: each
 * colour role has a fixed lightness, so text on its background always has enough contrast,
 * whatever colour was picked.
 */
object Colors {
    fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun red(argb: Int) = (argb shr 16) and 0xFF
    fun green(argb: Int) = (argb shr 8) and 0xFF
    fun blue(argb: Int) = argb and 0xFF

    /** "#7A3FB0". */
    fun hex(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

    /** Reads "#7A3FB0", "7a3fb0" or "#7AB"; null when it isn't a colour. */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        val full = when (digits.length) {
            3 -> digits.map { "$it$it" }.joinToString("")
            6 -> digits
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF000000.toInt()) or full.toInt(16)
    }

    // Hue, saturation and value, for the colour square and hue slider.

    /** [hue] in degrees 0–360, [saturation] and [value] 0–1. */
    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - kotlin.math.abs(h % 2 - 1))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return argb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** Hue in degrees, saturation and value 0–1. */
    fun toHsv(argb: Int): Triple<Float, Float, Float> {
        val r = red(argb) / 255f
        val g = green(argb) / 255f
        val b = blue(argb) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return Triple(hue, if (max == 0f) 0f else d / max, max)
    }

    // Lightness as people see it (CIELAB), which is what keeps text readable.

    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun gamma(c: Double): Int {
        val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
        return (v * 255).roundToInt().coerceIn(0, 255)
    }

    /** How bright a colour is, 0 for black to 1 for white, as WCAG measures it. */
    fun luminance(argb: Int): Double = 0.2126 * linear(red(argb)) + 0.7152 * linear(green(argb)) + 0.0722 * linear(blue(argb))

    /** WCAG contrast ratio, from 1 (none) to 21 (black on white). Text needs 4.5 or more. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private const val XN = 0.95047
    private const val ZN = 1.08883
    private const val E = 216.0 / 24389.0
    private const val K = 24389.0 / 27.0

    private fun f(t: Double) = if (t > E) cbrt(t) else (K * t + 16) / 116
    private fun fInverse(t: Double) = if (t * t * t > E) t * t * t else (116 * t - 16) / K

    /** Lightness (0–100), chroma and hue (degrees) of a colour. */
    fun lch(argb: Int): Triple<Double, Double, Double> {
        val r = linear(red(argb))
        val g = linear(green(argb))
        val b = linear(blue(argb))
        val x = 0.41233895 * r + 0.35762064 * g + 0.18051042 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = 0.01932141 * r + 0.11916382 * g + 0.95034478 * b
        val fy = f(y)
        val l = 116 * fy - 16
        val a = 500 * (f(x / XN) - fy)
        val bb = 200 * (fy - f(z / ZN))
        val hue = (atan2(bb, a) * 180 / PI).let { if (it < 0) it + 360 else it }
        return Triple(l, hypot(a, bb), hue)
    }

    private fun rgbOf(l: Double, c: Double, h: Double): DoubleArray {
        val rad = h * PI / 180
        val a = c * cos(rad)
        val b = c * sin(rad)
        val fy = (l + 16) / 116
        val x = XN * fInverse(fy + a / 500)
        val y = if (l > K * E) fy * fy * fy else l / K
        val z = ZN * fInverse(fy - b / 200)
        return doubleArrayOf(
            3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z,
            -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z,
            0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z,
        )
    }

    private fun inGamut(rgb: DoubleArray) = rgb.all { it >= -0.0001 && it <= 1.0001 }

    /**
     * The colour with this lightness and hue, as close to [chroma] as the screen can show. The
     * lightness is kept exactly; the colour gets greyer if it has to.
     */
    fun fromLch(lightness: Double, chroma: Double, hue: Double): Int {
        val l = lightness.coerceIn(0.0, 100.0)
        if (l <= 0.0) return argb(0, 0, 0)
        if (l >= 100.0) return argb(255, 255, 255)
        var low = 0.0
        var high = chroma.coerceAtLeast(0.0)
        if (!inGamut(rgbOf(l, high, hue))) {
            repeat(24) {
                val mid = (low + high) / 2
                if (inGamut(rgbOf(l, mid, hue))) low = mid else high = mid
            }
            high = low
        }
        val rgb = rgbOf(l, high, hue)
        return argb(gamma(rgb[0].coerceIn(0.0, 1.0)), gamma(rgb[1].coerceIn(0.0, 1.0)), gamma(rgb[2].coerceIn(0.0, 1.0)))
    }
}

/** One hue and chroma at every lightness ("tone"), from 0 (black) to 100 (white). */
class TonalPalette(val hue: Double, val chroma: Double) {
    fun tone(tone: Int): Int = Colors.fromLch(tone.toDouble(), chroma, hue)

    companion object {
        fun of(argb: Int, chroma: (Double) -> Double = { it }): TonalPalette {
            val (_, c, h) = Colors.lch(argb)
            return TonalPalette(h, chroma(c))
        }
    }
}

/** Light, dark, or pure black, where black backgrounds save power on many screens. */
enum class Brightness { LIGHT, DARK, BLACK }

/** Every colour role of the app's look, built from one colour. */
data class ColorScheme(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int, val inversePrimary: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
    val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
    val background: Int, val onBackground: Int,
    val surface: Int, val onSurface: Int, val surfaceVariant: Int, val onSurfaceVariant: Int,
    val inverseSurface: Int, val inverseOnSurface: Int,
    val outline: Int, val outlineVariant: Int, val scrim: Int,
    val surfaceDim: Int, val surfaceBright: Int,
    val surfaceContainerLowest: Int, val surfaceContainerLow: Int, val surfaceContainer: Int,
    val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
)

object Schemes {
    /**
     * The whole set of colours from [seed]. The picked colour's hue and colourfulness carry
     * through; the lightness of each role is fixed, as in Material Design, so contrast holds.
     */
    fun of(seed: Int, brightness: Brightness): ColorScheme {
        val (_, chroma, hue) = Colors.lch(seed)
        val p = TonalPalette(hue, chroma.coerceAtLeast(6.0))
        val s = TonalPalette(hue, (chroma / 3).coerceIn(4.0, 24.0))
        val t = TonalPalette((hue + 60) % 360, (chroma / 2).coerceIn(8.0, 40.0))
        val n = TonalPalette(hue, (chroma / 12).coerceAtMost(4.0))
        val nv = TonalPalette(hue, (chroma / 6).coerceAtMost(8.0))
        val e = TonalPalette(33.0, 80.0)
        return if (brightness == Brightness.LIGHT) {
            ColorScheme(
                primary = p.tone(40), onPrimary = p.tone(100), primaryContainer = p.tone(90), onPrimaryContainer = p.tone(10), inversePrimary = p.tone(80),
                secondary = s.tone(40), onSecondary = s.tone(100), secondaryContainer = s.tone(90), onSecondaryContainer = s.tone(10),
                tertiary = t.tone(40), onTertiary = t.tone(100), tertiaryContainer = t.tone(90), onTertiaryContainer = t.tone(10),
                error = e.tone(40), onError = e.tone(100), errorContainer = e.tone(90), onErrorContainer = e.tone(10),
                background = n.tone(98), onBackground = n.tone(10),
                surface = n.tone(98), onSurface = n.tone(10), surfaceVariant = nv.tone(90), onSurfaceVariant = nv.tone(30),
                inverseSurface = n.tone(20), inverseOnSurface = n.tone(95),
                outline = nv.tone(50), outlineVariant = nv.tone(80), scrim = n.tone(0),
                surfaceDim = n.tone(87), surfaceBright = n.tone(98),
                surfaceContainerLowest = n.tone(100), surfaceContainerLow = n.tone(96), surfaceContainer = n.tone(94),
                surfaceContainerHigh = n.tone(92), surfaceContainerHighest = n.tone(90),
            )
        } else {
            val black = brightness == Brightness.BLACK
            ColorScheme(
                primary = p.tone(80), onPrimary = p.tone(20), primaryContainer = p.tone(30), onPrimaryContainer = p.tone(90), inversePrimary = p.tone(40),
                secondary = s.tone(80), onSecondary = s.tone(20), secondaryContainer = s.tone(30), onSecondaryContainer = s.tone(90),
                tertiary = t.tone(80), onTertiary = t.tone(20), tertiaryContainer = t.tone(30), onTertiaryContainer = t.tone(90),
                error = e.tone(80), onError = e.tone(20), errorContainer = e.tone(30), onErrorContainer = e.tone(90),
                background = n.tone(if (black) 0 else 6), onBackground = n.tone(90),
                surface = n.tone(if (black) 0 else 6), onSurface = n.tone(90), surfaceVariant = nv.tone(30), onSurfaceVariant = nv.tone(80),
                inverseSurface = n.tone(90), inverseOnSurface = n.tone(20),
                outline = nv.tone(60), outlineVariant = nv.tone(30), scrim = n.tone(0),
                surfaceDim = n.tone(if (black) 0 else 6), surfaceBright = n.tone(24),
                surfaceContainerLowest = n.tone(if (black) 0 else 4), surfaceContainerLow = n.tone(if (black) 4 else 10),
                surfaceContainer = n.tone(if (black) 6 else 12), surfaceContainerHigh = n.tone(if (black) 10 else 17),
                surfaceContainerHighest = n.tone(if (black) 14 else 22),
            )
        }
    }

}
