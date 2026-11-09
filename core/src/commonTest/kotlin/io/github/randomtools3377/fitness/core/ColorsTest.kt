package io.github.randomtools3377.fitness.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Copied from the checklist app (0.17.0), with the fitness app's own colours as seeds. */
class ColorsTest {
    private val SEEDS = Accent.entries.map { it.seed } + listOf(0xFFE0567A.toInt(), 0xFF6B8E23.toInt(), 0xFF00897B.toInt(), 0xFF8D6E63.toInt())

    @Test
    fun hexCodesReadAndWrite() {
        assertEquals(0xFF7A3FB0.toInt(), Colors.parseHex("#7A3FB0"))
        assertEquals(0xFF7A3FB0.toInt(), Colors.parseHex(" 7a3fb0 "))
        assertEquals(0xFF77AABB.toInt(), Colors.parseHex("#7AB"))
        assertNull(Colors.parseHex("#7A3FB"))
        assertNull(Colors.parseHex("#GGGGGG"))
        assertEquals("#7A3FB0", Colors.hex(0xFF7A3FB0.toInt()))
        assertEquals("#000000", Colors.hex(0xFF000000.toInt()))
    }

    @Test
    fun hueSaturationAndValueGoBothWays() {
        assertEquals(0xFFFF0000.toInt(), Colors.fromHsv(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), Colors.fromHsv(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), Colors.fromHsv(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), Colors.fromHsv(200f, 0f, 1f))
        for (color in SEEDS) {
            val (h, s, v) = Colors.toHsv(color)
            val back = Colors.fromHsv(h, s, v)
            assertTrue(abs(Colors.red(back) - Colors.red(color)) <= 1 && abs(Colors.green(back) - Colors.green(color)) <= 1 && abs(Colors.blue(back) - Colors.blue(color)) <= 1, Colors.hex(color))
        }
    }

    @Test
    fun aColourKeepsItsLightnessAndHueThroughLch() {
        for (color in SEEDS) {
            val (l, c, h) = Colors.lch(color)
            val back = Colors.fromLch(l, c, h)
            assertTrue(Colors.contrast(back, color) < 1.03, "${Colors.hex(color)} came back as ${Colors.hex(back)}")
        }
        // Too colourful to show at that lightness: it gets greyer but stays as light.
        val pale = Colors.fromLch(95.0, 120.0, 300.0)
        assertTrue(abs(Colors.lch(pale).first - 95.0) < 1.0)
    }

    @Test
    fun textStaysReadableWhateverColourIsPicked() {
        val seeds = SEEDS + (0 until 360 step 15).map { Colors.fromHsv(it.toFloat(), 1f, 1f) } +
            listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt())
        for (seed in seeds) {
            for (brightness in Brightness.entries) {
                val s = Schemes.of(seed, brightness)
                val pairs = listOf(
                    s.primary to s.onPrimary, s.primaryContainer to s.onPrimaryContainer,
                    s.secondary to s.onSecondary, s.secondaryContainer to s.onSecondaryContainer,
                    s.tertiaryContainer to s.onTertiaryContainer, s.error to s.onError, s.errorContainer to s.onErrorContainer,
                    s.background to s.onBackground, s.surface to s.onSurface, s.surfaceVariant to s.onSurfaceVariant,
                    s.surfaceContainerHighest to s.onSurface, s.inverseSurface to s.inverseOnSurface,
                    s.surface to s.primary,
                )
                for ((back, text) in pairs) {
                    assertTrue(Colors.contrast(back, text) >= 4.5, "${Colors.hex(seed)} $brightness: ${Colors.hex(text)} on ${Colors.hex(back)} is ${Colors.contrast(back, text)}")
                }
            }
        }
    }

    @Test
    fun pureBlackIsBlackAndThePickedHueCarriesThrough() {
        val ocean = Schemes.of(0xFF2F80C0.toInt(), Brightness.BLACK)
        assertEquals(0xFF000000.toInt(), ocean.background)
        assertEquals(0xFF000000.toInt(), ocean.surface)
        val light = Schemes.of(0xFF2F80C0.toInt(), Brightness.LIGHT)
        val seedHue = Colors.lch(0xFF2F80C0.toInt()).third
        assertTrue(abs(Colors.lch(light.primary).third - seedHue) < 8, "hue ${Colors.lch(light.primary).third} vs $seedHue")
    }

    @Test
    fun aPickedColourComesFirstAmongTheRecentOnes() {
        var t = ThemeSettings()
        for (c in listOf(1, 2, 3, 4, 5, 6, 7, 3)) t = t.picked(c)
        assertEquals(3, t.color)
        assertEquals(listOf(3, 7, 6, 5, 4, 2), t.recent)
        assertEquals(3, t.seed)
        assertEquals(null, ThemeSettings().seed) // matches the wallpaper
        assertEquals(Accent.OCEAN.seed, ThemeSettings(accent = Accent.OCEAN).seed)
    }

    @Test
    fun aHiddenFirstTabOpensTheDiary() {
        val all = AppTabs.ALL
        assertEquals("WORKOUTS", ThemeSettings(firstTab = "WORKOUTS").opensOn(all))
        assertEquals("DIARY", ThemeSettings(firstTab = "WORKOUTS", hiddenTabs = setOf("WORKOUTS")).opensOn(all))
        assertEquals("DIARY", ThemeSettings(firstTab = "PLAN").opensOn(all - "PLAN")) // switched off on the Features page
    }
}
