package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fibre, sugar, saturated fat and salt (0.7.0), and favourites. */
class ExtrasTest {
    @Test
    fun anUnknownExtraDoesNotHideAKnownOne() {
        val a = Nutrients(100.0, fibre = 2.0, salt = 0.5)
        val b = Nutrients(50.0, fibre = 1.0)
        val sum = a + b
        assertEquals(3.0, sum.fibre)
        assertEquals(0.5, sum.salt)
        assertNull(sum.sugar)
        assertEquals(6.0, (a * 3.0).fibre)
        assertTrue(!Nutrients(1.0).hasExtras)
    }

    @Test
    fun builtInFoodsReadTheExtraColumnsAndOldFilesStillWork() {
        val foods = BuiltInFoods.parse("S1\tOats\t372\t13.5\t58.7\t7\t1 bowl=40\t9\t1\t1.2\t0.01\nS2\tOld\t100\t1\t2\t3\t\n")
        assertEquals(9.0, foods[0].per100.fibre)
        assertEquals(1.0, foods[0].per100.sugar)
        assertEquals(1.2, foods[0].per100.satFat)
        assertEquals(0.01, foods[0].per100.salt)
        assertNull(foods[1].per100.fibre)
    }

    @Test
    fun aCountryFileLineCanHaveTheExtras() {
        val p = Products.parseLine("4006381333931\tOats\tMill\t372\t13.5\t58.7\t7\t40\t1 bowl\t9\t1\t\t0.02")!!
        assertEquals(9.0, p.per100.fibre)
        assertNull(p.per100.satFat)
        assertEquals(0.02, p.per100.salt)
    }

    @Test
    fun aSpreadsheetCanGiveSugarSaturatesAndSodium() {
        val r = FoodCsv.parse("name,kcal,protein,carbs,fat,carbs of which sugars,fat of which saturates,sodium mg\nBar,400,10,50,20,30,8,200\n")
        val f = r.foods.single()
        assertEquals(50.0, f.per100.carbs)
        assertEquals(30.0, f.per100.sugar)
        assertEquals(8.0, f.per100.satFat)
        assertEquals(0.5, f.per100.salt!!, 1e-9)
    }

    @Test
    fun sugarCannotBeMoreThanTheCarbs() {
        assertTrue(Label.problems("X", Nutrients(100.0, carbs = 10.0, sugar = 12.0)).isNotEmpty())
    }

    @Test
    fun favouritesComeFirst() {
        val apple = Food(builtInId = "S1", name = "Apple", per100 = Nutrients(52.0))
        val applePie = Food(builtInId = "S2", name = "Apple pie", per100 = Nutrients(237.0))
        val used = mapOf("b:S1" to Usage("b:S1", 5, 0))
        assertEquals(listOf(apple, applePie), FoodSearch.search("apple", listOf(apple, applePie), used, 0))
        assertEquals(listOf(applePie, apple), FoodSearch.search("apple", listOf(apple, applePie), used, 0, favourites = setOf("b:S2")))
        assertEquals(listOf(applePie, apple), FoodSearch.recent(listOf(apple, applePie), used, 0, setOf("b:S2")))
    }
}
