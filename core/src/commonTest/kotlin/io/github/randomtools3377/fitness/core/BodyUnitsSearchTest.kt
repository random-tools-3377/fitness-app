package io.github.randomtools3377.fitness.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyUnitsSearchTest {
    private fun d(day: Int) = LocalDate(2026, 11, day)

    @Test
    fun theLatestWeighInWins() {
        val w = listOf(WeighIn(1, d(1), 80.0, 10), WeighIn(2, d(3), 79.0, 30), WeighIn(3, d(3), 78.8, 40), WeighIn(4, d(2), 79.5, 50))
        assertEquals(78.8, Weight.latest(w)!!.kg)
        assertNull(Weight.latest(emptyList()))
        assertEquals(listOf(d(1) to 80.0, d(2) to 79.5, d(3) to 78.8), Weight.daily(w))
    }

    @Test
    fun theSevenDayAverageCoversThatDayAndTheSixBefore() {
        val w = (1..10).map { WeighIn(it.toLong(), d(it), 80.0 - it * 0.1, it.toLong()) }
        val avg = Weight.sevenDayAverage(w)
        assertEquals(10, avg.size)
        assertEquals(79.9, avg[0].second, 1e-9) // only day 1
        assertEquals(79.75, avg[3].second, 1e-9) // days 1–4
        // Day 10: days 4 to 10, 79.6 down to 79.0.
        assertEquals(79.3, avg[9].second, 1e-9)
    }

    @Test
    fun waterAddsUpForTheDay() {
        val e = listOf(WaterEntry(1, d(9), 250), WaterEntry(2, d(9), 500), WaterEntry(3, d(8), 250))
        assertEquals(750, Water.total(e, d(9)))
        assertEquals(WaterSettings(2000, 250, 500), WaterSettings())
    }

    @Test
    fun unitsConvertAndShow() {
        assertEquals(176.4, Units.kgToShown(80.0, WeightUnit.LB), 0.05)
        assertEquals(80.0, Units.shownToKg(Units.kgToShown(80.0, WeightUnit.LB), WeightUnit.LB), 1e-9)
        assertEquals("80 kg", Units.weight(80.0, WeightUnit.KG))
        assertEquals("176.4 lb", Units.weight(80.0, WeightUnit.LB))
        assertEquals("5 ft 11 in", Units.height(180.0, HeightUnit.FT_IN))
        assertEquals("180 cm", Units.height(180.0, HeightUnit.CM))
        assertEquals(177.8, Units.feetInchesToCm(5, 10.0), 1e-9)
        assertEquals("2,000 kcal", Units.energy(2000.0, EnergyUnit.KCAL))
        assertEquals("8,368 kJ", Units.energy(2000.0, EnergyUnit.KJ))
        assertEquals("8.5 fl oz", Units.volume(250.0, VolumeUnit.FL_OZ))
        assertEquals("1,500 ml", Units.volume(1500.0, VolumeUnit.ML))
        assertEquals("4.5 g", Units.grams(4.49))
        assertEquals("32 g", Units.grams(31.6))
        assertEquals(1.5, Units.parse(" 1,5 "))
        assertNull(Units.parse("abc"))
        assertNull(Units.parse(""))
    }

    @Test
    fun unitsFollowTheRegion() {
        assertEquals(WeightUnit.LB, UnitSettings.forRegion("US").weight)
        assertEquals(VolumeUnit.FL_OZ, UnitSettings.forRegion("us").volume)
        assertEquals(UnitSettings(), UnitSettings.forRegion("DE"))
        assertEquals(EnergyUnit.KJ, UnitSettings.forRegion("AU").energy)
        assertEquals(DistanceUnit.MILES, UnitSettings.forRegion("GB").distance)
    }

    private val foods = listOf(
        Food(builtInId = "S1", name = "Bananas, raw", per100 = Nutrients(89.0)),
        Food(builtInId = "N2", name = "Banana bread", per100 = Nutrients(326.0)),
        Food(builtInId = "S3", name = "Bread, white, commercially prepared", per100 = Nutrients(266.0)),
        Food(id = 4, name = "Brown bread", brand = "Corner bakery", per100 = Nutrients(240.0)),
        Food(builtInId = "S5", name = "Crème fraîche", per100 = Nutrients(390.0)),
    )

    @Test
    fun searchMatchesTheStartOfEachWordTyped() {
        assertTrue(FoodSearch.matches(foods[0], "ban"))
        assertTrue(FoodSearch.matches(foods[1], "bread ban"))
        assertFalse(FoodSearch.matches(foods[0], "bread"))
        assertTrue(FoodSearch.matches(foods[3], "corner"))
        assertTrue(FoodSearch.matches(foods[4], "creme fraiche"))
        assertFalse(FoodSearch.matches(foods[0], "  "))
    }

    @Test
    fun usedFoodsComeFirstThenTheUsersOwn() {
        val now = 100L * 86_400_000L
        val plain = FoodSearch.search("bread", foods, emptyMap(), now)
        assertEquals("Brown bread", plain.first().name) // typed in by the user
        val used = mapOf("b:S3" to Usage("b:S3", 3, now))
        assertEquals("Bread, white, commercially prepared", FoodSearch.search("bread", foods, used, now).first().name)
    }

    @Test
    fun recentAndFrequentFoodsComeFirst() {
        val day = 86_400_000L
        val now = 100 * day
        val usages = mapOf(
            "b:S1" to Usage("b:S1", 20, now - 60 * day), // often, but long ago
            "b:N2" to Usage("b:N2", 3, now - day), // a few times, lately
            "u:4" to Usage("u:4", 1, now - 2 * day),
        )
        val recent = FoodSearch.recent(foods, usages, now)
        assertEquals(listOf("Banana bread", "Bananas, raw", "Brown bread"), recent.map { it.name })
    }

    @Test
    fun theBundledFoodListIsRead() {
        val text = """
            # comment
            S171705	Egg, whole, raw, fresh	143	12.6	0.7	9.5	1 large=50|1 medium=44
            N1	Water, tap	0	0	0	0
            bad line
        """.trimIndent()
        val list = BuiltInFoods.parse(text)
        assertEquals(2, list.size)
        val egg = list[0]
        assertEquals("S171705", egg.builtInId)
        assertEquals("b:S171705", egg.key)
        assertEquals(143.0, egg.per100.kcal)
        assertEquals(listOf(Portion("1 large", 50.0), Portion("1 medium", 44.0)), egg.portions)
        assertEquals(71.5, egg.nutrientsFor(Amount(1.0, "1 large")).kcal, 1e-9)
        assertTrue(list[1].portions.isEmpty())
    }

    @Test
    fun aBuiltInFoodWithTheUsersOwnCopyIsHiddenUntilTheCopyGoes() {
        val per100 = Nutrients(250.0, 9.0, 45.0, 3.0, micros = mapOf("iron" to 2.0))
        val bread = Food(builtInId = "S1", name = "Bread, white", per100 = per100, portions = listOf(Portion("1 slice", 29.0)))
        val rice = Food(builtInId = "S2", name = "Rice", per100 = per100)
        val copy = FoodSearch.copyOf(bread)
        kotlin.test.assertNull(copy.builtInId)
        assertEquals(bread.per100, copy.per100) // vitamins and minerals kept too
        assertEquals(bread.portions, copy.portions)
        val mine = copy.copy(id = 7, name = "Bread (local bakery)")
        val replaced = mapOf("S1" to 7L)
        assertEquals(listOf(rice), FoodSearch.visibleBuiltIns(listOf(bread, rice), replaced, listOf(mine)))
        // Deleted (hidden) or gone: the built-in bread is back.
        assertEquals(listOf(bread, rice), FoodSearch.visibleBuiltIns(listOf(bread, rice), replaced, listOf(mine.copy(hidden = true))))
        assertEquals(listOf(bread, rice), FoodSearch.visibleBuiltIns(listOf(bread, rice), replaced, emptyList()))
    }

    @Test
    fun foodsCanBeNarrowedToATypeAndATableComesFirst() {
        val n = Nutrients(100.0, 1.0, 1.0, 1.0)
        val usBread = Food(builtInId = "S1", name = "Bread, white", per100 = n, category = "bread")
        val euBread = Food(builtInId = "C2", name = "Bread, white, French", per100 = n, category = "bread")
        val cheese = Food(builtInId = "C3", name = "Cheese bread", per100 = n, category = "dairy")
        val all = listOf(usBread, euBread, cheese)
        assertEquals(listOf(euBread, usBread), FoodSearch.search("bread", all, emptyMap(), 0, type = "bread", prefer = FoodSearch.EU))
        assertEquals(usBread, FoodSearch.search("bread", all, emptyMap(), 0, prefer = FoodSearch.US).first())
        // A type with nothing typed lists that type.
        assertEquals(listOf(euBread, usBread), FoodSearch.search("", all, emptyMap(), 0, type = "bread", prefer = FoodSearch.EU))
        assertEquals(FoodSearch.EU, FoodSearch.preferred(null, "fr"))
        assertEquals(FoodSearch.US, FoodSearch.preferred(null, "US"))
        assertEquals(null, FoodSearch.preferred(FoodSearch.NO_PREFERENCE, "FR"))
        assertEquals(FoodSearch.US, FoodSearch.preferred(FoodSearch.US, "FR"))
        val parsed = BuiltInFoods.parse((listOf("S1", "Bread", "250", "9", "45", "3") + List(17) { "" } + "bread").joinToString("\t") + "\nS2\tOld\t1\t1\t1\t1\n")
        assertEquals(listOf("bread", null), parsed.map { it.category })
    }
}
