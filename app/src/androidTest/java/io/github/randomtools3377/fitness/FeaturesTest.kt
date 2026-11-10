package io.github.randomtools3377.fitness

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A new install starts simple; switching a part on in Settings → Features shows it (0.15.0). */
@RunWith(AndroidJUnit4::class)
class FeaturesTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase(allFeatures = false)).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun theSimpleStartAndSwitchingFastingOn() {
        app.waitFor { !gone("diary-list") }
        // Workouts and Progress are there; the Plan tab isn't, as planning and fasting start off.
        app.waitFor { !gone("nav:workouts") }
        app.waitFor { gone("nav:plan") }

        app.tap("open-settings")
        app.waitFor { !gone("open-features") }
        app.tap("open-features")
        app.waitFor { !gone("features-list") }
        app.tapIn("features-list", "feature:FASTING")
        app.tap("back")
        app.tap("back")
        app.waitFor { !gone("nav:plan") }
        app.onNodeWithTag("nav:plan").performClick()
        app.waitFor { !gone("fasting") }
    }

    @Test
    fun betterLabelReadingIsOffAndAsksForItsFile() {
        app.waitFor { !gone("diary-list") }
        app.tap("open-settings")
        app.waitFor { !gone("open-features") }
        app.tap("open-features")
        app.waitFor { !gone("features-list") }
        // Off to start, with no file asked for.
        app.onNodeWithTag("features-list").performScrollToNode(hasTestTag("feature:AI_LABELS"))
        app.waitFor { gone("ai-open") }
        app.tapIn("features-list", "feature:AI_LABELS")
        // Switched on, it asks for the model file, and reading labels comes on with it.
        app.onNodeWithTag("features-list").performScrollToNode(hasTestTag("ai-open"))
        app.waitFor { !gone("ai-open") }
        app.onNodeWithTag("features-list").performScrollToNode(hasTestTag("feature:LABEL_PHOTO"))
        app.onNodeWithTag("feature:LABEL_PHOTO").assertIsOn()
    }
}
