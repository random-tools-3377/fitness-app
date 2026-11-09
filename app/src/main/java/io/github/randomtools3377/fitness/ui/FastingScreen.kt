package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.Fast
import io.github.randomtools3377.fitness.core.FastPlan
import io.github.randomtools3377.fitness.core.Fasting
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.formatAmount
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun clockOf(ms: Long): String =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).let { clockText(it.hour * 60 + it.minute) }

/** The fasting timer: plans of 16:8, 18:6, 20:4 and 23:1, or your own hours. */
@Composable
fun FastingPart(vm: MainViewModel) {
    val active by vm.activeFast.collectAsState()
    val fasts by vm.fasts.collectAsState()
    val settings by vm.settings.collectAsState()
    var hours by remember { mutableStateOf(settings.fastHours) }
    var own by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("fasting")) {
        val f = active
        Section {
            if (f == null) {
                Text("Choose a plan: hours fasting, out of 24.", style = MaterialTheme.typography.bodySmall)
                Choices(FastPlan.PRESETS.map { it.toDouble() }, hours, { FastPlan(it).title }, { "fast-plan:${it.toInt()}" }) { hours = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(own, { own = it; Units.parse(it)?.let { h -> hours = h } }, "Or your own hours", "field:fast-hours", Modifier.weight(1f), suffix = "h")
                }
                Button(onClick = { vm.startFast(hours) }, modifier = Modifier.fillMaxWidth().testTag("start-fast")) {
                    Text("Start a ${FastPlan(hours).title} fast now")
                }
            } else {
                FastRing(f, now)
                val left = f.leftMs(now)
                Text(
                    if (left > 0) "${Fasting.hoursMinutes(left)} to go · ends at ${clockOf(f.endsAt())}" else "Done! You can end it when you eat.",
                    modifier = Modifier.testTag("fast-left"),
                )
                OutlinedButton(onClick = { vm.endFast() }, modifier = Modifier.fillMaxWidth().testTag("end-fast")) { Text("End the fast") }
            }
            Text("A notification says when the fast is done. The app gives no medical advice about fasting.", style = MaterialTheme.typography.bodySmall)
        }
        val past = fasts.filter { it.endedAt != null }.sortedByDescending { it.startedAt }
        if (past.isNotEmpty()) {
            Section("Past fasts") {
                for (p in past.take(30)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("past-fast")) {
                        Column(Modifier.weight(1f)) {
                            Text("${dayName(dayOfMillis(p.startedAt), vm.today())}, ${clockOf(p.startedAt)}")
                            Text(
                                "${Fasting.hoursMinutes(p.elapsedMs(0))} of ${formatAmount(p.plannedHours)} h" + if (p.finished) " · finished" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { vm.deleteFast(p) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FastRing(f: Fast, now: Long) {
    val done = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = (f.elapsedMs(now).toFloat() / f.plannedMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val w = 16.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(done, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Fasted", style = MaterialTheme.typography.bodySmall)
            Text(Fasting.hoursMinutes(f.elapsedMs(now)), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("fast-elapsed"))
            Text("of ${FastPlan(f.plannedHours).title}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
