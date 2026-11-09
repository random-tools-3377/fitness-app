package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelTextTest {
    private fun near(expected: Double, actual: Double?) {
        assertNotNull(actual)
        assertEquals(expected, actual, 0.01)
    }

    @Test
    fun anEuLabelIsReadPer100Grams() {
        val text = """
            NUTRITION INFORMATION
            Typical values   Per 100g   Per 40g serving
            Energy 1567kJ / 372kcal   627kJ / 149kcal
            Fat 7.0g 2.8g
            of which saturates 1.2g 0.5g
            Carbohydrate 58.7g 23.5g
            of which sugars 1.0g 0.4g
            Fibre 9.0g 3.6g
            Protein 13.5g 5.4g
            Salt <0.01g <0.01g
        """.trimIndent()
        val r = LabelText.read(text)!!
        assertFalse(r.perServing)
        near(372.0, r.kcal)
        near(7.0, r.fat)
        near(1.2, r.satFat)
        near(58.7, r.carbs)
        near(1.0, r.sugar)
        near(9.0, r.fibre)
        near(13.5, r.protein)
        near(0.0, r.salt)
        assertFalse(r.fibreTakenOff)
        assertEquals(8, r.found)
    }

    @Test
    fun aUsLabelIsPerServingWithFibreTakenOffTheCarbs() {
        val text = """
            Nutrition Facts
            Serving size 1 bar (45g)
            Calories 190
            Total Fat 6g 8%
            Saturated Fat 1.5g 8%
            Trans Fat 0g
            Sodium 200mg 9%
            Total Carbohydrate 18g 7%
            Dietary Fiber 3g 11%
            Total Sugars 8g
            Includes 6g Added Sugars 12%
            Protein 20g
        """.trimIndent()
        val r = LabelText.read(text)!!
        assertTrue(r.perServing)
        near(45.0, r.servingSize)
        near(190.0, r.kcal)
        near(6.0, r.fat)
        near(1.5, r.satFat)
        near(15.0, r.carbs)
        assertTrue(r.fibreTakenOff)
        near(3.0, r.fibre)
        near(8.0, r.sugar)
        near(20.0, r.protein)
        near(0.5, r.salt) // 200 mg sodium × 2.5
    }

    @Test
    fun decimalCommasKjOnlyAndMillilitres() {
        val text = """
            Nährwerte pro 100 ml
            Energy 180 kJ
            Fat 0,1 g
            Carbohydrate 10,2 g
            of which sugars 9,8 g
            Protein 0,7 g
            Salt 0,01 g
        """.trimIndent()
        val r = LabelText.read(text)!!
        assertFalse(r.perServing)
        assertTrue(r.millilitres)
        near(180.0 / Units.KJ_PER_KCAL, r.kcal)
        near(0.1, r.fat)
        near(10.2, r.carbs)
        near(9.8, r.sugar)
        near(0.01, r.salt)
        assertNull(r.fibre)
    }

    @Test
    fun ocrSlipsWithTheLetterOAreRead() {
        val r = LabelText.read("per 100g\nFat 1O.5g\nProtein O.8g")!!
        near(10.5, r.fat)
        near(0.8, r.protein)
    }

    @Test
    fun textWithNoNumbersGivesNothing() {
        assertNull(LabelText.read("Ingredients: oats, salt"))
        assertNull(LabelText.read(""))
    }
}
