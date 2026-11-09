package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.Calendar
import io.github.randomtools3377.fitness.core.DayMark
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.YearReviews
import io.github.randomtools3377.fitness.core.formatAmount
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** Progress → Calendar (0.14.0): a month, each day coloured by the calorie target, with workouts marked. */
@Composable
fun CalendarScreen(svm: StatsViewModel, modifier: Modifier) {
    val entries by svm.entries.collectAsState()
    val workouts by svm.workouts.collectAsState()
    val settings by svm.settings.collectAsState()
    val chart = LocalChartColors.current
    val today = svm.today()
    var month by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }
    val dayKcal = remember(entries) { entries.filter { !it.planned }.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.nutrients.kcal } } }
    val workoutDays = remember(workouts) { workouts.filter { it.finished }.map { it.day }.toSet() }
    val targetFor = remember(settings, workouts) { svm.targetFor(settings, workouts) }
    val days = Calendar.month(month, dayKcal, workoutDays, targetFor).associateBy { it.day }

    fun color(m: DayMark): Color = when (m) {
        DayMark.ON_TARGET -> chart.good
        DayMark.OVER -> chart.over
        DayMark.UNDER -> chart.under
        DayMark.LOGGED -> chart.dots
        DayMark.NOTHING -> Color.Transparent
    }

    LazyColumn(modifier.fillMaxSize().testTag("calendar")) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }, modifier = Modifier.testTag("month-before")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Month before")
                }
                Text("${MONTHS[month.monthNumber - 1]} ${month.year}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("month"), textAlign = TextAlign.Center)
                IconButton(onClick = { month = month.plus(DatePeriod(months = 1)) }, modifier = Modifier.testTag("month-after")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Month after")
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row {
                    for (n in listOf("M", "T", "W", "T", "F", "S", "S")) Text(n, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                }
                for (week in Calendar.weeks(month)) {
                    Row {
                        for (d in week) {
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (d != null) {
                                    val info = days[d]
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(color(info?.mark ?: DayMark.NOTHING).copy(alpha = 0.55f))
                                            .testTag("cal:$d"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${d.dayOfMonth}",
                                            style = if (d == today) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                                        )
                                        if (info?.workout == true) {
                                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (settings.targetKcal != null) {
                    Key(chart.good, "Within 10% of the day's target")
                    Key(chart.under, "Under")
                    Key(chart.over, "Over")
                } else {
                    Key(chart.dots, "Food logged (set a target to see on or off target)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    Text("  A workout", style = MaterialTheme.typography.bodySmall)
                }
                val inMonth = days.values.filter { it.kcal > 0 }
                Text(
                    "${inMonth.size} days logged" +
                        (if (settings.targetKcal != null) ", ${inMonth.count { it.mark == DayMark.ON_TARGET }} on target" else "") +
                        ", ${days.values.count { it.workout }} with a workout",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("month-summary"),
                )
            }
        }
    }
}

@Composable
private fun Key(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.55f)))
        Text("  $text", style = MaterialTheme.typography.bodySmall)
    }
}

/** Progress → Year (0.14.0): the year's totals and bests, for this year and the ones before. */
@Composable
fun YearReviewScreen(svm: StatsViewModel, modifier: Modifier) {
    val entries by svm.entries.collectAsState()
    val workouts by svm.workouts.collectAsState()
    val weighIns by svm.weighIns.collectAsState()
    val settings by svm.settings.collectAsState()
    val units by svm.units.collectAsState()
    val today = svm.today()
    val years = remember(entries, workouts, weighIns) { YearReviews.years(entries, workouts, weighIns, today.year) }
    var year by remember { mutableStateOf(today.year) }
    val r = remember(year, entries, workouts, weighIns, settings) { YearReviews.of(year, entries, workouts, weighIns, svm.targetFor(settings, workouts)) }

    LazyColumn(modifier.fillMaxSize().testTag("year-review")) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Choices(years, year, { "$it" }, { "year:$it" }) { year = it }
            }
        }
        if (r.empty) {
            item { Text("Nothing logged in $year yet.", Modifier.padding(16.dp)) }
        } else {
            item {
                Section(if (year == today.year) "$year so far" else "$year") {
                    LabelValue("Days with food logged", "${r.daysLogged}", tag = "year-days")
                    r.averageKcal?.let { LabelValue("Average on those days", energy(it.toDouble(), units)) }
                    if (settings.targetKcal != null) LabelValue("Days on target", "${r.daysOnTarget}")
                    LabelValue("Longest run of days logged", "${r.longestFoodStreak}")
                }
            }
            item {
                Section("Training") {
                    LabelValue("Workouts", "${r.workouts}", tag = "year-workouts")
                    if (r.totalLiftedKg > 0) LabelValue("Total lifted", Units.weight(r.totalLiftedKg, units.weight))
                    if (r.cardioMinutes > 0) LabelValue("Cardio", "${r.cardioMinutes} min" + if (r.cardioKm > 0) " · " + Units.distance(r.cardioKm, units.distance) else "")
                    if (r.bestsSet > 0) LabelValue("Personal bests set", "${r.bestsSet}")
                }
            }
            r.weightChangeKg?.let { change ->
                item {
                    Section("Weight") {
                        LabelValue("From", Units.weight(r.weightStartKg!!, units.weight))
                        LabelValue("To", Units.weight(r.weightEndKg!!, units.weight))
                        LabelValue("Change", (if (change > 0) "+" else if (change < 0) "−" else "") + Units.weight(kotlin.math.abs(change), units.weight))
                    }
                }
            }
            if (r.topFoods.isNotEmpty()) {
                item {
                    Section("Where the calories came from") {
                        for ((name, kcal) in r.topFoods) LabelValue(name, energy(kcal.toDouble(), units))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
