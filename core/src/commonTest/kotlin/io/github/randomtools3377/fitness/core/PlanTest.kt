package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTest {
    private val day = LocalDate(2026, 11, 9)
    private val pasta = Food(id = 1, name = "Pasta, dry", per100 = Nutrients(360.0, 12.0, 70.0, 2.0))
    private val chicken = Food(id = 2, name = "Chicken breast", per100 = Nutrients(120.0, 23.0, 0.0, 2.5))

    private fun recipe(cooked: Double? = null) = Recipe(
        id = 5, name = "Chicken pasta", servings = 4.0, cookedGrams = cooked,
        items = listOf(Recipe.item(pasta, Amount(400.0)), Recipe.item(chicken, Amount(600.0))),
    )

    @Test
    fun aRecipeIsSplitIntoServings() {
        val r = recipe()
        assertEquals(2160.0, r.total.kcal, 1e-9) // 1,440 + 720
        assertEquals(540.0, r.perServing.kcal, 1e-9)
        assertEquals(1000.0, r.rawGrams, 1e-9)
        val f = r.toFood()
        assertEquals("b:R5", f.key)
        assertEquals(540.0, f.nutrientsFor(Amount(1.0, "1 serving")).kcal, 1e-9)
        assertTrue(r.problems().isEmpty())
        assertEquals(3, Recipe(name = "", servings = 0.0, items = emptyList()).problems().size)
    }

    @Test
    fun aWeighedDishIsLoggedByItsCookedWeight() {
        // Cooking added water: 1,000 g of food became 1,600 g of dish.
        val f = recipe(cooked = 1600.0).toFood()
        assertEquals(135.0, f.per100.kcal, 1e-9)
        assertEquals(400.0, f.portions.single().amount, 1e-9) // a quarter of the dish
        assertEquals(540.0, f.nutrientsFor(Amount(400.0)).kcal, 1e-9)
    }

    @Test
    fun plannedItemsDontCountUntilTicked() {
        val planned = Plan.plan(pasta, Amount(100.0), day, Meal.DINNER, 1)
        val eaten = Diary.entry(chicken, Amount(100.0), day, Meal.LUNCH, 2)
        assertEquals(120.0, DayTotals.of(listOf(planned, eaten)).total.kcal, 1e-9)
        assertEquals(360.0, Plan.planned(listOf(planned, eaten)).kcal, 1e-9)
        val ticked = Plan.eat(planned, 99)
        assertFalse(ticked.planned)
        assertEquals(99, ticked.createdAt)
        assertEquals(480.0, DayTotals.of(listOf(ticked, eaten)).total.kcal, 1e-9)
    }

    @Test
    fun aPlanCanBeCopiedAWeekOn() {
        val planned = listOf(Plan.plan(pasta, Amount(100.0), day, Meal.DINNER, 1).copy(id = 4))
        val copied = Plan.copy(planned, 7, 50)
        assertEquals(LocalDate(2026, 11, 16), copied.single().day)
        assertTrue(copied.single().planned)
        assertEquals(0, copied.single().id)
    }

    @Test
    fun theShoppingListAddsUpFoodsAndBreaksRecipesUp() {
        val r = recipe()
        val planned = listOf(
            Plan.plan(chicken, Amount(200.0), day, Meal.LUNCH, 1),
            Plan.plan(r.toFood(), Amount(2.0, "1 serving"), day, Meal.DINNER, 2), // half the recipe
            Plan.plan(chicken, Amount(100.0), LocalDate(2026, 11, 10), Meal.LUNCH, 3),
            Diary.quickAdd(200.0, day, Meal.SNACKS, 4).copy(planned = true),
        )
        val list = Plan.shopping(planned, mapOf(5L to r)) { e ->
            when (e.foodKey) {
                "b:R5" -> r.toFood().let { e.amount.baseAmount(it) }
                "u:2" -> e.amount.baseAmount(chicken)
                else -> null
            }
        }
        assertEquals(listOf("Chicken breast", "Pasta, dry"), list.map { it.name })
        assertEquals(600.0, list[0].grams!!, 1e-9) // 200 + 100 + half of 600
        assertEquals(200.0, list[1].grams!!, 1e-9) // half of 400
    }

    @Test
    fun eachMealGetsItsShareOfTheDay() {
        val day = Nutrients(2000.0, 125.0, 250.0, 56.0)
        val t = MealSplit(enabled = true).targets(day)
        assertEquals(500.0, t.getValue(Meal.BREAKFAST).kcal, 1e-9)
        assertEquals(700.0, t.getValue(Meal.LUNCH).kcal, 1e-9)
        assertEquals(43.75, t.getValue(Meal.LUNCH).protein, 1e-9) // 35% of 125 g
        assertNull(MealSplit().problem(2000))
        assertNotNull(MealSplit(shares = MealSplit.DEFAULT + (Meal.SNACKS to 20.0)).problem(2000))
        val kcal = MealSplit(true, MacroMode.GRAMS, mapOf(Meal.BREAKFAST to 400.0, Meal.LUNCH to 800.0, Meal.DINNER to 600.0, Meal.SNACKS to 200.0))
        assertNull(kcal.problem(2000))
        assertEquals(0.4, kcal.targets(day).getValue(Meal.LUNCH).kcal / 2000.0, 1e-9)
    }

    @Test
    fun aFastIsFinishedOnlyWhenItLastedAsPlanned() {
        val h = 3_600_000L
        val going = Fast(startedAt = 0, plannedHours = 16.0)
        assertEquals(4 * h, going.leftMs(12 * h))
        assertEquals(16 * h, going.endsAt())
        assertFalse(going.finished)
        assertTrue(going.copy(endedAt = 16 * h).finished)
        assertFalse(going.copy(endedAt = 15 * h).finished)
        assertEquals("11:32", Fasting.hoursMinutes(11 * h + 32 * 60_000))
        assertEquals("16:8", FastPlan(16.0).title)
        assertEquals("14 hours", FastPlan(14.0).title)
    }
}
