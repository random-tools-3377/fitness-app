package io.github.randomtools3377.fitness

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A recipe is made, planned for tomorrow, and broken into its foods on the shopping list. */
@RunWith(AndroidJUnit4::class)
class PlanTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aRecipeIsPlannedAndGoesOnTheShoppingList() {
        app.waitFor { !gone("diary-list") }
        app.onNodeWithTag("nav:plan").performClick()
        app.onNodeWithTag("plan:recipes").performClick()
        app.waitFor { !gone("new-recipe") }
        app.onNodeWithTag("new-recipe").performClick()
        app.onNodeWithTag("field:recipe-name").performTextInput("Test porridge")
        app.onNodeWithTag("field:servings").performTextReplacement("2")

        // 100 g of a food typed in from its label, 380 kcal per 100 g.
        app.tap("recipe-add-food")
        app.onNodeWithTag("new-food").performClick()
        app.onNodeWithTag("field:name").performTextInput("Test oats")
        app.tap("energy:KCAL")
        app.onNodeWithTag("field:kcal").performTextInput("380")
        app.tap("save-food")
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextReplacement("100")
        app.onNodeWithTag("confirm-amount").performClick()

        app.waitFor { !gone("recipe-per-serving") }
        app.onNodeWithTag("recipe-per-serving").assertTextContains("A serving: 190 kcal", substring = true)
        app.onNodeWithTag("save-recipe").performClick()
        app.waitFor { !gone("recipe:Test porridge") }

        // Plan one serving for tomorrow's lunch.
        val tomorrow = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(DatePeriod(days = 1))
        app.onNodeWithTag("plan:meals").performClick()
        app.waitFor { !gone("plan-list") }
        app.tapIn("plan-list", "plan-add:$tomorrow:LUNCH")
        app.onNodeWithTag("search").performTextInput("porridge")
        app.waitFor { !gone("food:Test porridge") }
        app.onNodeWithTag("food:Test porridge").performClick()
        app.waitFor { !gone("confirm-amount") }
        app.onNodeWithTag("confirm-amount").performClick() // 1 serving, the recipe's own portion
        app.waitFor { app.shownIn("plan-list", "planned:$tomorrow") }
        app.onNodeWithTag("planned:$tomorrow").assertTextContains("Planned 190 kcal", substring = true)

        // The shopping list breaks the recipe into its food: half of 100 g.
        app.onNodeWithTag("plan:shopping").performClick()
        app.waitFor { !gone("make-list") }
        app.onNodeWithTag("make-list").performClick()
        app.waitFor { !gone("shop:Test oats") }
        app.onNodeWithTag("shop:Test oats").assertTextContains("50 g", substring = true)
    }
}
