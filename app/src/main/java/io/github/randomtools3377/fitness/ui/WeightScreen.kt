package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.WeighIn
import io.github.randomtools3377.fitness.core.Weight
import kotlinx.datetime.LocalDate

@Composable
fun WeightScreen(vm: MainViewModel, modifier: Modifier) {
    val weighIns by vm.weighIns.collectAsState()
    val units by vm.units.collectAsState()
    var text by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<WeighIn?>(null) }
    val today = vm.today()

    LazyColumn(modifier.fillMaxSize().testTag("weight-list")) {
        item {
            Section("Weigh in") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(text, { text = it }, "Today's weight", "field:weight", Modifier.weight(1f), suffix = units.weight.short)
                    Button(
                        onClick = {
                            Units.parse(text)?.let { vm.addWeighIn(Units.shownToKg(it, units.weight), today) }
                            text = ""
                        },
                        enabled = Units.parse(text) != null,
                        modifier = Modifier.testTag("save-weight"),
                    ) { Text("Save") }
                }
                Weight.latest(weighIns)?.let {
                    Text("Latest: ${Units.weight(it.kg, units.weight)}, ${dayName(it.day, today)}", Modifier.testTag("latest-weight"))
                }
            }
        }
        item {
            Section("Over time") {
                if (weighIns.size < 2) {
                    Text("The chart appears after two weigh-ins.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    WeightChart(weighIns, toShown = { Units.kgToShown(it, units.weight) })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(MaterialTheme.colorScheme.outline, "Weigh-ins")
                        Legend(MaterialTheme.colorScheme.primary, "7-day average")
                    }
                    Text(
                        "Weight goes up and down from day to day with water and food. The average line shows the trend.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(weighIns.sortedWith(compareByDescending<WeighIn> { it.day }.thenByDescending { it.at }), key = { it.id }) { w ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayName(w.day, today), Modifier.weight(1f))
                Text(Units.weight(w.kg, units.weight))
                IconButton(onClick = { deleting = w }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    deleting?.let { w ->
        ConfirmDialog(
            "Delete this weigh-in?",
            "${Units.weight(w.kg, units.weight)} on ${dayName(w.day, today)}.",
            "Delete",
            onConfirm = { vm.deleteWeighIn(w); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(16.dp).height(4.dp)) { drawRect(color) }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Weigh-ins as dots and the 7-day average as a line, drawn by the app itself. */
@Composable
private fun WeightChart(weighIns: List<WeighIn>, toShown: (Double) -> Double) {
    val daily = Weight.daily(weighIns)
    val average = Weight.sevenDayAverage(weighIns)
    val dots = MaterialTheme.colorScheme.outline
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val first = daily.first().first.toEpochDays()
    val last = daily.last().first.toEpochDays()
    val values = (daily.map { it.second } + average.map { it.second }).map(toShown)
    val low = values.min()
    val high = values.max()
    val pad = ((high - low) * 0.1).coerceAtLeast(0.5)
    val lo = low - pad
    val hi = high + pad
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(180.dp).testTag("weight-chart")) {
            val spanDays = (last - first).coerceAtLeast(1)
            fun x(d: LocalDate) = (d.toEpochDays() - first).toFloat() / spanDays * size.width
            fun y(v: Double) = ((hi - toShown(v)) / (hi - lo)).toFloat() * size.height
            for (i in 0..4) {
                val gy = size.height * i / 4f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            for ((d, kg) in daily) drawCircle(dots, radius = 4.dp.toPx(), center = Offset(x(d), y(kg)))
            val path = Path()
            average.forEachIndexed { i, (d, kg) ->
                if (i == 0) path.moveTo(x(d), y(kg)) else path.lineTo(x(d), y(kg))
            }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
        }
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 6.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(Units.oneDecimal(hi), style = MaterialTheme.typography.labelSmall)
            Text(Units.oneDecimal(lo), style = MaterialTheme.typography.labelSmall)
        }
    }
}
