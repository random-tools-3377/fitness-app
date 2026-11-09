package io.github.randomtools3377.fitness

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Food logged today starts the streak, and the stats count it. */
@RunWith(AndroidJUnit4::class)
class ProgressTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textIs(tag: String, text: String): Boolean = try {
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }

    @Test
    fun loggingFoodStartsTheStreakAndShowsInTheStats() {
        app.waitFor { !gone("diary-list") }
        app.onNodeWithTag("nav:progress").performClick()
        app.waitFor { !gone("streak-food") }
        app.waitFor { textIs("streak-food", "0") }

        app.onNodeWithTag("nav:diary").performClick()
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "add:LUNCH")
        app.onNodeWithTag("quick-add").performClick()
        app.onNodeWithTag("field:quick-kcal").performTextInput("500")
        app.onNodeWithTag("confirm-quick").performClick()
        app.waitFor { !gone("diary-list") }

        app.onNodeWithTag("nav:progress").performClick()
        app.waitFor { textIs("streak-food", "1") }
        app.waitFor { !gone("stats-average") }
        app.onNodeWithTag("stats-average").assertTextEquals("Average 500 kcal on 1 day logged")

        // The weight part is one tap away.
        app.onNodeWithTag("progress:weight").performClick()
        app.waitFor { !gone("field:weight") }
    }
}
