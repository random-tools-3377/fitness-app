package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodTest {
    private val bread = Food(
        id = 7,
        name = "Wholemeal bread",
        per100 = Nutrients(kcal = 250.0, protein = 10.0, carbs = 40.0, fat = 4.0),
        portions = listOf(Portion("1 slice", 36.0)),
    )
    private val milk = Food(
        builtInId = "S171265",
        name = "Milk, whole",
        unit = BaseUnit.MILLILITRE,
        per100 = Nutrients(kcal = 61.0, protein = 3.2, carbs = 4.8, fat = 3.3),
    )
    private val day = LocalDate(2026, 11, 9)

    @Test
    fun nutrientsScaleWithGrams() {
        val n = bread.nutrientsFor(Amount(150.0))
        assertEquals(375.0, n.kcal, 1e-9)
        assertEquals(15.0, n.protein, 1e-9)
        assertEquals(60.0, n.carbs, 1e-9)
        assertEquals(6.0, n.fat, 1e-9)
    }

    @Test
    fun nutrientsScaleWithTheFoodsOwnPortions() {
        val two = bread.nutrientsFor(Amount(2.0, "1 slice"))
        assertEquals(180.0, two.kcal, 1e-9) // 72 g
        assertEquals(36.0, Amount(1.0, "1 slice").baseAmount(bread), 1e-9)
        assertFailsWith<IllegalArgumentException> { Amount(1.0, "1 loaf").baseAmount(bread) }
    }

    @Test
    fun amountsAreDescribedPlainly() {
        assertEquals("150 g", Amount(150.0).describe(BaseUnit.GRAM))
        assertEquals("250 ml", Amount(250.0).describe(BaseUnit.MILLILITRE))
        assertEquals("1 slice", Amount(1.0, "1 slice").describe(BaseUnit.GRAM))
        assertEquals("1.5 × 1 slice", Amount(1.5, "1 slice").describe(BaseUnit.GRAM))
        assertEquals("0.25", formatAmount(0.25))
        assertEquals("2", formatAmount(2.0))
    }

    @Test
    fun aLabelPerServingBecomesPer100() {
        // 30 g serving: 120 kcal, 3 g protein, 20 g carbs, 3 g fat.
        val per100 = Label.per100(Nutrients(120.0, 3.0, 20.0, 3.0), 30.0)
        assertEquals(400.0, per100.kcal, 1e-9)
        assertEquals(10.0, per100.protein, 1e-9)
        assertEquals(66.667, per100.carbs, 0.001)
        assertFailsWith<IllegalArgumentException> { Label.per100(Nutrients(1.0), 0.0) }
    }

    @Test
    fun impossibleLabelsAreCaught() {
        assertTrue(Label.problems("Oats", Nutrients(380.0, 13.0, 60.0, 7.0)).isEmpty())
        assertTrue(Label.problems("", Nutrients(380.0)).isNotEmpty())
        assertTrue(Label.problems("Oats", Nutrients(380.0, 50.0, 60.0, 7.0)).isNotEmpty())
        assertTrue(Label.problems("Oats", Nutrients(-1.0)).isNotEmpty())
        // 100 ml of syrup can hold more than 100 g of sugar.
        assertTrue(Label.problems("Syrup", Nutrients(420.0, 0.0, 105.0, 0.0), BaseUnit.MILLILITRE).isEmpty())
    }

    @Test
    fun aLoggedFoodKeepsItsNumbers() {
        val e = Diary.entry(bread, Amount(2.0, "1 slice"), day, Meal.BREAKFAST, now = 1000)
        assertEquals(180.0, e.nutrients.kcal, 1e-9)
        assertEquals(7L, e.foodId)
        assertNull(e.builtInId)
        assertEquals("u:7", e.foodKey)
        assertEquals("2 × 1 slice", e.describeAmount())

        val m = Diary.entry(milk, Amount(250.0), day, Meal.BREAKFAST, now = 1001)
        assertNull(m.foodId)
        assertEquals("b:S171265", m.foodKey)
        assertEquals(152.5, m.nutrients.kcal, 1e-9)
        assertEquals("250 ml", m.describeAmount())

        val changed = Diary.changeAmount(m, milk, Amount(100.0))
        assertEquals(61.0, changed.nutrients.kcal, 1e-9)
        assertEquals(m.id, changed.id)
    }

    @Test
    fun quickAddIsJustCalories() {
        val q = Diary.quickAdd(300.0, day, Meal.SNACKS, now = 5)
        assertTrue(q.isQuickAdd)
        assertNull(q.foodKey)
        assertEquals(Nutrients(kcal = 300.0), q.nutrients)
        assertEquals("Quick add", q.describeAmount())
        assertFailsWith<IllegalArgumentException> { Diary.quickAdd(0.0, day, Meal.SNACKS, now = 5) }
    }

    @Test
    fun theDayIsTotalledPerMealAndAltogether() {
        val entries = listOf(
            Diary.entry(bread, Amount(100.0), day, Meal.BREAKFAST, 1),
            Diary.entry(milk, Amount(200.0), day, Meal.BREAKFAST, 2),
            Diary.quickAdd(400.0, day, Meal.DINNER, 3),
        )
        val t = DayTotals.of(entries)
        assertEquals(372.0, t.byMeal.getValue(Meal.BREAKFAST).kcal, 1e-9)
        assertEquals(0.0, t.byMeal.getValue(Meal.LUNCH).kcal, 1e-9)
        assertEquals(400.0, t.byMeal.getValue(Meal.DINNER).kcal, 1e-9)
        assertEquals(772.0, t.total.kcal, 1e-9)
        assertEquals(16.4, t.total.protein, 1e-9)
    }

    @Test
    fun aMealOrADayCanBeCopiedToAnotherDay() {
        val yesterday = LocalDate(2026, 11, 8)
        val entries = listOf(
            Diary.entry(bread, Amount(100.0), yesterday, Meal.BREAKFAST, 1).copy(id = 1),
            Diary.entry(milk, Amount(200.0), yesterday, Meal.LUNCH, 2).copy(id = 2),
        )
        val copied = Diary.copy(entries, day, now = 100)
        assertEquals(listOf(0L, 0L), copied.map { it.id })
        assertEquals(listOf(day, day), copied.map { it.day })
        assertEquals(listOf(Meal.BREAKFAST, Meal.LUNCH), copied.map { it.meal })
        assertEquals(entries.map { it.nutrients }, copied.map { it.nutrients })

        val intoDinner = Diary.copy(entries.take(1), day, now = 100, toMeal = Meal.DINNER)
        assertEquals(Meal.DINNER, intoDinner.single().meal)
    }

    @Test
    fun aSavedMealLogsItsItemsTogether() {
        val entries = listOf(
            Diary.entry(bread, Amount(2.0, "1 slice"), day, Meal.BREAKFAST, 1),
            Diary.entry(milk, Amount(200.0), day, Meal.BREAKFAST, 2),
        )
        val meal = SavedMeal.from("Usual breakfast", entries, now = 9)
        assertEquals(302.0, meal.nutrients.kcal, 1e-9)
        val logged = meal.toEntries(LocalDate(2026, 11, 10), Meal.LUNCH, now = 50)
        assertEquals(2, logged.size)
        assertTrue(logged.all { it.meal == Meal.LUNCH && it.day == LocalDate(2026, 11, 10) })
        assertEquals("u:7", logged[0].foodKey)
        assertEquals(Amount(2.0, "1 slice"), logged[0].amount)
        assertFailsWith<IllegalArgumentException> { SavedMeal.from(" ", entries, 1) }
        assertFailsWith<IllegalArgumentException> { SavedMeal.from("Empty", emptyList(), 1) }
    }
}
