package io.github.randomtools3377.fitness

import android.net.Uri
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.randomtools3377.fitness.ui.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Imports a food list from a spreadsheet file and finds the foods in the Foods tab. */
@RunWith(AndroidJUnit4::class)
class FoodImportTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aSpreadsheetAddsItsFoodsAndListsTheRowsItSkipped() {
        val file = File(app.activity.cacheDir, "foods.csv")
        file.writeText(
            "name;brand;per;serving size;serving name;kcal;protein;carbs;fat;portions\n" +
                "Rye crispbread;Nordic;100 g;;;350;10;60;2;1 slice=10\n" +
                "Energy drink;;serving;250 ml;1 can;115;0;27,5;0;\n" +
                "Mystery;;100 g;;;;1;1;1;\n",
        )
        app.onNodeWithTag("nav:foods").performClick()
        val vm = ViewModelProvider(app.activity)[MainViewModel::class.java]
        vm.previewFoodImport(Uri.fromFile(file))

        app.waitFor { !gone("food-import-summary") }
        app.onNodeWithTag("food-import-summary").assertTextEquals("2 foods ready, 1 row skipped")
        app.onNodeWithTag("confirm-food-import").performClick()

        app.waitFor { app.shownIn("foods-list", "my-food:Rye crispbread") }
        app.waitFor { app.shownIn("foods-list", "my-food:Energy drink") }
    }
}
