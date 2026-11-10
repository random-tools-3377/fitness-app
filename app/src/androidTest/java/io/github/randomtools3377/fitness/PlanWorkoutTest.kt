package io.github.randomtools3377.fitness

import android.Manifest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A workout planned ahead as a circuit of exercises counted in reps, then started: the numbers
 * are filled in, and the rest comes only after the round (0.20.0). A workout started by mistake
 * and left at once isn't kept.
 */
@RunWith(AndroidJUnit4::class)
class PlanWorkoutTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase())
        .around(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textIs(tag: String, text: String): Boolean = try {
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }

    private fun pick(name: String, search: String) {
        app.waitFor { !gone("field:exercise-search") }
        app.onNodeWithTag("field:exercise-search").performTextInput(search)
        app.waitFor { !gone("exercise:$name") }
        app.onNodeWithTag("exercise:$name").performClick()
    }

    @Test
    fun aCircuitPlannedAheadStartsFilledInAndRestsAfterTheRound() {
        app.onNodeWithTag("nav:workouts").performClick()

        // Started by mistake and left at once: not kept.
        app.waitFor { !gone("start-empty") }
        app.onNodeWithTag("start-empty").performClick()
        app.waitFor { !gone("add-exercise") }
        app.tap("back")
        app.waitFor { !gone("start-empty") }
        app.waitFor { gone("continue-workout") }

        // Planned ahead: jumping jacks and push-ups as a circuit.
        app.tap("plan-workout")
        app.waitFor { !gone("field:plan-name") }
        app.onNodeWithTag("field:plan-name").performTextInput("Circuit")
        app.tap("plan-add-exercise")
        pick("Jumping jacks", "jumping")
        // Three sets the same: changed all at once.
        app.waitFor { !gone("all:reps:plan:0") }
        app.tap("all:reps:plan:0")
        app.onNodeWithTag("field:value").performTextInput("20")
        app.tap("save-value")
        app.waitFor { textIs("value:reps:plan:0", "20") }
        app.tap("plan-add-exercise")
        pick("Push-up", "push-up")
        app.waitFor { !gone("plan-menu:0") }
        app.tap("plan-menu:0")
        app.tap("plan-join:0")
        app.waitFor { !gone("plan-circuit:0") }
        app.tap("save-plan")

        app.waitFor { !gone("start:Circuit") }
        app.onNodeWithTag("start:Circuit").performClick()
        app.waitFor { !gone("all:reps:0") }
        app.waitFor { textIs("value:reps:0", "20") }
        app.tap("plus:reps:1")
        app.waitFor { textIs("value:reps:1", "1") }

        // One set of each, then rest.
        app.tap("tick:0:0")
        app.waitForIdle()
        check(gone("rest-timer")) { "No rest between the exercises of a circuit" }
        app.tap("tick:1:0")
        app.waitFor { !gone("rest-timer") }
        app.tap("skip-rest")

        // A whole round at once.
        app.tap("round-done:1")
        app.waitFor { !gone("rest-timer") }
    }
}
