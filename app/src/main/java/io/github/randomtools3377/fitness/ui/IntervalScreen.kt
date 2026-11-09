package io.github.randomtools3377.fitness.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.fitness.core.IntervalPlan
import io.github.randomtools3377.fitness.core.Intervals
import io.github.randomtools3377.fitness.core.Workouts
import kotlinx.coroutines.delay

/**
 * The interval timer (0.13.0): rounds of work and rest, with a buzz at each change. The screen
 * stays on while it runs, so it keeps time without the app having to run in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalScreen(wvm: WorkoutViewModel, modifier: Modifier, back: () -> Unit) {
    val saved by wvm.intervalPlan.collectAsState()
    var work by remember(saved) { mutableStateOf(saved.workSeconds.toString()) }
    var rest by remember(saved) { mutableStateOf(saved.restSeconds.toString()) }
    var rounds by remember(saved) { mutableStateOf(saved.rounds.toString()) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf<IntervalPlan?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val view = LocalView.current

    val typed = IntervalPlan(work.toIntOrNull() ?: 0, rest.toIntOrNull() ?: 0, rounds.toIntOrNull() ?: 0)

    fun buzz(long: Boolean) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        }
        v?.vibrate(VibrationEffect.createOneShot(if (long) 700 else 250, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // The screen stays on while the timer runs.
    DisposableEffect(running) {
        view.keepScreenOn = running != null
        onDispose { view.keepScreenOn = false }
    }

    val plan = running
    val phase = plan?.let { Intervals.phaseAt(it, ((now - startedAt) / 1000).toInt()) }
    var lastKey by remember { mutableStateOf("") }
    LaunchedEffect(plan) {
        while (running != null) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    LaunchedEffect(phase?.round, phase?.work, phase?.done) {
        val p = phase ?: return@LaunchedEffect
        val key = "${p.round}:${p.work}:${p.done}"
        if (key != lastKey) {
            if (lastKey.isNotEmpty()) buzz(long = p.done || p.work)
            lastKey = key
        }
        if (p.done) running = null
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Interval timer") },
            navigationIcon = { IconButton(onClick = back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("intervals"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (plan != null && phase != null) {
                Text(if (phase.work) "Work" else "Rest", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("interval-phase"),
                    color = if (phase.work) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                Text(Workouts.clock(phase.secondsLeft), fontSize = 72.sp, modifier = Modifier.testTag("interval-left"))
                Text("Round ${phase.round} of ${plan.rounds}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { running = null; lastKey = "" }, modifier = Modifier.testTag("interval-stop")) { Text("Stop") }
            } else {
                Text("Buzzes at each change. The screen stays on while it runs.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val p = IntervalPlan.TABATA; work = "${p.workSeconds}"; rest = "${p.restSeconds}"; rounds = "${p.rounds}" }, modifier = Modifier.testTag("preset-tabata")) {
                        Text("Tabata 20/10 × 8")
                    }
                    OutlinedButton(onClick = { val p = IntervalPlan.HIIT; work = "${p.workSeconds}"; rest = "${p.restSeconds}"; rounds = "${p.rounds}" }, modifier = Modifier.testTag("preset-hiit")) {
                        Text("HIIT 40/20 × 10")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(work, { work = it }, "Work", "field:interval-work", Modifier.weight(1f), suffix = "s", decimals = false)
                    NumberField(rest, { rest = it }, "Rest", "field:interval-rest", Modifier.weight(1f), suffix = "s", decimals = false)
                    NumberField(rounds, { rounds = it }, "Rounds", "field:interval-rounds", Modifier.weight(1f), decimals = false)
                }
                val problem = typed.problem()
                Text(problem ?: "In all ${Workouts.clock(typed.totalSeconds)}", style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        wvm.setIntervalPlan(typed)
                        startedAt = System.currentTimeMillis()
                        now = startedAt
                        lastKey = ""
                        running = typed
                        buzz(long = true)
                    },
                    enabled = problem == null,
                    modifier = Modifier.fillMaxWidth().testTag("interval-start"),
                ) { Text("Start") }
                Text(
                    "To count the calories, log it afterwards with Log cardio, as \"Circuit or HIIT class\" with the minutes it took.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
