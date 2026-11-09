package io.github.randomtools3377.fitness

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.fitness.core.UnitSettings
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Starts each test with no data, the way the app is after installing, but with metric units and
 * kcal whatever the emulator's region, so the numbers below are the same everywhere.
 */
class FreshDatabase : ExternalResource() {
    override fun before() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FitnessApplication
        app.container.db.clearAllTables()
        runBlocking { app.container.repository.updateSettings { it.copy(units = UnitSettings()) } }
    }
}

/** Logs foods on a real (emulated) phone and checks the day's totals. */
@RunWith(AndroidJUnit4::class)
class DiaryTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    /** Whether [tag] shows [text]; in the diary, it scrolls there first. */
    private fun textIs(tag: String, text: String): Boolean = try {
        if (!gone("diary-list")) app.shownIn("diary-list", tag)
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }

    @Test
    fun aFoodTypedInFromItsLabelAddsToTheDay() {
        app.waitFor { textIs("day-kcal", "0 kcal") }

        // Oats, per 100 g: 380 kcal, 13 g protein, 60 g carbs, 7 g fat.
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "add:BREAKFAST")
        app.onNodeWithTag("new-food").performClick()
        app.onNodeWithTag("field:name").performTextInput("Test oats")
        app.tap("energy:KCAL")
        app.onNodeWithTag("field:kcal").performTextInput("380")
        app.onNodeWithTag("field:protein").performTextInput("13")
        app.onNodeWithTag("field:carbs").performTextInput("60")
        app.onNodeWithTag("field:fat").performTextInput("7")
        app.tap("save-food")

        // Its amount opens straight away: 50 g is 190 kcal.
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextReplacement("50")
        app.waitFor { textIs("amount-kcal", "190 kcal") }
        app.onNodeWithTag("confirm-amount").performClick()

        app.waitFor { !gone("diary-list") }
        app.waitFor { textIs("day-kcal", "190 kcal") }
        app.waitFor { textIs("meal-kcal:BREAKFAST", "190 kcal") }
        app.waitFor { textIs("day-protein", "6.5 g") }

        // It is remembered among the user's foods.
        app.onNodeWithTag("nav:foods").performClick()
        app.waitFor { app.shownIn("foods-list", "my-food:Test oats") }
    }

    @Test
    fun aBuiltInFoodAndAQuickAddAddUp() {
        // 100 g of raw banana, from the built-in USDA list: 89 kcal.
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "add:LUNCH")
        app.onNodeWithTag("search").performTextInput("bananas raw")
        app.waitFor(30_000) { !gone("food:Bananas, raw") }
        app.onNodeWithTag("food:Bananas, raw").performClick()
        app.tap("unit:base")
        app.onNodeWithTag("field:amount").performTextReplacement("100")
        app.waitFor { textIs("amount-kcal", "89 kcal") }
        app.onNodeWithTag("confirm-amount").performClick()
        app.waitFor { textIs("day-kcal", "89 kcal") }

        // Quick add: just 300 calories in snacks.
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "add:SNACKS")
        app.onNodeWithTag("quick-add").performClick()
        app.onNodeWithTag("field:quick-kcal").performTextInput("300")
        app.onNodeWithTag("confirm-quick").performClick()
        app.waitFor { textIs("day-kcal", "389 kcal") }
        app.waitFor { textIs("meal-kcal:SNACKS", "300 kcal") }
    }

    @Test
    fun waterAddsUpAndCanBeTakenBack() {
        app.waitFor { textIs("water-total", "0 ml of 2,000 ml") }
        app.tap("add-glass")
        app.waitFor { textIs("water-total", "250 ml of 2,000 ml") }
        app.tap("add-bottle")
        app.waitFor { textIs("water-total", "750 ml of 2,000 ml") }
        app.tap("undo-water")
        app.waitFor { textIs("water-total", "250 ml of 2,000 ml") }
    }
}
