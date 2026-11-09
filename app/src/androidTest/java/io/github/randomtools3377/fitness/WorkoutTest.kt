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
import io.github.randomtools3377.fitness.core.WeighIn
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Workouts on a real (emulated) phone: a set ticked, cardio typed in, calories added to the day. */
@RunWith(AndroidJUnit4::class)
class WorkoutTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    @Before
    fun weighIn() {
        // Calories burned need a body weight: 70 kg.
        val fitness = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FitnessApplication
        runBlocking {
            fitness.container.repository.addWeighIn(WeighIn(day = Clock.System.todayIn(TimeZone.currentSystemDefault()), kg = 70.0, at = 1))
        }
    }

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textIs(tag: String, text: String): Boolean = try {
        if (!gone("diary-list")) app.shownIn("diary-list", tag)
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }

    @Test
    fun cardioWithADistanceAddsItsCaloriesToTheDay() {
        // 5 km in 30 minutes is 10 km/h: about 10.06 MET × 70 kg × 0.5 h = 352 kcal.
        app.onNodeWithTag("nav:workouts").performClick()
        app.waitFor { !gone("log-cardio") }
        app.onNodeWithTag("log-cardio").performClick()
        app.tap("cardio:Running")
        app.onNodeWithTag("field:cardio-minutes").performTextInput("30")
        app.onNodeWithTag("field:cardio-distance").performTextInput("5")
        app.onNodeWithTag("confirm-cardio").performClick()
        app.waitFor { app.shownIn("workouts-list", "history:Running") }

        app.onNodeWithTag("nav:diary").performClick()
        app.waitFor { textIs("day-burned", "352 kcal") }
        app.waitFor { !gone("day-workout:Running") }
    }

    @Test
    fun aWorkoutWithATickedSetIsSavedForNextTime() {
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
        // Ticking starts the rest timer.
        app.waitFor { !gone("rest-timer") }
        app.tap("skip-rest")

        app.onNodeWithTag("finish-workout").performClick()
        app.onNodeWithTag("field:finish-name").performTextInput("Leg day")
        app.onNodeWithTag("confirm-finish").performClick()

        // Saved, and next time it fills in from this one.
        app.waitFor { !gone("start:Leg day") }
        app.onNodeWithTag("start:Leg day").performClick()
        app.waitFor { !gone("field:weight:0:0") }
        app.onNodeWithTag("field:weight:0:0").assertTextEquals("80")
        app.onNodeWithTag("field:reps:0:0").assertTextEquals("5")
    }
}
