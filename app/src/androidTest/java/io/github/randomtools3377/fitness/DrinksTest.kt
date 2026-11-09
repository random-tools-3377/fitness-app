package io.github.randomtools3377.fitness

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A drink typed in with its alcohol shows standard drinks and caffeine on the day (0.11.0). */
@RunWith(AndroidJUnit4::class)
class DrinksTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun contains(tag: String, text: String): Boolean = try {
        if (!gone("diary-list")) app.shownIn("diary-list", tag)
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextContains(text, substring = true)
        true
    } catch (e: AssertionError) {
        false
    }

    @Test
    fun alcoholAndCaffeineShowOnTheDay() {
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "add:DINNER")
        app.onNodeWithTag("new-food").performClick()
        app.onNodeWithTag("field:name").performTextInput("Test stout")
        app.tap("energy:KCAL")
        app.onNodeWithTag("field:kcal").performTextInput("40")
        app.onNodeWithTag("field:alcohol").performTextInput("4")
        app.onNodeWithTag("field:caffeine").performTextInput("10")
        app.tap("save-food")
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextReplacement("350")
        app.onNodeWithTag("confirm-amount").performClick()

        // 14 g of alcohol: 1.4 standard drinks of 10 g, or 1 of 14 g where the phone is set to the US.
        app.waitFor { !gone("diary-list") }
        app.waitFor { contains("day-alcohol", "standard drinks") }
        app.waitFor { contains("day-caffeine", "35 mg") }
    }
}
