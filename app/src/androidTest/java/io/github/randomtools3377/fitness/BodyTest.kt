package io.github.randomtools3377.fitness

import androidx.compose.ui.test.assertTextContains
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

/** A habit is added, ticked in the diary and gets a streak; a waist measurement is saved (0.8.0). */
@RunWith(AndroidJUnit4::class)
class BodyTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun contains(tag: String, text: String): Boolean = try {
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextContains(text, substring = true)
        true
    } catch (e: AssertionError) {
        false
    }

    @Test
    fun aHabitTickedTodayStartsItsStreak() {
        app.waitFor { !gone("diary-list") }
        app.onNodeWithTag("nav:progress").performClick()
        app.onNodeWithTag("progress:habits").performClick()
        app.waitFor { !gone("field:habit") }
        app.onNodeWithTag("field:habit").performTextInput("Test stretch")
        app.onNodeWithTag("add-habit").performClick()
        app.waitFor { !gone("habit-streak:Test stretch") }
        app.waitFor { contains("habit-streak:Test stretch", "No streak yet") }

        app.onNodeWithTag("nav:diary").performClick()
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "tick:Test stretch")

        app.onNodeWithTag("nav:progress").performClick()
        app.onNodeWithTag("progress:habits").performClick()
        app.waitFor { contains("habit-streak:Test stretch", "1 day in a row") }
    }

    @Test
    fun aMeasurementIsSavedAndShown() {
        app.waitFor { !gone("diary-list") }
        app.onNodeWithTag("nav:progress").performClick()
        app.onNodeWithTag("progress:body").performClick()
        app.waitFor { !gone("measure:WAIST") }
        app.onNodeWithTag("measure:BODY_FAT").performTextInput("25")
        app.tap("save-measurements")
        app.waitFor { !gone("latest:BODY_FAT") }
        app.waitFor { contains("latest:BODY_FAT", "25 %") }
    }

    @Test
    fun aSupplementIsAddedAndTickedInTheDiary() {
        app.waitFor { !gone("diary-list") }
        app.onNodeWithTag("nav:progress").performClick()
        app.onNodeWithTag("progress:habits").performClick()
        app.waitFor { !gone("habits-list") }
        app.tapIn("habits-list", "field:supplement")
        app.onNodeWithTag("field:supplement").performTextInput("Test vitamin")
        app.tap("add-supplement")
        app.waitFor { app.shownIn("habits-list", "supplement:Test vitamin") }

        app.onNodeWithTag("nav:diary").performClick()
        app.waitFor { !gone("diary-list") }
        app.tapIn("diary-list", "tick:Test vitamin")
    }
}
