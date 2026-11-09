package io.github.randomtools3377.fitness.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductsTest {
    @Test
    fun barcodesAreStoredAsThirteenOrEightDigits() {
        assertEquals("0012345678905", Products.normalize("012345678905")) // UPC-A
        assertEquals("3017620422003", Products.normalize(" 3017620422003 "))
        assertEquals("3017620422003", Products.normalize("03017620422003")) // GTIN-14
        assertEquals("96385074", Products.normalize("96385074")) // EAN-8
        assertNull(Products.normalize("12345"))
        assertNull(Products.normalize("not a code"))
    }

    @Test
    fun checkDigitsAreChecked() {
        assertTrue(Products.checkDigitOk("3017620422003"))
        assertTrue(Products.checkDigitOk("0012345678905"))
        assertTrue(Products.checkDigitOk("96385074"))
        assertFalse(Products.checkDigitOk("3017620422004"))
    }

    @Test
    fun aCountryFileIsRead() {
        val first = "# Fitness App country file: packaged products sold in France, for barcode scanning. Made 2026-11-09 by tools/build-country-files.py."
        assertEquals("France", Products.countryOf(first))
        assertEquals("United States", Products.countryOf(first.replace("France", "United States")))
        assertNull(Products.countryOf("barcode,name"))
        val p = Products.parseLine("3017620422003\tNutella\tFerrero\t539\t6.3\t57.5\t30.9\t15\t15 g")!!
        assertEquals("Nutella", p.name)
        assertEquals(539.0, p.per100.kcal)
        val food = p.toFood()
        assertEquals("b:P3017620422003", food.key)
        assertEquals(listOf(Portion("15 g", 15.0)), food.portions)
        assertEquals(80.85, food.nutrientsFor(Amount(1.0, "15 g")).kcal, 1e-9)
        val noServing = Products.parseLine("4000000000002\tWater\tY\t0\t0\t0\t0\t\t")!!
        assertEquals(emptyList(), noServing.toFood().portions)
        assertNull(Products.parseLine("# comment"))
        assertNull(Products.parseLine("123\tshort"))
    }
}
