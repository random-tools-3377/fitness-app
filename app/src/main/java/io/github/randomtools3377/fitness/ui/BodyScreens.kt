package io.github.randomtools3377.fitness.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.core.CheckIn
import io.github.randomtools3377.fitness.core.DayNote
import io.github.randomtools3377.fitness.core.HeightUnit
import io.github.randomtools3377.fitness.core.MeasureKind
import io.github.randomtools3377.fitness.core.Measurement
import io.github.randomtools3377.fitness.core.Measurements
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.formatAmount
import io.github.randomtools3377.fitness.data.Photos
import io.github.randomtools3377.fitness.data.dayOf
import io.github.randomtools3377.fitness.labels.LabelOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** A photo from the camera or the gallery. Returns (take a photo, choose a photo). */
@Composable
fun rememberPhotoSource(onPicked: (Uri) -> Unit, onProblem: (String) -> Unit): Pair<() -> Unit, () -> Unit> {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending
        if (ok && uri != null) onPicked(uri)
    }
    fun start() {
        val uri = LabelOcr.newPhotoUri(context)
        pending = uri
        runCatching { take.launch(uri) }.onFailure { onProblem("No camera app was found. Choose a photo instead.") }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else onProblem("The camera wasn't allowed. Choose a photo instead.")
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPicked(uri) }
    return Pair(
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
            else ask.launch(Manifest.permission.CAMERA)
        },
        { choose.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/** A length in the units chosen for height: cm, or inches with "ft/in". */
private fun lengthUnit(u: UnitSettings) = if (u.height == HeightUnit.CM) "cm" else "in"
private fun shownLength(cm: Double, u: UnitSettings) = if (u.height == HeightUnit.CM) cm else cm / 2.54
private fun measureText(m: Measurement, u: UnitSettings): String =
    if (m.kind.percent) "${formatAmount(Math.round(m.value * 10) / 10.0)} %" else "${formatAmount(Math.round(shownLength(m.value, u) * 10) / 10.0)} ${lengthUnit(u)}"

/** The weekly check-in card (0.8.0), on the Progress tab from Monday until closed. */
@Composable
fun CheckInCard(c: CheckIn, units: UnitSettings, onTake: (Int) -> Unit, onClose: () -> Unit) {
    fun kg(x: Double) = Units.weight(kotlin.math.abs(x), units.weight)
    Section("Your week") {
        val moved = when {
            kotlin.math.abs(c.changeKg) < 0.05 -> "Your average weight held steady"
            c.changeKg < 0 -> "Your average weight went down ${kg(c.changeKg)}"
            else -> "Your average weight went up ${kg(c.changeKg)}"
        }
        val plan = when {
            c.plannedKg < 0 -> "the plan is to lose ${kg(c.plannedKg)} a week"
            c.plannedKg > 0 -> "the plan is to gain ${kg(c.plannedKg)} a week"
            else -> "the plan is to keep your weight"
        }
        Text("$moved, from ${Units.weight(c.previousAverageKg, units.weight)} to ${Units.weight(c.averageKg, units.weight)}; $plan.", modifier = Modifier.testTag("checkin-text"))
        val s = c.suggestedKcal
        if (s == null) {
            Text("Your target of ${c.currentKcal} kcal looks right. Keep going.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClose, modifier = Modifier.testTag("checkin-close")) { Text("OK") }
        } else {
            Text(
                "To match the plan, a daily target of $s kcal (now ${c.currentKcal}) would fit better. " +
                    "One week can be water or salt, so it's fine to wait for another week.",
                style = MaterialTheme.typography.bodyMedium,
            )
            c.minimumReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTake(s) }, modifier = Modifier.testTag("checkin-take")) { Text("Use $s kcal") }
                TextButton(onClick = onClose, modifier = Modifier.testTag("checkin-close")) { Text("Keep ${c.currentKcal}") }
            }
        }
        Text("An estimate from your weigh-ins, not medical advice.", style = MaterialTheme.typography.bodySmall)
    }
}

/** Progress → Body: measurements and progress photos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasurementsScreen(bvm: BodyViewModel, modifier: Modifier) {
    val all by bvm.measurements.collectAsState()
    val photos by bvm.photos.collectAsState()
    val units by bvm.units.collectAsState()
    val typed = remember { mutableStateMapOf<MeasureKind, String>() }
    var deleting by remember { mutableStateOf<Measurement?>(null) }
    var viewing by remember { mutableStateOf<io.github.randomtools3377.fitness.data.PhotoRow?>(null) }
    val today = bvm.today()
    val (takePhoto, choosePhoto) = rememberPhotoSource(onPicked = { bvm.addPhoto(it, today) }, onProblem = { bvm.message.value = it })
    val latest = Measurements.latest(all)

    LazyColumn(modifier.fillMaxSize().testTag("body-list")) {
        item {
            Section("Measure") {
                Text("Fill in any you like. Measure the same way each time, like first thing in the morning.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in MeasureKind.entries) {
                        NumberField(
                            typed[k].orEmpty(), { typed[k] = it }, k.title, "measure:${k.name}",
                            Modifier.width(150.dp),
                            suffix = if (k.percent) "%" else lengthUnit(units),
                        )
                    }
                }
                Button(
                    onClick = {
                        val values = typed.mapNotNull { (k, t) -> Units.parse(t)?.let { k to it } }.toMap()
                        bvm.addMeasurements(values, today)
                        typed.clear()
                    },
                    enabled = typed.values.any { Units.parse(it) != null },
                    modifier = Modifier.testTag("save-measurements"),
                ) { Text("Save") }
            }
        }
        if (latest.isNotEmpty()) {
            item {
                Section("Latest") {
                    for (k in MeasureKind.entries) {
                        val m = latest[k] ?: continue
                        val change = Measurements.change(all, k)?.let { d ->
                            val shown = if (k.percent) d else shownLength(d, units)
                            val unit = if (k.percent) "%" else lengthUnit(units)
                            " (" + (if (shown >= 0) "+" else "−") + formatAmount(Math.round(kotlin.math.abs(shown) * 10) / 10.0) + " $unit since the first)"
                        }.orEmpty()
                        LabelValue(k.title, measureText(m, units) + change, tag = "latest:${k.name}")
                    }
                    if (MeasureKind.BODY_FAT in latest) {
                        Text("Body fat from a scale or calipers is a rough guide; the trend matters more than one number.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Section("Progress photos") {
                Text(
                    "Kept only inside the app on this phone: not in the gallery, not in exports or backups. " +
                        "Lock the Progress tab in Settings → Privacy to keep them behind your phone's lock.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = takePhoto, modifier = Modifier.testTag("photo-take")) { Text("Take a photo") }
                    OutlinedButton(onClick = choosePhoto, modifier = Modifier.testTag("photo-choose")) { Text("Choose a photo") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in photos) {
                        Column(Modifier.clickable { viewing = p }.testTag("photo:${p.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                            PhotoImage(p.file, 96, Modifier.size(96.dp))
                            Text(dayName(dayOf(p.day), today), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (all.isNotEmpty()) {
            item { Text("All measurements", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp)) }
            items(all.reversed(), key = { "m:${it.id}" }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { deleting = m }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${dayName(m.day, today)} · ${m.kind.title}", Modifier.weight(1f))
                    Text(measureText(m, units))
                }
                HorizontalDivider()
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    deleting?.let { m ->
        ConfirmDialog(
            "Delete this measurement?",
            "${m.kind.title}, ${measureText(m, units)}, ${dayName(m.day, today)}.",
            "Delete",
            onConfirm = { bvm.deleteMeasurement(m.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    viewing?.let { p -> PhotoDialog(bvm, p, onDone = { viewing = null }) }
}

@Composable
private fun PhotoImage(file: String, maxSide: Int, modifier: Modifier) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeSampled(Photos.file(context, file).path, (maxSide * density).toInt()) }
    }
    val b = bitmap
    if (b == null) Box(modifier) else Image(b.asImageBitmap(), contentDescription = "Progress photo", modifier = modifier, contentScale = ContentScale.Crop)
}

private fun decodeSampled(path: String, maxSide: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@Composable
private fun PhotoDialog(bvm: BodyViewModel, p: io.github.randomtools3377.fitness.data.PhotoRow, onDone: () -> Unit) {
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) bvm.savePhotoCopy(p.id, uri)
    }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(dayName(dayOf(p.day), bvm.today())) },
        text = { PhotoImage(p.file, 600, Modifier.fillMaxWidth().height(420.dp)) },
        confirmButton = { TextButton(onClick = onDone) { Text("Close") } },
        dismissButton = {
            Row {
                TextButton(onClick = { save.launch("progress-${dayOf(p.day)}.jpg") }, modifier = Modifier.testTag("photo-save")) { Text("Save a copy") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("photo-delete")) { Text("Delete") }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this photo?",
            "It is removed from the app for good. A copy you saved elsewhere stays.",
            "Delete",
            onConfirm = { bvm.deletePhoto(p.id); confirmDelete = false; onDone() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Progress → Habits: the user's own habits with their streaks, and sleep and mood next to calories. */
@Composable
fun HabitsScreen(bvm: BodyViewModel, kcalByDay: Map<LocalDate, Double>, modifier: Modifier) {
    val habits by bvm.habits.collectAsState()
    val streaks by bvm.habitStreaks.collectAsState()
    val notes by bvm.notes.collectAsState()
    val units by bvm.units.collectAsState()
    var name by remember { mutableStateOf("") }
    val today = bvm.today()
    LazyColumn(modifier.fillMaxSize().testTag("habits-list")) {
        item {
            Section("Your habits") {
                Text("Tick them each day in the diary. Each one gets a streak, like the built-in ones.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { TextInput(name, { name = it }, "New habit, like \"Stretch 10 minutes\"", "field:habit") }
                    Button(onClick = { bvm.addHabit(name); name = "" }, enabled = name.isNotBlank(), modifier = Modifier.testTag("add-habit")) { Text("Add") }
                }
                for (h in habits.filter { !it.archived }) {
                    val s = streaks[h.id]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("habit:${h.name}")) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name)
                            Text(
                                if (s == null || s.current == 0) "No streak yet" + (s?.best?.takeIf { it > 0 }?.let { " · best $it days" } ?: "")
                                else "${s.current} ${if (s.current == 1) "day" else "days"} in a row · best ${s.best}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("habit-streak:${h.name}"),
                            )
                        }
                        TextButton(onClick = { bvm.archiveHabit(h, true) }) { Text("Stop") }
                    }
                }
                val stopped = habits.filter { it.archived }
                if (stopped.isNotEmpty()) {
                    Text("Stopped", style = MaterialTheme.typography.labelLarge)
                    for (h in stopped) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { bvm.archiveHabit(h, false) }) { Text("Start again") }
                        }
                    }
                }
            }
        }
        item {
            Section("Sleep and mood, the last 14 days") {
                Text("Next to what you ate, to spot patterns. Fill them in on the diary.", style = MaterialTheme.typography.bodySmall)
                Row {
                    Text("Day", Modifier.weight(1.3f), style = MaterialTheme.typography.labelLarge)
                    Text("Eaten", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("Sleep", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                    Text("Mood", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                }
                val byDay = notes.associateBy { it.day }
                for (i in 0 until 14) {
                    val d = today.minus(DatePeriod(days = i))
                    val n = byDay[d]
                    Row(Modifier.testTag("sleep-row:$d")) {
                        Text(dayName(d, today), Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                        Text(kcalByDay[d]?.let { energy(it, units) } ?: "–", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(n?.sleepHours?.let { "${formatAmount(it)} h" } ?: "–", Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                        Text(n?.mood?.let { DayNote.MOODS[it - 1] } ?: "–", Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** On the diary: ticking the user's habits for the day shown. */
@Composable
fun HabitsCard(bvm: BodyViewModel, day: LocalDate) {
    val habits by bvm.habits.collectAsState()
    val ticks by bvm.ticks.collectAsState()
    val active = habits.filter { !it.archived }
    if (active.isEmpty()) return
    Section("Habits") {
        for (h in active) {
            val done = ticks.any { it.habitId == h.id && it.day == day }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { bvm.tick(h, day, !done) }) {
                Checkbox(checked = done, onCheckedChange = { bvm.tick(h, day, it) }, modifier = Modifier.testTag("tick:${h.name}"))
                Text(h.name)
            }
        }
    }
}

/** On the diary: hours slept and mood for the day shown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepMoodCard(bvm: BodyViewModel, day: LocalDate) {
    val notes by bvm.notes.collectAsState()
    val note = notes.firstOrNull { it.day == day } ?: DayNote(day)
    var sleep by remember(day, note.sleepHours) { mutableStateOf(note.sleepHours?.let { formatAmount(it) }.orEmpty()) }
    Section("Sleep and mood") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(sleep, { sleep = it }, "Hours slept", "field:sleep", Modifier.weight(1f), suffix = "h")
            TextButton(
                onClick = { bvm.saveNote(note.copy(sleepHours = Units.parse(sleep))) },
                enabled = Units.parse(sleep) != note.sleepHours,
                modifier = Modifier.testTag("save-sleep"),
            ) { Text("Save") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayNote.MOODS.forEachIndexed { i, label ->
                val m = i + 1
                FilterChip(
                    selected = note.mood == m,
                    onClick = { bvm.saveNote(note.copy(mood = if (note.mood == m) null else m)) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("mood:$m"),
                )
            }
        }
    }
}
