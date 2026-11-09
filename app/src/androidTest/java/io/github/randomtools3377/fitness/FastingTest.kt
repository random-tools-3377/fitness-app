package io.github.randomtools3377.fitness

import android.Manifest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A fast is started; logging food during it asks to end it; the ended fast is kept. */
@RunWith(AndroidJUnit4::class)
class FastingTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase())
        .around(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun loggingFoodDuringAFastAsksToEndIt() {
        app.waitFor { !gone("diary-list") }
        app.onNodeWithTag("nav:plan").performClick()
        app.onNodeWithTag("plan:fasting").performClick()
        app.waitFor { !gone("start-fast") }
        app.tap("fast-plan:16")
        app.tap("start-fast")
        app.waitFor { !gone("fast-elapsed") }
        app.onNodeWithTag("fast-elapsed").assertTextEquals("0:00")

        // Logging food while fasting asks whether to end the fast.
        app.onNodeWithTag("nav:diary").performClick()
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "add:SNACKS")
        app.onNodeWithTag("quick-add").performClick()
        app.onNodeWithTag("field:quick-kcal").performTextInput("150")
        app.onNodeWithTag("confirm-quick").performClick()
        app.waitFor { !gone("confirm-end-fast") }
        app.onNodeWithTag("confirm-end-fast").performClick()

        // Ended, and kept among past fasts.
        app.onNodeWithTag("nav:plan").performClick()
        app.onNodeWithTag("plan:fasting").performClick()
        app.waitFor { !gone("past-fast") }
        app.waitFor { !gone("start-fast") }
    }
}
