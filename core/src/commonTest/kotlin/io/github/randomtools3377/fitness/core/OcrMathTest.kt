package io.github.randomtools3377.fitness.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrMathTest {
    @Test
    fun theTextFindingSizeIsMultiplesOf32WithinLimits() {
        assertEquals(736 to 1312, OcrMath.detSize(300, 534)) // short side up to 736
        assertEquals(1504 to 2016, OcrMath.detSize(3000, 4000).let { it }) // long side down to about 2000
        val (w, h) = OcrMath.detSize(1000, 800)
        assertTrue(w % 32 == 0 && h % 32 == 0)
    }

    @Test
    fun aPatchOfTextBecomesOneBoxWithRoomAround() {
        val mw = 100
        val mh = 50
        val map = FloatArray(mw * mh)
        for (y in 20 until 30) for (x in 10 until 70) map[y * mw + x] = 0.9f
        val boxes = OcrMath.boxes(map, mw, mh, 200, 100) // the photo is twice the map's size
        assertEquals(1, boxes.size)
        val b = boxes[0]
        assertTrue(abs(b.uy) < 1e-9 && b.ux > 0.99, "along the line")
        assertTrue(b.length > 120 && b.height > 20, "${b.length} × ${b.height}")
        assertEquals(50.0, b.centreY, 1.5)
    }

    @Test
    fun faintPatchesAndSpecksAreLeftOut() {
        val mw = 60
        val mh = 30
        val map = FloatArray(mw * mh)
        for (y in 5 until 10) for (x in 5 until 40) map[y * mw + x] = 0.4f // above 0.3 but scores under 0.5
        map[20 * mw + 50] = 0.9f // a speck
        assertTrue(OcrMath.boxes(map, mw, mh, mw, mh).isEmpty())
    }

    @Test
    fun aSlantedLineGetsATurnedBox() {
        val mw = 200
        val mh = 100
        val map = FloatArray(mw * mh)
        for (x in 20 until 180) {
            val y0 = 40 + (x - 20) / 10 // drops 16 pixels across: about 5.7 degrees
            for (y in y0 until y0 + 8) map[y * mw + x] = 0.9f
        }
        val b = OcrMath.boxes(map, mw, mh, mw, mh).single()
        assertTrue(b.uy > 0.05 && b.uy < 0.15, "uy ${b.uy}")
        assertTrue(b.height < 30, "height ${b.height}") // a level box would need about 37
    }

    @Test
    fun decodingDropsRepeatsBlanksAndForeignLetters() {
        val alphabet = OcrMath.alphabet("a\nb\n中\n1")
        // classes: 0 nothing, 1 a, 2 b, 3 中, 4 1, 5 space
        val allowed = OcrMath.allowed(alphabet)
        assertTrue(!allowed[3])
        val steps = listOf(1, 1, 0, 1, 3, 5, 4)
        val scores = FloatArray(steps.size * alphabet.size)
        steps.forEachIndexed { t, c -> scores[t * alphabet.size + c] = 5f }
        // At the step where 中 is best, "b" comes second.
        scores[4 * alphabet.size + 2] = 3f
        assertEquals("aab 1", OcrMath.decode(scores, steps.size, alphabet.size, alphabet, allowed))
    }

    @Test
    fun wordsAreJoinedIntoLines() {
        fun box(x: Double, y: Double) = OcrMath.Box(1.0, 0.0, 0.0, 1.0, x, x + 50, y, y + 20)
        val text = OcrMath.lines(listOf(box(100.0, 52.0) to "7.0g", box(0.0, 50.0) to "Fat", box(0.0, 100.0) to "Salt", box(100.0, 101.0) to "1g"))
        assertEquals("Fat 7.0g\nSalt 1g", text)
    }

    @Test
    fun thePhotoIsPutInBlueGreenRedOrder() {
        val px = intArrayOf(0xFFFF0000.toInt()) // one red pixel
        val x = OcrMath.detInput(px, 1, 1, 1, 1)
        assertEquals(-1f, x[0], 1e-6f) // blue
        assertEquals(-1f, x[1], 1e-6f) // green
        assertEquals(1f, x[2], 1e-6f) // red
    }
}
