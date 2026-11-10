package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiTextTest {
    private fun food(name: String, id: String, vararg portions: Pair<String, Double>, unit: BaseUnit = BaseUnit.GRAM) =
        Food(builtInId = id, name = name, unit = unit, per100 = Nutrients(100.0, 1.0, 1.0, 1.0), portions = portions.map { Portion(it.first, it.second) })

    private val egg = food("Egg, whole, cooked, hard-boiled", "1", "1 large" to 50.0)
    private val bread = food("Bread, white, commercially prepared", "2", "1 slice" to 29.0)
    private val butter = food("Butter, salted", "3", "1 tbsp" to 14.2)
    private val milk = food("Semi-skimmed milk", "4", unit = BaseUnit.MILLILITRE)
    private val eggplant = food("Eggplant, raw", "5")
    private val foods = listOf(egg, bread, butter, milk, eggplant)

    @Test
    fun theModelsLinesAreRead() {
        val items = AiText.items("boiled egg | 2 | piece\nwhite bread | 1 | slice\nnot a line\nbutter | 0,5 | tbsp\nboiled egg | 2 | piece\nflour (not listed) | 1 | tsp\nbanana</think> | 1 | litre\n")
        assertEquals(
            listOf(
                AiText.Item(2.0, "piece", "boiled egg"), AiText.Item(1.0, "slice", "white bread"), AiText.Item(0.5, "tbsp", "butter"),
                AiText.Item(1.0, "tsp", "flour"), AiText.Item(1.0, "serving", "banana"),
            ),
            items,
        )
    }

    @Test
    fun aNameFindsTheFoodEvenWhenTheWordsDiffer() {
        assertEquals(egg, AiText.candidates("boiled eggs", foods, emptyMap(), 0).first())
        assertEquals(bread, AiText.candidates("white bread", foods, emptyMap(), 0).first())
        assertEquals(milk, AiText.candidates("semi-skimmed milk", foods, emptyMap(), 0).first())
        // "egg" is the food itself, so the eggplant comes after the egg.
        val c = AiText.candidates("fried egg", foods, emptyMap(), 0)
        assertEquals(egg, c.first())
    }

    @Test
    fun foodsUsedBeforeComeFirst() {
        val mine = Food(id = 9, name = "Toast (my bread)", per100 = Nutrients(250.0, 9.0, 45.0, 3.0), portions = listOf(Portion("1 slice", 35.0)))
        val usages = mapOf(mine.key to Usage(mine.key, 20, 0))
        assertEquals(mine, AiText.candidates("toast", foods + mine, usages, 0).first())
    }

    @Test
    fun unitsBecomeTheFoodsOwnPortions() {
        assertEquals(AiText.Fit(Amount(2.0, "1 large"), false), AiText.amountFor(egg, 2.0, "piece"))
        assertEquals(AiText.Fit(Amount(1.0, "1 slice"), false), AiText.amountFor(bread, 1.0, "slice"))
        assertEquals(AiText.Fit(Amount(0.5, "1 tbsp"), false), AiText.amountFor(butter, 0.5, "tbsp"))
        assertEquals(AiText.Fit(Amount(200.0), false), AiText.amountFor(egg, 200.0, "g"))
        // A glass of milk has no portion: a usual glass, marked as a guess.
        val glass = AiText.amountFor(milk, 1.0, "glass")
        assertEquals(Amount(250.0), glass.amount)
        assertTrue(glass.guessed)
        assertTrue(AiText.amountFor(milk, 100.0, "g").guessed)
        assertFalse(AiText.amountFor(milk, 100.0, "ml").guessed)
    }

    @Test
    fun aRecipesNameServingsAndMethodAreFound() {
        val r = AiText.recipeParts("Simple chilli con carne\nServes 4\n\nIngredients\n1 onion\n\nMethod\nFry the onion.\nAdd the rest.")
        assertEquals("Simple chilli con carne", r.name)
        assertEquals(4, r.servings)
        assertEquals("Fry the onion.\nAdd the rest.", r.method)
        val p = AiText.recipeParts("Banana pancakes (makes 8)\n2 bananas")
        assertEquals("Banana pancakes", p.name)
        assertEquals(8, p.servings)
        assertEquals("", p.method)
    }

    @Test
    fun speechAndPlateAnswersAreTidied() {
        assertEquals("Two eggs and a slice of toast.", AiText.heard("language English<asr_text>Two eggs and a slice of toast."))
        assertEquals("Two eggs.", AiText.heard("language English Two eggs."))
        assertEquals(listOf("fried egg", "baked beans", "toast"), AiText.names("fried egg\nbaked beans\n- toast\nfried egg\n"))
        assertEquals(listOf("vegetables", "rice", "meat"), AiText.names("vegetables, rice, meat,\nvegetables, rice,"))
        assertEquals(listOf("fish", "chips", "peas"), AiText.names("fish\nand chips\npeas and\nand\n"))
        assertEquals(listOf("Spaghetti", "peas", "broccoli"), AiText.names("Spaghetti with peas, broccoli on plate\nplate"))
    }

    @Test
    fun theWeeksFactsAreOnlyTheNumbersGiven() {
        val t = AiText.weekFacts("2 Nov", "8 Nov", 7, 2140, 2000, 4, 96, 125, 3, 3, 5, "down 0.3 kg on the 7-day average")
        assertTrue("Average calories: 2,140 kcal a day; target 2,000 kcal." in t, t)
        assertTrue("Workouts: 3; their goal is 3 a week." in t, t)
        val bare = AiText.weekFacts("2 Nov", "8 Nov", 2, null, null, null, null, null, null, null, null, null)
        assertEquals("The week of 2 Nov to 8 Nov.\nDays with food logged: 2 of 7.", bare)
    }
}
