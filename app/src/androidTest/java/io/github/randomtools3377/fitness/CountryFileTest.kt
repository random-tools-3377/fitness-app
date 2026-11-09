package io.github.randomtools3377.fitness

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.randomtools3377.fitness.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.GZIPOutputStream

/** Adds a country file, then finds a product by its barcode, as a scan would. */
@RunWith(AndroidJUnit4::class)
class CountryFileTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aCountryFileIsAddedAndItsBarcodesAreFound() {
        val file = File(app.activity.cacheDir, "products-testland.tsv.gz")
        GZIPOutputStream(file.outputStream()).bufferedWriter().use {
            it.write("# Fitness App country file: packaged products sold in Testland, for barcode scanning. Made 2026-11-09.\n")
            it.write("# barcode\tname\tbrand\tkcal\tprotein\tcarbs\tfat\tserving g\tserving label\n")
            it.write("3017620422003\tHazelnut spread\tTestbrand\t539\t6.3\t57.5\t30.9\t15\t1 tbsp\n")
            it.write("0012345678905\tPeanut butter\tOther\t595\t22\t14\t50\t\t\n")
        }
        app.onNodeWithTag("nav:foods").performClick()
        val vm = ViewModelProvider(app.activity)[MainViewModel::class.java]
        vm.importCountry(Uri.fromFile(file))
        app.waitFor(20_000) { app.shownIn("foods-list", "country:Testland") }

        runBlocking {
            val spread = vm.foodForBarcode("3017620422003")!!
            assertEquals("Hazelnut spread", spread.name)
            assertEquals(539.0, spread.per100.kcal, 1e-9)
            // A 12-digit UPC is found under its 13-digit form.
            assertEquals("Peanut butter", vm.foodForBarcode("012345678905")!!.name)
            assertNull(vm.foodForBarcode("4000000000000"))
        }

        // Removing the country takes its products away.
        vm.removeCountry("Testland")
        app.waitFor { gone("country:Testland") }
        runBlocking { assertNull(vm.foodForBarcode("3017620422003")) }
    }
}
