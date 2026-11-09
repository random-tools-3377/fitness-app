package io.github.randomtools3377.fitness.ui

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.core.AllStreaks
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Stats
import io.github.randomtools3377.fitness.core.StatsPeriod
import io.github.randomtools3377.fitness.core.Streak
import io.github.randomtools3377.fitness.core.Streaks
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.WaterEntry
import io.github.randomtools3377.fitness.core.WeighIn
import io.github.randomtools3377.fitness.core.Weight
import io.github.randomtools3377.fitness.core.Workout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the stats and streaks need, read from the whole diary. */
class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitnessApplication).container.repository

    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val entries: StateFlow<List<DiaryEntry>> = repo.allEntries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val water: StateFlow<List<WaterEntry>> = repo.allWater.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workouts: StateFlow<List<Workout>> = repo.workouts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val weighIns: StateFlow<List<WeighIn>> = repo.weighIns.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val units: StateFlow<UnitSettings> = settings.map { it.units ?: UnitSettings.forRegion(Locale.getDefault().country) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSettings.forRegion(Locale.getDefault().country))

    /** The four streaks, and finished fasts. */
    val streaks: StateFlow<Pair<AllStreaks, Streak>?> = combine(entries, water, workouts, settings) { e, w, wo, s ->
        val eaten = e.filter { !it.planned }
        val all = Streaks.all(
            dayKcal = eaten.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.nutrients.kcal } },
            targetKcal = s.targetKcal,
            waterByDay = w.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.ml } },
            waterTargetMl = s.water.targetMl,
            workoutDays = wo.filter { it.finished }.map { it.day },
            workoutsPerWeek = s.workoutsPerWeek,
            today = today(),
        )
        all to Streak(0, 0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun StreaksCard(s: AllStreaks, fasts: Streak?, perWeek: Int) {
    Section("Streaks") {
        StreakRow(s.foodLogged, "Days with food logged", "streak-food")
        StreakRow(s.onTarget, "Days on target", "streak-target", "Within 10% of the calorie target")
        StreakRow(s.workoutWeeks, "Weeks with $perWeek workouts", "streak-workouts", "Rest days don't break it")
        StreakRow(s.water, "Days the water target was reached", "streak-water")
        if (fasts != null && fasts.best > 0) StreakRow(fasts, "Days a planned fast was finished", "streak-fasts")
    }
}

@Composable
private fun StreakRow(s: Streak, title: String, tag: String, note: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "${s.current}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(56.dp).testTag(tag),
        )
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(listOfNotNull("Best: ${s.best}", note).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The Stats tab: streaks, then week, month or year of eating, weight and training. */
@Composable
fun StatsScreen(svm: StatsViewModel, modifier: Modifier) {
    val entries by svm.entries.collectAsState()
    val water by svm.water.collectAsState()
    val workouts by svm.workouts.collectAsState()
    val weighIns by svm.weighIns.collectAsState()
    val settings by svm.settings.collectAsState()
    val units by svm.units.collectAsState()
    val streaks by svm.streaks.collectAsState()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }
    val today = svm.today()
    val eaten = entries.filter { !it.planned }
    val p = Stats.period(period, today, eaten, settings.targetKcal, water, workouts)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("stats")) {
        Text("Stats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        streaks?.let { (s, f) -> StreaksCard(s, f, settings.workoutsPerWeek) }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Choices(StatsPeriod.entries, period, { it.title }, { "period:${it.name}" }) { period = it }
        }

        Section("Calories") {
            val days = generateSequence(p.from) { if (it < p.to) LocalDate.fromEpochDays(it.toEpochDays() + 1) else null }.toList()
            BarChart(days.map { p.byDay[it]?.kcal ?: 0.0 }, settings.targetKcal?.toDouble())
            Text(
                "Average ${energy(p.average.kcal, units)} on ${p.daysLogged} ${if (p.daysLogged == 1) "day" else "days"} logged" +
                    (settings.targetKcal?.let { " · ${p.daysOnTarget} on target" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("stats-average"),
            )
            Text("Average ${macroLine(p.average)}", style = MaterialTheme.typography.bodySmall)
        }

        if (p.topFoods.isNotEmpty()) {
            Section("Most calories") {
                for ((name, kcal) in p.topFoods) LabelValue(name, energy(kcal, units))
            }
        }

        Section("Weight") {
            val latest = Weight.latest(weighIns)
            if (latest == null) {
                Text("Weigh in to see the trend.", style = MaterialTheme.typography.bodySmall)
            } else {
                val trend = Stats.weightTrend(weighIns, today)
                LabelValue("Now", Units.weight(latest.kg, units.weight))
                trend?.let { LabelValue("Trend", "${if (it > 0) "+" else ""}${Units.weight(it * 7, units.weight)} a week") }
                val goal = settings.profile?.goalWeightKg
                if (goal != null) {
                    LabelValue("Goal", Units.weight(goal, units.weight))
                    val date = Stats.goalDate(latest.kg, goal, trend, today)
                    Text(
                        if (date != null) "At this trend, about ${dayName(date, today)} (an estimate)." else "The trend isn't heading toward the goal yet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("goal-date"),
                    )
                } else {
                    Text("Set a goal weight with your calorie target to see when you'd reach it.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Section("Training") {
            val weeks = Stats.trainingWeeks(workouts, 8, today)
            Text("Workouts a week", style = MaterialTheme.typography.labelLarge)
            BarChart(weeks.map { it.workouts.toDouble() }, settings.workoutsPerWeek.toDouble())
            Text("Total lifted a week (sets × reps × weight)", style = MaterialTheme.typography.labelLarge)
            BarChart(weeks.map { Units.kgToShown(it.liftedKg, units.weight) }, null)
            LabelValue("This ${period.title.lowercase()}", "${p.workouts} workouts" + if (p.burnedKcal > 0) " · ${energy(p.burnedKcal, units)} burned" else "")
            val inPeriod = workouts.filter { it.day in p.from..p.to }
            val byMuscle = Stats.liftedByMuscle(inPeriod)
            if (byMuscle.isNotEmpty()) {
                Text("Lifted by muscle group", style = MaterialTheme.typography.labelLarge)
                for ((m, kg) in byMuscle) LabelValue(m, "${Units.grouped(Units.kgToShown(kg, units.weight).roundToInt())} ${units.weight.short}")
            }
            val byExercise = Stats.liftedByExercise(inPeriod).take(6)
            if (byExercise.isNotEmpty()) {
                Text("Lifted by exercise", style = MaterialTheme.typography.labelLarge)
                for ((e, kg) in byExercise) LabelValue(e, "${Units.grouped(Units.kgToShown(kg, units.weight).roundToInt())} ${units.weight.short}")
            }
        }

        Section("Water") {
            LabelValue("Average a day", Units.volume(p.averageWaterMl.toDouble(), units.volume))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Bars, drawn by the app itself, with an optional dashed target line. */
@Composable
fun BarChart(values: List<Double>, target: Double?) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.tertiary
    val line = MaterialTheme.colorScheme.secondary
    val top = maxOf(values.maxOrNull() ?: 0.0, target ?: 0.0).coerceAtLeast(1.0) * 1.1
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 4.dp)) {
        val n = values.size.coerceAtLeast(1)
        val gap = if (n > 40) 1f else 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = (v / top).toFloat() * size.height
            val color = if (target != null && v > target * 1.1) over else bar
            drawRect(color, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h))
        }
        if (target != null) {
            val y = size.height - (target / top).toFloat() * size.height
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}
