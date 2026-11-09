package io.github.randomtools3377.fitness

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Settings → Look (0.17.0): hiding a tab, opening it again from Settings, and pure black. */
@RunWith(AndroidJUnit4::class)
class LookTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aHiddenTabCanStillBeOpened() {
        app.waitFor { !gone("diary-list") }
        app.waitFor { !gone("nav:foods") }
        app.tap("open-settings")
        app.waitFor { !gone("settings") }
        app.tap("theme:BLACK")
        app.tap("tab:FOODS")
        app.waitFor { !gone("go-tab:FOODS") }
        app.tap("go-tab:FOODS")
        // The Foods tab opens, though it isn't on the bar.
        app.waitFor { gone("settings") }
        app.waitFor { gone("nav:foods") }
        app.waitFor { !gone("nav:diary") }
    }
}
