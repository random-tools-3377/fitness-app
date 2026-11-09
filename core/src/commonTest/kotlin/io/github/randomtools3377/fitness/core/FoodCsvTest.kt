package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodCsvTest {
    @Test
    fun theTemplateReadsBackAsThreeFoods() {
        val r = FoodCsv.parse(FoodCsv.TEMPLATE)
        assertEquals(emptyList(), r.problems)
        assertEquals(listOf("Rolled oats", "Protein bar", "Orange juice"), r.foods.map { it.name })
        val oats = r.foods[0]
        assertEquals("Mill & Co", oats.brand)
        assertEquals(372.0, oats.per100.kcal)
        assertEquals(listOf(Portion("1 bowl", 40.0)), oats.portions)
        // 190 kcal per 45 g bar is 422.2 per 100 g, and the bar is kept as a portion.
        val bar = r.foods[1]
        assertEquals(422.22, bar.per100.kcal, 0.01)
        assertEquals(Portion("1 bar", 45.0), bar.portions.first())
        assertEquals(190.0, bar.nutrientsFor(Amount(1.0, "1 bar")).kcal, 1e-9)
        assertEquals(BaseUnit.MILLILITRE, r.foods[2].unit)
        assertEquals("3 foods ready", r.summary())
    }

    @Test
    fun europeanSpreadsheetsWithSemicolonsAndDecimalCommasAreRead() {
        val text = "﻿Name;Brand;Energy (kJ);Protein (g);Carbohydrate (g);Fat (g)\n" +
            "Rye bread;Bäckerei;\"1 050\";8,5;40,1;1,7\n"
        val r = FoodCsv.parse(text)
        assertEquals(emptyList(), r.problems)
        val f = r.foods.single()
        assertEquals(250.96, f.per100.kcal, 0.01)
        assertEquals(8.5, f.per100.protein)
        assertEquals(40.1, f.per100.carbs)
    }

    @Test
    fun quotedCellsKeepTheirCommas() {
        val r = FoodCsv.parse("name,kcal,portions\n\"Cheese, cheddar\",403,\"1 slice=28; 1 cup, diced=132\"\n")
        assertEquals("Cheese, cheddar", r.foods.single().name)
        assertEquals(listOf(Portion("1 slice", 28.0), Portion("1 cup, diced", 132.0)), r.foods.single().portions)
    }

    @Test
    fun badRowsAreSkippedWithTheReasonAndTheRestAreKept() {
        val text = """
            name,per,serving size,kcal,protein,carbs,fat,portions
            Good,100 g,,100,1,1,1,
            ,100 g,,100,1,1,1,
            No energy,100 g,,,1,1,1,
            Bar,serving,,200,1,1,1,
            Odd,per box,,200,1,1,1,
            Heavy,100 g,,100,60,60,1,
            Bad portion,100 g,,100,1,1,1,a slice
            Good,100 g,,100,1,1,1,
        """.trimIndent()
        val r = FoodCsv.parse(text)
        assertEquals(listOf("Good"), r.foods.map { it.name })
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9), r.problems.map { it.line })
        assertTrue(r.problems[2].reason.contains("serving size"))
        assertTrue(r.problems.last().reason.contains("twice"))
        assertEquals("1 food ready, 7 rows skipped", r.summary())
    }

    @Test
    fun aFileWithoutTheNeededColumnsSaysSo() {
        assertTrue(FoodCsv.parse("food;protein\nx;1\n").problems.single().reason.contains("kcal"))
        assertTrue(FoodCsv.parse("kcal\n100\n").problems.single().reason.contains("name"))
        assertTrue(FoodCsv.parse("").problems.single().reason.contains("empty"))
    }

    @Test
    fun foodsAlreadyThereAreLeftOut() {
        val r = FoodCsv.parse(FoodCsv.TEMPLATE)
        val existing = listOf(Food(id = 1, name = "ROLLED OATS", brand = "mill & co", per100 = Nutrients(1.0)))
        val left = FoodCsv.withoutExisting(r, existing)
        assertEquals(listOf("Protein bar", "Orange juice"), left.foods.map { it.name })
        assertTrue(left.problems.single().reason.contains("already"))
        // A deleted food doesn't count.
        assertEquals(3, FoodCsv.withoutExisting(r, existing.map { it.copy(hidden = true) }).foods.size)
    }
}
