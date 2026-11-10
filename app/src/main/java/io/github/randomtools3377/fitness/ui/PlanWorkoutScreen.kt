package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.Exercise
import io.github.randomtools3377.fitness.core.Exercises
import io.github.randomtools3377.fitness.core.Feature
import io.github.randomtools3377.fitness.core.SavedWorkout
import io.github.randomtools3377.fitness.core.SavedWorkoutItem
import io.github.randomtools3377.fitness.core.Supersets
import io.github.randomtools3377.fitness.core.Track
import io.github.randomtools3377.fitness.core.WorkoutSet
import io.github.randomtools3377.fitness.core.Workouts

/**
 * Planning a saved workout ahead, or changing one (0.20.0): its exercises in order, circuits,
 * each set's numbers and rest. Nothing is started; it is saved for later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanWorkoutScreen(wvm: WorkoutViewModel, id: Long, modifier: Modifier, back: () -> Unit) {
    val saved by wvm.saved.collectAsState()
    val units by wvm.units.collectAsState()
    val restTimes by wvm.restTimes.collectAsState()
    val extras = on(Feature.TRAINING_EXTRAS)
    var name by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SavedWorkoutItem>>(emptyList()) }
    var createdAt by remember { mutableStateOf(0L) }
    var ready by remember { mutableStateOf(id == 0L) }
    // Bumped whenever sets or exercises are added, removed or moved, so the boxes are filled in afresh.
    var shape by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf<Int?>(null) }
    var restFor by remember { mutableStateOf<Exercise?>(null) }
    var restSet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val eachSet = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var changed by remember { mutableStateOf(false) }

    LaunchedEffect(saved, id) {
        if (ready) return@LaunchedEffect
        val sw = saved.firstOrNull { it.id == id } ?: return@LaunchedEffect
        name = sw.name
        createdAt = sw.createdAt
        items = sw.items.map { it.copy(exercise = wvm.current(it.exercise), plan = it.planned()) }
        ready = true
        shape++
    }

    fun edit(f: (List<SavedWorkoutItem>) -> List<SavedWorkoutItem>, reshape: Boolean = false) {
        items = f(items)
        changed = true
        if (reshape) shape++
    }

    fun setPlan(i: Int, f: (List<WorkoutSet>) -> List<WorkoutSet>, reshape: Boolean = false) =
        edit({ l -> l.mapIndexed { k, it -> if (k == i) it.copy(plan = f(it.plan)).let { x -> x.copy(sets = x.plan.size) } else it } }, reshape)

    fun tryBack() { if (changed) leaving = true else back() }
    androidx.activity.compose.BackHandler { tryBack() }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (id == 0L) "Plan a workout" else "Change the workout") },
            navigationIcon = { IconButton(onClick = { tryBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(
                    onClick = { wvm.savePlanned(SavedWorkout(id, name, items, createdAt), done = back) },
                    enabled = ready && name.isNotBlank() && items.isNotEmpty(),
                    modifier = Modifier.testTag("save-plan"),
                ) { Text("Save") }
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("plan-list")) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextInput(name, { name = it; changed = true }, "Name, like \"Leg day\"", "field:plan-name")
                    Text(
                        "Set up the exercises, sets and rests now, and start it from the Workouts tab later. " +
                            "Once you've done an exercise, its numbers come from last time; the rests stay as planned here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // One circuit for everything, or none (0.21.0).
                    if (items.size >= 2) Row {
                        TextButton(onClick = {
                            val g = Supersets.allOne(items.size, true)
                            edit({ l -> l.mapIndexed { k, it -> it.copy(superset = g[k]) } })
                        }, modifier = Modifier.testTag("plan-all-one")) { Text("All one circuit") }
                        if (items.any { it.superset != null }) TextButton(onClick = {
                            edit({ l -> l.map { it.copy(superset = null) } })
                        }, modifier = Modifier.testTag("plan-no-circuits")) { Text("No circuits") }
                    }
                }
            }
            itemsIndexed(items, key = { i, it -> "$i:${it.exercise.key}:$shape" }) { i, item ->
                val e = item.exercise
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium)
                            if (!e.long) Text(
                                "Rest ${Workouts.clock(restTimes[e.key] ?: Exercises.DEFAULT_REST_SECONDS)} between sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { restFor = e }.testTag("plan-rest:$i"),
                            )
                        }
                        val g = item.superset
                        PlanItemMenu(
                            i, items.size, g != null,
                            canMoveCircuit = g != null && (items.indexOfFirst { it.superset == g } > 0 || items.indexOfLast { it.superset == g } < items.lastIndex),
                            onMoveCircuit = { by -> edit({ Supersets.moveItems(it, i, by, whole = true) }, reshape = true) },
                            onTrack = { tracking = i },
                            onMove = { by -> edit({ Supersets.moveItems(it, i, by) }, reshape = true) },
                            onJoin = { edit({ Supersets.joinItems(it, i) }) },
                            onLeave = { edit({ Supersets.leaveItems(it, i) }) },
                            onRemove = { edit({ l -> Supersets.tidyItems(l.filterIndexed { k, _ -> k != i }) }, reshape = true) },
                        )
                    }
                    item.superset?.let { g ->
                        Text(
                            "Circuit ${('A' + (g - 1) % 26)}: one set of each in turn, then rest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.testTag("plan-circuit:$i"),
                        )
                    }
                    val tracks = e.tracks
                    // All the sets at once (0.21.0).
                    AllSetsBar(tracks, item.plan, units, "plan:$i",
                        onCount = { n -> setPlan(i, { Workouts.resize(it, n.coerceIn(1, 30)) }, reshape = true) },
                        onOpen = { f -> setPlan(i, { p -> p.map(f) }, reshape = true) })
                    val showEach = eachSet["$i:${e.key}"] ?: eachSetAtFirst(item.plan)
                    if (showEach) SetHeader(tracks, units, end = 48.dp)
                    if (showEach) item.plan.forEachIndexed { j, s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlanSetNumber(i, j, s, rests = !e.long, extras = extras, reps = Track.REPS in tracks,
                                onRest = { restSet = i to j },
                                onFailure = { setPlan(i, { p -> p.mapIndexed { k, x -> if (k == j) x.copy(toFailure = !x.toFailure, reps = if (!x.toFailure) null else x.reps) else x } }, reshape = true) },
                                onWarmUp = { setPlan(i, { p -> p.mapIndexed { k, x -> if (k == j) x.copy(warmUp = !x.warmUp) else x } }) })
                            SetBoxes(tracks, s, units, "plan:$i:$j:$shape:$tracks", "plan:$i:$j") { f ->
                                setPlan(i, { p -> p.mapIndexed { k, x -> if (k == j) f(x) else x } })
                            }
                            IconButton(onClick = { setPlan(i, { p -> p.filterIndexed { k, _ -> k != j } }, reshape = true) }, enabled = item.plan.size > 1) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove set")
                            }
                        }
                        s.restSeconds?.takeIf { !e.long }?.let {
                            Text("Then rest ${Workouts.clock(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 36.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EachSetToggle(showEach, "plan:$i") { eachSet["$i:${e.key}"] = !showEach }
                        if (showEach) TextButton(
                            onClick = { setPlan(i, { p -> Workouts.resize(p, p.size + 1) }, reshape = true) },
                            modifier = Modifier.testTag("plan-add-set:$i"),
                        ) { Text("+ Add a set") }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { picking = true }, enabled = ready, modifier = Modifier.testTag("plan-add-exercise")) { Text("Add an exercise") }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    if (picking) {
        ExercisePicker(wvm, onPick = { e ->
            val plan = wvm.lastSets(e.key)?.map { it.copy(done = false, doneAt = null) }?.ifEmpty { null } ?: List(3) { WorkoutSet() }
            edit({ it + SavedWorkoutItem(e, plan.size, plan = plan) }, reshape = true)
            picking = false
        }, onDismiss = { picking = false })
    }
    tracking?.let { i ->
        val e = items.getOrNull(i)?.exercise
        if (e == null) { tracking = null; return@let }
        TrackDialog(e, onSave = { t ->
            wvm.setTrack(e, t)
            edit({ l -> l.map { if (it.exercise.key == e.key) it.copy(exercise = it.exercise.copy(track = t)) else it } }, reshape = true)
            tracking = null
        }, onDismiss = { tracking = null })
    }
    restFor?.let { e ->
        var text by remember(e.key) { mutableStateOf((restTimes[e.key] ?: Exercises.DEFAULT_REST_SECONDS).toString()) }
        AlertDialog(
            onDismissRequest = { restFor = null },
            title = { Text("Rest for ${e.name}") },
            text = { NumberField(text, { text = it }, "Seconds between sets", "field:plan-rest-seconds", Modifier.fillMaxWidth(), suffix = "s", decimals = false) },
            confirmButton = {
                TextButton(onClick = { text.toIntOrNull()?.let { wvm.setRest(e.key, it) }; restFor = null }, enabled = (text.toIntOrNull() ?: 0) in 5..3600) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { restFor = null }) { Text("Cancel") } },
        )
    }
    restSet?.let { (i, j) ->
        val item = items.getOrNull(i)
        val s = item?.plan?.getOrNull(j)
        if (item == null || s == null) { restSet = null; return@let }
        SetRestDialog(item.exercise.name, j, s.restSeconds, restTimes[item.exercise.key] ?: Exercises.DEFAULT_REST_SECONDS, onSave = { sec ->
            setPlan(i, { p -> p.mapIndexed { k, x -> if (k == j) x.copy(restSeconds = sec) else x } })
            restSet = null
        }, onDismiss = { restSet = null })
    }
    if (leaving) {
        ConfirmDialog("Leave without saving?", "The changes to this workout are not kept.", "Leave", { leaving = false; back() }, { leaving = false }, confirmTag = "confirm-leave-plan")
    }
}

@Composable
private fun PlanSetNumber(i: Int, j: Int, s: WorkoutSet, rests: Boolean, extras: Boolean, reps: Boolean, onRest: () -> Unit, onFailure: () -> Unit, onWarmUp: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.width(36.dp)) {
        Text(
            if (s.warmUp) "W" else "${j + 1}",
            Modifier.clickable(enabled = rests || extras || reps) { menu = true }.padding(vertical = 12.dp).testTag("plan-set:$i:$j"),
            color = if (s.warmUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (rests) DropdownMenuItem(
                text = { Text("Rest after this set" + (s.restSeconds?.let { ": " + Workouts.clock(it) } ?: "…")) },
                onClick = { menu = false; onRest() },
                modifier = Modifier.testTag("plan-set-rest:$i:$j"),
            )
            if (extras) DropdownMenuItem(text = { Text(if (s.warmUp) "Make it a working set" else "Make it a warm-up set") }, onClick = { menu = false; onWarmUp() })
            if (reps) DropdownMenuItem(
                text = { Text(if (s.toFailure) "✓ Reps till failure" else "Reps till failure") },
                onClick = { menu = false; onFailure() },
                modifier = Modifier.testTag("plan-failure:$i:$j"),
            )
        }
    }
}

@Composable
private fun PlanItemMenu(
    i: Int, count: Int, inCircuit: Boolean, canMoveCircuit: Boolean, onMoveCircuit: (Int) -> Unit,
    onTrack: () -> Unit, onMove: (Int) -> Unit, onJoin: () -> Unit, onLeave: () -> Unit, onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("plan-menu:$i")) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("What to log…") }, onClick = { open = false; onTrack() }, modifier = Modifier.testTag("plan-track:$i"))
            if (i > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { open = false; onMove(-1) }, modifier = Modifier.testTag("plan-up:$i"))
            if (i < count - 1) DropdownMenuItem(text = { Text("Move down") }, onClick = { open = false; onMove(1) }, modifier = Modifier.testTag("plan-down:$i"))
            if (i < count - 1) DropdownMenuItem(text = { Text("Do in a circuit with the next one") }, onClick = { open = false; onJoin() }, modifier = Modifier.testTag("plan-join:$i"))
            if (canMoveCircuit) {
                DropdownMenuItem(text = { Text("Move the whole circuit up") }, onClick = { open = false; onMoveCircuit(-1) }, modifier = Modifier.testTag("plan-circuit-up:$i"))
                DropdownMenuItem(text = { Text("Move the whole circuit down") }, onClick = { open = false; onMoveCircuit(1) }, modifier = Modifier.testTag("plan-circuit-down:$i"))
            }
            if (inCircuit) DropdownMenuItem(text = { Text("Take out of the circuit") }, onClick = { open = false; onLeave() }, modifier = Modifier.testTag("plan-leave:$i"))
            DropdownMenuItem(text = { Text("Remove") }, onClick = { open = false; onRemove() }, modifier = Modifier.testTag("plan-remove:$i"))
        }
    }
}
