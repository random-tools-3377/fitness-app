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

/** After a workout where every set was done, the next one suggests a little more; a set can be a warm-up (0.9.0). */
@RunWith(AndroidJUnit4::class)
class TrainingTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase())
        .around(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textIs(tag: String, vararg texts: String): Boolean = texts.any { t ->
        try {
            app.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(t)
            true
        } catch (e: AssertionError) {
            false
        }
    }

    @Test
    fun theNextWorkoutSuggestsALittleMore() {
        app.onNodeWithTag("nav:workouts").performClick()
        app.waitFor { !gone("start-empty") }
        app.onNodeWithTag("start-empty").performClick()
        app.waitFor { !gone("add-exercise") }
        app.tap("add-exercise")
        app.onNodeWithTag("field:exercise-search").performTextInput("squat")
        app.waitFor { !gone("exercise:Squat") }
        app.onNodeWithTag("exercise:Squat").performClick()
        app.waitFor { !gone("field:weight:0:0") }
        app.onNodeWithTag("field:weight:0:0").performTextReplacement("80")
        app.onNodeWithTag("field:reps:0:0").performTextReplacement("5")
        app.tap("tick:0:0")
        app.waitFor { !gone("rest-timer") }
        app.tap("skip-rest")
        app.onNodeWithTag("finish-workout").performClick()
        app.onNodeWithTag("field:finish-name").performTextInput("Leg day")
        app.onNodeWithTag("confirm-finish").performClick()

        app.waitFor { !gone("start:Leg day") }
        app.onNodeWithTag("start:Leg day").performClick()
        app.waitFor { !gone("hint:0") }
        app.tap("use-hint:0")
        // +2.5 kg, or +5 lb where the phone uses pounds.
        app.waitFor { textIs("field:weight:0:0", "82.5", "85") }

        // Tapping the set's number makes it a warm-up.
        app.tap("warmup:0:0")
        app.waitFor { textIs("warmup:0:0", "W") }
    }
}
