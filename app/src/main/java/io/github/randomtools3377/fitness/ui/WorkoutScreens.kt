package io.github.randomtools3377.fitness.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.core.Best
import io.github.randomtools3377.fitness.core.Exercise
import io.github.randomtools3377.fitness.core.ExerciseKind
import io.github.randomtools3377.fitness.core.Exercises
import io.github.randomtools3377.fitness.core.SavedWorkout
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.Track
import io.github.randomtools3377.fitness.core.Workout
import io.github.randomtools3377.fitness.core.WorkoutSet
import io.github.randomtools3377.fitness.core.Workouts
import io.github.randomtools3377.fitness.core.formatAmount
import io.github.randomtools3377.fitness.workout.RestTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The Workouts tab: the workout in progress, saved workouts, cardio, history and bests. */
@Composable
fun WorkoutsScreen(wvm: WorkoutViewModel, vm: MainViewModel, modifier: Modifier, open: (Route) -> Unit) {
    val active by wvm.active.collectAsState()
    val saved by wvm.saved.collectAsState()
    val history by wvm.history.collectAsState()
    val units by wvm.units.collectAsState()
    val exercises by wvm.exercises.collectAsState()
    var cardio by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Workout?>(null) }
    var planning by remember { mutableStateOf(false) }
    val extras = on(io.github.randomtools3377.fitness.core.Feature.TRAINING_EXTRAS)
    val programme by wvm.programme.collectAsState()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val context = LocalContext.current
    fun beforeStart() {
        // The rest timer tells you when rest is over, even with the app in the background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(modifier.fillMaxSize().testTag("workouts-list")) {
        item {
            Text("Workouts", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        }
        active?.let { w ->
            item {
                Section("In progress") {
                    Text("${w.name} · started ${((System.currentTimeMillis() - w.startedAt) / 60_000).coerceAtLeast(0)} min ago")
                    Button(onClick = { open(Route.ActiveWorkout) }, modifier = Modifier.testTag("continue-workout")) { Text("Continue") }
                }
            }
        }
        if (active == null) {
            // Today's workout from the weekly programme (0.9.0).
            val todays = io.github.randomtools3377.fitness.core.Programme.today(programme, saved, wvm.todayDate())
            if (extras) item {
                Section("Weekly programme") {
                    if (programme.isEmpty()) {
                        Text("Plan which saved workout to do on which days, like Push on Monday and Legs on Friday.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(io.github.randomtools3377.fitness.core.Programme.describe(programme, saved), style = MaterialTheme.typography.bodySmall)
                        if (todays != null) {
                            Button(onClick = { beforeStart(); wvm.startSaved(todays); open(Route.ActiveWorkout) }, modifier = Modifier.testTag("start-today")) {
                                Text("Today: start ${todays.name}")
                            }
                        } else {
                            Text("Nothing planned today: a rest day.", modifier = Modifier.testTag("rest-day"))
                        }
                    }
                    TextButton(onClick = { planning = true }, enabled = saved.isNotEmpty(), modifier = Modifier.testTag("edit-programme")) {
                        Text(if (saved.isEmpty()) "Save a workout first to plan a week" else if (programme.isEmpty()) "Plan my week" else "Change the plan")
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { beforeStart(); wvm.startEmpty(); open(Route.ActiveWorkout) }, modifier = Modifier.testTag("start-empty")) {
                        Text("Start a workout")
                    }
                    OutlinedButton(onClick = { cardio = true }, modifier = Modifier.testTag("log-cardio")) { Text("Log cardio") }
                }
            }
            item {
                TextButton(onClick = { open(Route.PlanWorkout()) }, modifier = Modifier.padding(horizontal = 8.dp).testTag("plan-workout")) {
                    Text("+ Plan a workout ahead")
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { open(Route.Bests) }, modifier = Modifier.testTag("open-bests")) { Text("Personal bests") }
                if (extras) TextButton(onClick = { open(Route.Intervals) }, modifier = Modifier.testTag("open-intervals")) { Text("Interval timer") }
            }
        }
        item { Text("Saved workouts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
        if (saved.isEmpty()) {
            item {
                Text(
                    "None yet. Plan one ahead with \"Plan a workout ahead\", or give a workout a name when you finish it, like \"Leg day\". Next time it fills in each exercise from last time.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(saved, key = { "s${it.id}" }) { sw ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = active == null) { beforeStart(); wvm.startSaved(sw); open(Route.ActiveWorkout) }
                    .padding(horizontal = 16.dp, vertical = 8.dp).testTag("start:${sw.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sw.name, style = MaterialTheme.typography.bodyLarge)
                    Text(sw.items.joinToString(" · ") { "${it.exercise.name} ${it.sets}×" }, style = MaterialTheme.typography.bodySmall)
                }
                var confirm by remember { mutableStateOf(false) }
                IconButton(onClick = { open(Route.PlanWorkout(sw.id)) }, modifier = Modifier.testTag("edit:${sw.name}")) { Icon(Icons.Filled.Edit, contentDescription = "Change") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                if (confirm) {
                    ConfirmDialog("Delete ${sw.name}?", "Workouts already done stay in the history.", "Delete", { wvm.deleteSaved(sw); confirm = false }, { confirm = false })
                }
            }
            HorizontalDivider()
        }
        item { Text("Lately", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp)) }
        if (history.isEmpty()) {
            item { Text("Finished workouts show here.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
        items(history.take(50), key = { "h${it.id}" }) { w ->
            Row(
                Modifier.fillMaxWidth().clickable { viewing = w }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("history:${w.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${dayName(w.day, vm.today())} · ${summary(w, units)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(burnedText(w.burnedKcal, units), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (cardio) {
        LogCardioDialog(
            exercises = exercises.filter { it.kind == ExerciseKind.CARDIO && (Track.MINUTES in it.tracks || Track.DISTANCE in it.tracks) },
            units = units,
            onLog = { e, minutes, km -> wvm.logCardio(e, minutes, km, vm.day.value); cardio = false },
            onDismiss = { cardio = false },
        )
    }
    viewing?.let { w -> WorkoutDetailsDialog(w, units, wvm, onDismiss = { viewing = null }) }
    if (planning) ProgrammeDialog(saved, programme, onSave = { wvm.setProgramme(it); planning = false }, onDismiss = { planning = false })
}

private fun summary(w: Workout, u: UnitSettings): String {
    val sets = w.exercises.sumOf { it.sets.count { s -> s.done } }
    val cardio = w.exercises.singleOrNull()?.takeIf { it.exercise.kind == ExerciseKind.CARDIO && it.sets.size == 1 && it.exercise.long }?.sets?.firstOrNull()
    return if (cardio != null) Workouts.describe(cardio, u)
    else "${w.minutes().toInt()} min · $sets ${if (sets == 1) "set" else "sets"}"
}

fun burnedText(kcal: Double, u: UnitSettings): String = if (kcal <= 0) "" else "− " + energy(kcal, u)

@Composable
private fun WorkoutDetailsDialog(w: Workout, units: UnitSettings, wvm: WorkoutViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(w.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${summary(w, units)}${if (w.burnedKcal > 0) " · about " + energy(w.burnedKcal, units) + " burned" else ""}", style = MaterialTheme.typography.bodySmall)
                for (we in w.exercises) {
                    Text(we.exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text(we.sets.filter { it.done }.joinToString("\n") { Workouts.describe(it, units) }, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                TextInput(name, { name = it }, "Save as a workout, named", "field:save-as")
                TextButton(onClick = { wvm.saveAsWorkout(name, w); onDismiss() }, enabled = name.isNotBlank()) { Text("Save for next time") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete-workout")) { Text("Delete this workout") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (confirmDelete) {
        ConfirmDialog("Delete this workout?", "Its sets and the calories it burned are taken out.", "Delete", { wvm.deleteWorkout(w); confirmDelete = false; onDismiss() }, { confirmDelete = false })
    }
}

/** Cardio typed in: which kind, how long, and how far if known. */
@Composable
private fun LogCardioDialog(exercises: List<Exercise>, units: UnitSettings, onLog: (Exercise, Double, Double?) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(exercises.firstOrNull()) }
    var minutes by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log cardio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                picked?.let { sel -> Choices(exercises, sel, { it.name }, { "cardio:${it.name}" }) { picked = it } }
                NumberField(minutes, { minutes = it }, "How long", "field:cardio-minutes", Modifier.fillMaxWidth(), suffix = "min")
                NumberField(distance, { distance = it }, "How far (optional)", "field:cardio-distance", Modifier.fillMaxWidth(), suffix = units.distance.short)
                Text("With a distance, the calories come from your speed.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val m = Units.parse(minutes)
            TextButton(
                onClick = { val e = picked ?: return@TextButton; onLog(e, m ?: 0.0, Units.parse(distance)?.takeIf { it > 0 }?.let { Units.shownToKm(it, units.distance) }) },
                enabled = picked != null && m != null && m > 0,
                modifier = Modifier.testTag("confirm-cardio"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The workout in progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(wvm: WorkoutViewModel, modifier: Modifier, back: () -> Unit) {
    val active by wvm.active.collectAsState()
    val loaded by wvm.loaded.collectAsState()
    val units by wvm.units.collectAsState()
    val rest by RestTimer.rest.collectAsState()
    val restTimes by wvm.restTimes.collectAsState()
    val celebration by wvm.celebration.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var restFor by remember { mutableStateOf<Exercise?>(null) }
    var platesFor by remember { mutableStateOf<Int?>(null) }
    var noting by remember { mutableStateOf<Exercise?>(null) }
    var tracking by remember { mutableStateOf<Exercise?>(null) }
    var restSet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val extras = on(io.github.randomtools3377.fitness.core.Feature.TRAINING_EXTRAS)
    val notes by wvm.notes.collectAsState()
    val keepOn by wvm.keepScreenOn.collectAsState()
    // Leaving: a workout under a minute with nothing ticked isn't kept (0.20.0).
    val leave = { wvm.leave(); back() }
    androidx.activity.compose.BackHandler { leave() }
    // Keep the screen on while the workout is open, if chosen (0.20.0).
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    // Bumped when a hint fills in the sets, so their boxes show the new numbers.
    var refills by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    LaunchedEffect(rest, now) {
        val r = rest
        if (r != null && now >= r.endsAt) RestTimer.finishedOnScreen(context)
    }

    val w = active
    if (w == null) {
        // Finished or discarded, or not started: go back once it's known.
        LaunchedEffect(loaded, w) { if (loaded) { delay(800); if (wvm.active.value == null) back() } }
        Box(modifier.fillMaxSize())
        return
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(w.name, modifier = Modifier.clickable { renaming = true }.testTag("workout-name")) },
            navigationIcon = {
                IconButton(onClick = leave, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                Text(Workouts.clock(((now - w.startedAt) / 1000).toInt().coerceAtLeast(0)), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { finishing = true }, modifier = Modifier.testTag("finish-workout")) { Text("Finish") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(
                            text = { Text(if (keepOn) "✓ Keep the screen on" else "Keep the screen on") },
                            onClick = { menu = false; wvm.setKeepScreenOn(!keepOn) },
                            modifier = Modifier.testTag("toggle-screen-on"),
                        )
                        DropdownMenuItem(text = { Text("Discard this workout") }, onClick = { menu = false; discarding = true }, modifier = Modifier.testTag("discard-workout"))
                    }
                }
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("workout-list")) {
            itemsIndexed(w.exercises, key = { i, we -> "$i:${we.exercise.key}" }) { i, we ->
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(we.exercise.name, style = MaterialTheme.typography.titleMedium)
                            // The user's own cue for this exercise (0.13.0).
                            val note = notes[we.exercise.key]
                            if (extras) Text(
                                note ?: "Add a note",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (note == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { noting = we.exercise }.testTag("note:$i"),
                            )
                            if (!we.exercise.long) {
                                Text(
                                    "Rest ${Workouts.clock(restTimes[we.exercise.key] ?: Exercises.DEFAULT_REST_SECONDS)} between sets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { restFor = we.exercise }.testTag("rest:$i"),
                                )
                            }
                        }
                        ExerciseMenu(w, i, we, wvm, extras, onPlates = { platesFor = i }, onTrack = { tracking = we.exercise })
                        IconButton(onClick = { wvm.removeExercise(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove exercise") }
                    }
                    we.superset?.let { g ->
                        Text(
                            "Circuit ${('A' + (g - 1) % 26)}: one set of each in turn, then rest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.testTag("superset:$i"),
                        )
                    }
                    // "Try a bit more" (0.9.0), until a set is ticked.
                    if (extras && we.sets.none { it.done }) {
                        val h = remember(we.exercise.key, w.id) { wvm.hint(we.exercise) }
                        if (h != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(h.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag("hint:$i"))
                                TextButton(onClick = { wvm.applyHint(i, h); refills++ }, modifier = Modifier.testTag("use-hint:$i")) { Text("Use it") }
                            }
                        }
                    }
                    val tracks = we.exercise.tracks
                    SetHeader(tracks, units)
                    we.sets.forEachIndexed { j, s ->
                        SetRow(w.id, i, j, tracks, s, units, wvm, "${w.exercises.size}:${we.sets.size}:$refills:$tracks", extras, rests = !we.exercise.long, onRest = { restSet = i to j })
                    }
                    TextButton(onClick = { wvm.addSet(i) }, modifier = Modifier.testTag("add-set:$i")) { Text("+ Add a set") }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.testTag("add-exercise")) { Text("Add an exercise") }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
        rest?.let { r -> RestBar(r.endsAt, r.seconds, now) }
    }

    if (picking) ExercisePicker(wvm, onPick = { wvm.addExercise(it); picking = false }, onDismiss = { picking = false })
    tracking?.let { e ->
        TrackDialog(e, onSave = { wvm.setTrack(e, it); tracking = null; refills++ }, onDismiss = { tracking = null })
    }
    restSet?.let { (i, j) ->
        val we = w.exercises.getOrNull(i)
        val s = we?.sets?.getOrNull(j)
        if (we == null || s == null) { restSet = null; return@let }
        val usual = restTimes[we.exercise.key] ?: Exercises.DEFAULT_REST_SECONDS
        SetRestDialog(we.exercise.name, j, s.restSeconds, usual, onSave = { wvm.setSetRest(i, j, it); restSet = null }, onDismiss = { restSet = null })
    }
    if (finishing) {
        var name by remember { mutableStateOf("") }
        val fromSaved = w.savedWorkoutId != null
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Finish the workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only ticked sets count.")
                    if (!fromSaved) {
                        Text("To do it again, save it with a name (optional):", style = MaterialTheme.typography.bodySmall)
                        TextInput(name, { name = it }, "Like \"Leg day\"", "field:finish-name")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { finishing = false; wvm.finish(name.ifBlank { null }) }, modifier = Modifier.testTag("confirm-finish")) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Keep going") } },
        )
    }
    if (discarding) {
        ConfirmDialog("Discard this workout?", "Nothing from it is kept.", "Discard", { discarding = false; wvm.discard() }, { discarding = false }, confirmTag = "confirm-discard")
    }
    if (renaming) {
        var name by remember { mutableStateOf(w.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { TextInput(name, { name = it }, "Name", "field:workout-name") },
            confirmButton = { TextButton(onClick = { wvm.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    noting?.let { e ->
        var text by remember(e.key) { mutableStateOf(notes[e.key].orEmpty()) }
        AlertDialog(
            onDismissRequest = { noting = null },
            title = { Text("Note for ${e.name}") },
            text = { TextInput(text, { text = it }, "Like \"elbows in\"", "field:note") },
            confirmButton = { TextButton(onClick = { wvm.setNote(e.key, text); noting = null }, modifier = Modifier.testTag("save-note")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { noting = null }) { Text("Cancel") } },
        )
    }
    platesFor?.let { idx ->
        val we = w.exercises.getOrNull(idx)
        val firstOpen = we?.sets?.firstOrNull { !it.done && !it.warmUp }?.weightKg ?: we?.sets?.firstOrNull()?.weightKg
        PlatesDialog(wvm, units, firstOpen, onDismiss = { platesFor = null })
    }
    restFor?.let { e ->
        var text by remember(e.key) { mutableStateOf(((restTimes[e.key] ?: Exercises.DEFAULT_REST_SECONDS)).toString()) }
        AlertDialog(
            onDismissRequest = { restFor = null },
            title = { Text("Rest for ${e.name}") },
            text = { NumberField(text, { text = it }, "Seconds between sets", "field:rest-seconds", Modifier.fillMaxWidth(), suffix = "s", decimals = false) },
            confirmButton = {
                TextButton(onClick = { text.toIntOrNull()?.let { wvm.setRest(e.key, it) }; restFor = null }, enabled = (text.toIntOrNull() ?: 0) in 5..3600) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { restFor = null }) { Text("Cancel") } },
        )
    }
    if (celebration.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { wvm.celebration.value = emptyList() },
            title = { Text("🏆 New personal best!", modifier = Modifier.testTag("pb-dialog")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val e = celebration.first().new.exercise
                    Text(e.name, style = MaterialTheme.typography.titleMedium)
                    for (b in celebration) {
                        Text("${b.kind.title}: ${b.new.describe(units)}", fontWeight = FontWeight.Medium)
                        Text("Up from ${b.old.describe(units)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { wvm.celebration.value = emptyList() }, modifier = Modifier.testTag("close-pb")) { Text("Keep going") } },
        )
    }
}

/** The column title for each thing logged. */
fun trackColumn(t: Track, units: UnitSettings): String = when (t) {
    Track.WEIGHT -> units.weight.short
    Track.REPS -> "reps"
    Track.SECONDS -> "seconds"
    Track.MINUTES -> "minutes"
    Track.DISTANCE -> units.distance.short
}

@Composable
fun SetHeader(tracks: List<Track>, units: UnitSettings, end: androidx.compose.ui.unit.Dp = 88.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SET", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall)
        for (c in tracks) Text(trackColumn(c, units).uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(end))
    }
}

/** The number boxes of one set, one for each thing logged (0.20.0). [tag] is "$i:$j". */
@Composable
fun androidx.compose.foundation.layout.RowScope.SetBoxes(
    tracks: List<Track>,
    s: WorkoutSet,
    units: UnitSettings,
    key: String,
    tag: String,
    update: ((WorkoutSet) -> WorkoutSet) -> Unit,
) {
    for (t in tracks) when (t) {
        Track.WEIGHT -> SetField(s.weightKg?.let { formatAmount(Units.kgToShown(it, units.weight)) } ?: "", "field:weight:$tag", "$key:w", true, Modifier.weight(1f)) { x ->
            update { it.copy(weightKg = Units.parse(x)?.let { v -> Units.shownToKg(v, units.weight) }) }
        }
        Track.REPS -> SetField(s.reps?.toString() ?: "", "field:reps:$tag", "$key:reps", false, Modifier.weight(1f)) { x -> update { it.copy(reps = x.toIntOrNull()) } }
        Track.SECONDS -> SetField(s.seconds?.toString() ?: "", "field:seconds:$tag", "$key:seconds", false, Modifier.weight(1f)) { x -> update { it.copy(seconds = x.toIntOrNull()) } }
        Track.MINUTES -> SetField(s.seconds?.let { formatAmount(Math.round(it / 6.0) / 10.0) } ?: "", "field:minutes:$tag", "$key:minutes", true, Modifier.weight(1f)) { x ->
            update { it.copy(seconds = Units.parse(x)?.let { m -> (m * 60).toInt() }) }
        }
        Track.DISTANCE -> SetField(s.distanceKm?.let { formatAmount(Units.kmToShown(it, units.distance)) } ?: "", "field:distance:$tag", "$key:distance", true, Modifier.weight(1f)) { x ->
            update { it.copy(distanceKm = Units.parse(x)?.let { d -> Units.shownToKm(d, units.distance) }) }
        }
    }
}

/** A small number box inside a set row. Keeps what is typed while it is being typed. */
@Composable
private fun SetField(initial: String, tag: String, stateKey: String, decimals: Boolean, modifier: Modifier, onValue: (String) -> Unit) {
    var text by remember(stateKey) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> val f = t.filter { it.isDigit() || (decimals && (it == '.' || it == ',')) }; text = f; onValue(f) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.padding(end = 6.dp).heightIn(min = 48.dp).testTag(tag),
    )
}

@Composable
private fun SetRow(
    workoutId: Long, i: Int, j: Int, tracks: List<Track>, s: WorkoutSet, units: UnitSettings, wvm: WorkoutViewModel, shape: String,
    extras: Boolean, rests: Boolean, onRest: () -> Unit,
) {
    // Changes when sets or exercises are added or removed, so the boxes are filled in afresh.
    val key = "$workoutId:$i:$j:$shape"
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // The set's number opens its menu: its own rest (0.20.0), and marking a warm-up (0.9.0), shown as W.
        Box(Modifier.width(36.dp)) {
            Text(
                if (s.warmUp) "W" else "${j + 1}",
                Modifier.clickable(enabled = rests || extras) { menu = true }.padding(vertical = 12.dp).testTag("warmup:$i:$j"),
                color = if (s.done) MaterialTheme.colorScheme.outline else if (s.warmUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (rests) DropdownMenuItem(
                    text = { Text("Rest after this set" + (s.restSeconds?.let { ": " + Workouts.clock(it) } ?: "…")) },
                    onClick = { menu = false; onRest() },
                    modifier = Modifier.testTag("set-rest:$i:$j"),
                )
                if (extras) DropdownMenuItem(
                    text = { Text(if (s.warmUp) "Make it a working set" else "Make it a warm-up set") },
                    onClick = { menu = false; wvm.toggleWarmUp(i, j) },
                    modifier = Modifier.testTag("make-warmup:$i:$j"),
                )
            }
        }
        SetBoxes(tracks, s, units, key, "$i:$j") { f -> wvm.updateSet(i, j, f) }
        FilledIconToggleButton(checked = s.done, onCheckedChange = { wvm.tick(i, j) }, modifier = Modifier.testTag("tick:$i:$j")) {
            Icon(Icons.Filled.Check, contentDescription = if (s.done) "Done" else "Tick when done")
        }
        IconButton(onClick = { wvm.removeSet(i, j) }) { Icon(Icons.Filled.Close, contentDescription = "Remove set") }
    }
    s.restSeconds?.takeIf { rests }?.let {
        Text("Then rest ${Workouts.clock(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 36.dp).testTag("set-rest-shown:$i:$j"))
    }
}

/** The rest after one set (0.20.0): its own, or back to the exercise's. */
@Composable
fun SetRestDialog(name: String, j: Int, own: Int?, usual: Int, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf((own ?: usual).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest after set ${j + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.bodySmall)
                NumberField(text, { text = it }, "Seconds", "field:set-rest", Modifier.fillMaxWidth(), suffix = "s", decimals = false)
                Text("The other sets keep the exercise's rest (${Workouts.clock(usual)}). 0 means no rest.", style = MaterialTheme.typography.bodySmall)
                if (own != null) TextButton(onClick = { onSave(null) }, modifier = Modifier.testTag("set-rest-usual")) { Text("Use the exercise's rest") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toIntOrNull()) }, enabled = (text.toIntOrNull() ?: -1) in 0..3600, modifier = Modifier.testTag("save-set-rest")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Choosing what is logged for each set of an exercise (0.20.0). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TrackChips(chosen: List<Track>, onChange: (List<Track>) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in Track.entries) {
            androidx.compose.material3.FilterChip(
                selected = t in chosen,
                onClick = {
                    val next = if (t in chosen) chosen - t else {
                        // One kind of time at once.
                        val other = when (t) { Track.SECONDS -> Track.MINUTES; Track.MINUTES -> Track.SECONDS; else -> null }
                        chosen - setOfNotNull(other) + t
                    }
                    onChange(Track.ordered(next))
                },
                label = { Text(t.title) },
                modifier = Modifier.testTag("track:${t.name}"),
            )
        }
    }
}

@Composable
fun TrackDialog(e: Exercise, onSave: (List<Track>) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember(e.key) { mutableStateOf(e.tracks) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What to log for ${e.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackChips(chosen) { chosen = it }
                Text(
                    "Pick any mix, like reps for cardio. It's kept for this exercise from now on. Its tag (${e.kind.title}) stays as it is.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val usual = Exercises.usualTrack(e)
                if (chosen != usual) {
                    TextButton(onClick = { chosen = usual }) { Text("Back to the usual: " + usual.joinToString(", ") { it.title.lowercase() }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }, enabled = chosen.isNotEmpty(), modifier = Modifier.testTag("save-track")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestBar(endsAt: Long, seconds: Int, now: Long) {
    val context = LocalContext.current
    val left = ((endsAt - now) / 1000).toInt().coerceAtLeast(0)
    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("rest-timer")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("REST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
                Text(Workouts.clock(left), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
            LinearProgressIndicator(
                progress = { (left.toFloat() / seconds.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.inversePrimary,
            )
            TextButton(onClick = { RestTimer.add(context, 15) }) { Text("+15 s", color = MaterialTheme.colorScheme.inverseOnSurface) }
            TextButton(onClick = { RestTimer.stop(context) }, modifier = Modifier.testTag("skip-rest")) { Text("Skip", color = MaterialTheme.colorScheme.inverseOnSurface) }
        }
    }
}

/** Choosing an exercise: the built-in list and your own, or a new one of your own. */
@Composable
fun ExercisePicker(wvm: WorkoutViewModel, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    val all by wvm.exercises.collectAsState()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(ExerciseKind.WEIGHTS) }
    var track by remember { mutableStateOf(Track.usual(ExerciseKind.WEIGHTS)) }
    val scope = rememberCoroutineScope()
    val shown = Exercises.search(query, all)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "A new exercise" else "Add an exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(query, { query = it }, if (creating) "Name" else "Search", "field:exercise-search")
                if (creating) {
                    Text("Tag", style = MaterialTheme.typography.labelLarge)
                    Choices(ExerciseKind.entries, kind, { it.title }, { "kind:${it.name}" }) {
                        // Changing the tag suggests what to log, until the user picks their own.
                        if (track == Track.usual(kind)) track = Track.usual(it)
                        kind = it
                    }
                    Text("What to log for each set", style = MaterialTheme.typography.labelLarge)
                    TrackChips(track) { track = it }
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(shown, key = { it.key }) { e ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(e) }.padding(vertical = 8.dp).testTag("exercise:${e.name}")) {
                                Text(e.name)
                                Text(
                                    (listOf(e.kind.title, e.muscle).filter { it.isNotBlank() }.distinct() + e.tracks.joinToString(", ") { it.title.lowercase() }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { creating = true }, modifier = Modifier.testTag("new-exercise")) { Text("Not here? Add your own") }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { scope.launch { wvm.newExercise(query, kind, track)?.let(onPick) } }, enabled = query.isNotBlank() && track.isNotEmpty(), modifier = Modifier.testTag("save-exercise")) {
                    Text("Add")
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (creating) creating = false else onDismiss() }) { Text(if (creating) "Back" else "Cancel") } },
    )
}

/** Personal bests for every exercise done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestsScreen(wvm: WorkoutViewModel, modifier: Modifier, back: () -> Unit) {
    val bests by wvm.bests.collectAsState()
    val units by wvm.units.collectAsState()
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Personal bests") },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        val byExercise: Map<Exercise, List<Best>> = bests.groupBy { it.exercise }
        LazyColumn(Modifier.fillMaxSize().testTag("bests-list")) {
            if (byExercise.isEmpty()) item { Text("Tick sets in a workout, and your bests show here.", Modifier.padding(16.dp)) }
            for ((e, list) in byExercise.toList().sortedBy { it.first.name.lowercase() }) {
                item(key = e.key) {
                    Section(e.name) {
                        // Reps at a weight: only the heaviest few weights, so the list stays short.
                        val reps = list.filter { it.kind.name == "REPS_AT_WEIGHT" }.sortedByDescending { it.atKg }.take(3)
                        for (b in list.filter { it.kind.name != "REPS_AT_WEIGHT" } + reps) {
                            LabelValue(b.kind.title, b.describe(units))
                        }
                        // The weights for 1 to 12 reps from the estimated one-rep max (0.13.0).
                        list.firstOrNull { it.kind == io.github.randomtools3377.fitness.core.BestKind.ONE_REP_MAX }?.let { orm ->
                            var open by remember { mutableStateOf(false) }
                            TextButton(onClick = { open = !open }, modifier = Modifier.testTag("rep-table:${e.name}")) {
                                Text(if (open) "Hide the weights for each number of reps" else "Weights for 1 to 12 reps")
                            }
                            if (open) {
                                for ((reps, kg) in io.github.randomtools3377.fitness.core.RepTable.of(orm.value)) {
                                    LabelValue("$reps ${if (reps == 1) "rep" else "reps"}", Units.weight(kg, units.weight), tag = "rep:${e.name}:$reps")
                                }
                                Text("Estimates from your best, by the Epley formula.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The exercise's ⋮ menu: what to log, order, circuits and, with the extras, the plate calculator. */
@Composable
private fun ExerciseMenu(
    w: Workout, i: Int, we: io.github.randomtools3377.fitness.core.WorkoutExercise, wvm: WorkoutViewModel, extras: Boolean,
    onPlates: () -> Unit, onTrack: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("exercise-menu:$i")) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("What to log…") }, onClick = { open = false; onTrack() }, modifier = Modifier.testTag("track-menu:$i"))
            if (i > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { open = false; wvm.moveExercise(i, -1) }, modifier = Modifier.testTag("move-up:$i"))
            if (i < w.exercises.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { open = false; wvm.moveExercise(i, 1) }, modifier = Modifier.testTag("move-down:$i"))
            if (i < w.exercises.lastIndex) {
                DropdownMenuItem(
                    text = { Text("Do in a circuit with the next one") },
                    onClick = { open = false; wvm.joinSuperset(i) },
                    modifier = Modifier.testTag("join-superset:$i"),
                )
            }
            if (we.superset != null) {
                DropdownMenuItem(text = { Text("Take out of the circuit") }, onClick = { open = false; wvm.leaveSuperset(i) }, modifier = Modifier.testTag("leave-superset:$i"))
            }
            if (extras && Track.WEIGHT in we.exercise.tracks) {
                DropdownMenuItem(text = { Text("Plate calculator") }, onClick = { open = false; onPlates() }, modifier = Modifier.testTag("plates:$i"))
            }
            DropdownMenuItem(text = { Text("Tap a set's number for its own rest") }, onClick = { open = false }, enabled = false)
        }
    }
}

/** Which plates go on each side of the bar for a weight (0.9.0). */
@Composable
fun PlatesDialog(wvm: WorkoutViewModel, units: UnitSettings, initialKg: Double?, onDismiss: () -> Unit) {
    val plates by wvm.plates.collectAsState()
    val lb = units.weight == io.github.randomtools3377.fitness.core.WeightUnit.LB
    var text by remember { mutableStateOf(initialKg?.let { formatAmount(Math.round(Units.kgToShown(it, units.weight) * 100) / 100.0) }.orEmpty()) }
    val bar = if (lb) plates.barLb else plates.barKg
    val list = if (lb) plates.platesLb else plates.platesKg
    val unit = units.weight.short
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Weight on the bar, in total", "field:plates-total", Modifier.fillMaxWidth(), suffix = unit)
                val total = Units.parse(text)
                if (total != null) {
                    val l = io.github.randomtools3377.fitness.core.Plates.load(total, bar, list)
                    if (l == null) {
                        Text("That's less than the bar (${formatAmount(bar)} $unit).")
                    } else {
                        Text("On each side: ${io.github.randomtools3377.fitness.core.Plates.describe(l)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("plates-per-side"))
                        if (!l.exact) Text("${formatAmount(l.left)} $unit can't be made with your plates.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Bar ${formatAmount(bar)} $unit; plates ${list.joinToString(", ") { formatAmount(it) }} $unit. Change them in Settings → Workouts.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Picking a saved workout for each day of the week, or none (0.9.0). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProgrammeDialog(saved: List<SavedWorkout>, current: Map<Int, Long>, onSave: (Map<Int, Long>) -> Unit, onDismiss: () -> Unit) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val plan = remember { androidx.compose.runtime.mutableStateMapOf<Int, Long>().apply { putAll(current) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your week") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, name ->
                    val d = i + 1
                    Text(name, style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.FilterChip(selected = plan[d] == null, onClick = { plan.remove(d) }, label = { Text("Rest") }, modifier = Modifier.testTag("prog:$d:rest"))
                        for (sw in saved) {
                            androidx.compose.material3.FilterChip(selected = plan[d] == sw.id, onClick = { plan[d] = sw.id }, label = { Text(sw.name) }, modifier = Modifier.testTag("prog:$d:${sw.name}"))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(plan.toMap()) }, modifier = Modifier.testTag("save-programme")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
