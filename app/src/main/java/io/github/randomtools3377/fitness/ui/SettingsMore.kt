package io.github.randomtools3377.fitness.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.Privacy
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.ReminderSettings
import io.github.randomtools3377.fitness.core.ReminderTime

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "08:30" for minutes after midnight. */
fun clockText(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

/** Minutes after midnight from "8:30", "08:30" or "830"; null if it isn't a time. */
fun parseClock(text: String): Int? {
    val t = text.trim()
    val (h, m) = when {
        ':' in t -> t.substringBefore(':').toIntOrNull() to t.substringAfter(':').toIntOrNull()
        t.length in 3..4 -> t.dropLast(2).toIntOrNull() to t.takeLast(2).toIntOrNull()
        t.length in 1..2 -> t.toIntOrNull() to 0
        else -> null to null
    }
    if (h == null || m == null || h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@Composable
private fun TimeBox(minute: Int, tag: String, onChange: (Int) -> Unit) {
    var text by remember(minute) { mutableStateOf(clockText(minute)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t.filter { it.isDigit() || it == ':' }.take(5); parseClock(text)?.let(onChange) },
        singleLine = true,
        isError = parseClock(text) == null,
        modifier = Modifier.width(96.dp).testTag(tag),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(days: Set<Int>, tag: String, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in 1..7) {
            FilterChip(
                selected = d in days,
                onClick = { onChange(if (d in days) days - d else days + d) },
                label = { Text(DAY_NAMES[d - 1]) },
                modifier = Modifier.testTag("$tag:$d"),
            )
        }
    }
}

/** Optional reminders for meals, water, workouts and weigh-ins, all scheduled on the phone. */
@Composable
fun RemindersSection(vm: MainViewModel, settings: AppSettings) {
    val r = settings.reminders
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save(new: ReminderSettings) {
        if (new.anyOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.setReminders(new)
    }
    Section("Reminders") {
        Text("Optional, and set on the phone itself. Tap a time to change it.", style = MaterialTheme.typography.bodySmall)
        for (meal in Meal.entries) {
            val t = r.meals[meal]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.title, Modifier.weight(1f))
                if (t?.on == true) TimeBox(t.minute, "time:${meal.name}") { m -> save(r.copy(meals = r.meals + (meal to t.copy(minute = m)))) }
                Switch(
                    checked = t?.on == true,
                    onCheckedChange = { on ->
                        val base = t ?: ReminderTime(ReminderSettings.MEAL_TIMES.getValue(meal))
                        save(r.copy(meals = r.meals + (meal to base.copy(on = on))))
                    },
                    modifier = Modifier.testTag("remind:${meal.name}"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Water", Modifier.weight(1f))
            Switch(checked = r.waterOn, onCheckedChange = { save(r.copy(waterOn = it)) }, modifier = Modifier.testTag("remind:WATER"))
        }
        if (r.waterOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Every")
                Choices(listOf(1, 2, 3, 4), r.waterEveryHours, { "$it h" }, { "water-every:$it" }) { save(r.copy(waterEveryHours = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("From")
                TimeBox(r.waterFrom, "water-from") { save(r.copy(waterFrom = it)) }
                Text("to")
                TimeBox(r.waterTo, "water-to") { save(r.copy(waterTo = it)) }
            }
        }
        DaysReminder("Workouts", r.workout, 18 * 60, setOf(1, 3, 5), "WORKOUT") { save(r.copy(workout = it)) }
        DaysReminder("Weigh-ins", r.weighIn, 7 * 60, setOf(1), "WEIGH_IN") { save(r.copy(weighIn = it)) }
    }
}

@Composable
private fun DaysReminder(title: String, t: ReminderTime?, defaultMinute: Int, defaultDays: Set<Int>, tag: String, onChange: (ReminderTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            if (t?.on == true) TimeBox(t.minute, "time:$tag") { onChange(t.copy(minute = it)) }
            Switch(
                checked = t?.on == true,
                onCheckedChange = { on -> onChange((t ?: ReminderTime(defaultMinute, defaultDays)).copy(on = on)) },
                modifier = Modifier.testTag("remind:$tag"),
            )
        }
        if (t?.on == true) DayChips(t.days, "days:$tag") { onChange(t.copy(days = it)) }
    }
}

/** What sits behind the phone's own lock, and hiding the app in the recent-apps screen. */
@Composable
fun PrivacySection(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    var hide by remember { mutableStateOf(Privacy.hideInRecents(context)) }
    Section("Privacy") {
        Text(
            "Choose what opens only with your phone's own lock (fingerprint, face or PIN). It locks again each time you leave the app.",
            style = MaterialTheme.typography.bodySmall,
        )
        for (tab in Tab.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tab.name in settings.locked,
                    onCheckedChange = { on -> vm.setLocked(if (on) settings.locked + tab.name else settings.locked - tab.name) },
                    modifier = Modifier.testTag("lock:${tab.name}"),
                )
                Text(tab.title)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text("The app is hidden in the recent-apps screen.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide in recent apps")
                    Text("On this phone, that also blocks screenshots of the app.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = hide, onCheckedChange = {
                    hide = it
                    Privacy.setHideInRecents(context, it)
                    (context as? Activity)?.let { a -> Privacy.apply(a) }
                })
            }
        }
    }
}

/** The automatic daily backup to a folder the user picks, keeping the last 7. */
@Composable
fun AutoBackupSection(vm: MainViewModel) {
    val folder by vm.backupFolder.collectAsState()
    val last by vm.lastBackup.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.setBackupFolder(uri) }
    Section("Automatic backup") {
        Text(
            "Once a day, when you open the app, everything is saved to a folder you choose. The newest 7 are kept. " +
                "A folder on a memory card, or one a cloud app keeps in step, keeps it off the phone.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(if (folder == null) "Off" else "To the folder \"$folder\"", modifier = Modifier.testTag("backup-folder"))
        last?.let { Text("Last backup: ${dayName(dayOfMillis(it), vm.today())}", style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(null) }, modifier = Modifier.testTag("choose-backup-folder")) {
                Text(if (folder == null) "Choose a folder" else "Change folder")
            }
            if (folder != null) TextButton(onClick = { vm.setBackupFolder(null) }) { Text("Turn off") }
        }
    }
}

@Composable
fun WorkoutsPerWeekRow(vm: MainViewModel, settings: AppSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Workouts a week, for the weekly streak")
        Choices((1..7).toList(), settings.workoutsPerWeek, { "$it" }, { "per-week:$it" }) { vm.setWorkoutsPerWeek(it) }
    }
}

/** A target for each meal: off until turned on, the day's target split between the meals. */
@Composable
fun MealSplitSection(vm: MainViewModel, settings: AppSettings) {
    val current = settings.mealSplit
    var on by remember(current) { mutableStateOf(current.enabled) }
    var mode by remember(current) { mutableStateOf(current.mode) }
    val texts = remember(current) { Meal.entries.associateWith { mutableStateOf(io.github.randomtools3377.fitness.core.formatAmount(current.shares[it] ?: 0.0)) } }
    val day = settings.targetKcal
    fun typed() = io.github.randomtools3377.fitness.core.MealSplit(
        on, mode, Meal.entries.associateWith { io.github.randomtools3377.fitness.core.Units.parse(texts.getValue(it).value) ?: -1.0 },
    )
    Section("A target for each meal") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Split the day's target between meals", Modifier.weight(1f))
            Switch(checked = on, onCheckedChange = { on = it; if (!it) vm.setMealSplit(current.copy(enabled = false)) }, modifier = Modifier.testTag("meal-split-on"))
        }
        if (on) {
            if (day == null) Text("Set a daily calorie target first.", style = MaterialTheme.typography.bodySmall)
            Choices(
                io.github.randomtools3377.fitness.core.MacroMode.entries, mode,
                { if (it == io.github.randomtools3377.fitness.core.MacroMode.PERCENT) "Percent" else "Calories" }, { "split-mode:${it.name}" },
            ) { new ->
                // Keep the same split when switching between percent and calories.
                if (day != null && day > 0) {
                    for (m in Meal.entries) {
                        val x = io.github.randomtools3377.fitness.core.Units.parse(texts.getValue(m).value) ?: continue
                        texts.getValue(m).value = io.github.randomtools3377.fitness.core.formatAmount(
                            Math.round(if (new == io.github.randomtools3377.fitness.core.MacroMode.GRAMS) x * day / 100.0 else x * 100.0 / day).toDouble(),
                        )
                    }
                }
                mode = new
            }
            for (m in Meal.entries) {
                NumberField(
                    texts.getValue(m).value, { texts.getValue(m).value = it }, m.title, "field:split:${m.name}", Modifier,
                    suffix = if (mode == io.github.randomtools3377.fitness.core.MacroMode.PERCENT) "%" else "kcal",
                )
            }
            val problem = typed().problem(day ?: 0)
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Protein, carbs and fat follow the same split. Going over in one meal doesn't take from the others.", style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.Button(onClick = { vm.setMealSplit(typed()) }, enabled = problem == null && day != null, modifier = Modifier.testTag("save-meal-split")) { Text("Save") }
        }
    }
}

/** Daily targets for fibre (at least) and sugar, saturates and salt (at most), each optional (0.7.0). */
@Composable
fun ExtraTargetsSection(vm: MainViewModel, settings: AppSettings) {
    val t = settings.extraTargets
    fun text(x: Double?) = x?.let { io.github.randomtools3377.fitness.core.formatAmount(it) }.orEmpty()
    var fibre by remember(t) { mutableStateOf(text(t.fibre)) }
    var sugar by remember(t) { mutableStateOf(text(t.sugar)) }
    var satFat by remember(t) { mutableStateOf(text(t.satFat)) }
    var salt by remember(t) { mutableStateOf(text(t.salt)) }
    Section("Fibre, sugar, saturates and salt") {
        Text(
            "Shown on the day when a food gives them. Daily targets are optional: leave a box empty for none.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(fibre, { fibre = it }, "Fibre, at least", "target:fibre", Modifier.weight(1f), suffix = "g")
            NumberField(sugar, { sugar = it }, "Sugar, at most", "target:sugar", Modifier.weight(1f), suffix = "g")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(satFat, { satFat = it }, "Saturates, at most", "target:satfat", Modifier.weight(1f), suffix = "g")
            NumberField(salt, { salt = it }, "Salt, at most", "target:salt", Modifier.weight(1f), suffix = "g")
        }
        OutlinedButton(
            onClick = {
                fun parse(s: String) = io.github.randomtools3377.fitness.core.Units.parse(s)
                vm.setExtraTargets(io.github.randomtools3377.fitness.core.ExtraTargets(parse(fibre), parse(sugar), parse(satFat), parse(salt)))
            },
            modifier = Modifier.testTag("save-extra-targets"),
        ) { Text("Save") }
    }
}

/** The bar and plates for the plate calculator, in the weight unit in use (0.9.0). */
@Composable
fun PlatesRow(vm: MainViewModel, settings: AppSettings) {
    val p = settings.plates
    val lb = (settings.units?.weight ?: io.github.randomtools3377.fitness.core.UnitSettings.forRegion(java.util.Locale.getDefault().country).weight) ==
        io.github.randomtools3377.fitness.core.WeightUnit.LB
    val unit = if (lb) "lb" else "kg"
    fun list(xs: List<Double>) = xs.joinToString(", ") { io.github.randomtools3377.fitness.core.formatAmount(it) }
    var bar by remember(p, lb) { mutableStateOf(io.github.randomtools3377.fitness.core.formatAmount(if (lb) p.barLb else p.barKg)) }
    var plates by remember(p, lb) { mutableStateOf(list(if (lb) p.platesLb else p.platesKg)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Plate calculator: your bar and plates ($unit)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(bar, { bar = it }, "Bar", "field:bar", Modifier.width(100.dp), suffix = unit)
            OutlinedTextField(
                value = plates, onValueChange = { plates = it }, label = { Text("Plates, like 20, 10, 5") }, singleLine = true,
                modifier = Modifier.weight(1f).testTag("field:plates"),
            )
        }
        TextButton(
            onClick = {
                val b = io.github.randomtools3377.fitness.core.Units.parse(bar)
                val ps = plates.split(',', ';', ' ').mapNotNull { io.github.randomtools3377.fitness.core.Units.parse(it.trim()) }
                vm.setPlates(if (lb) p.copy(barLb = b ?: -1.0, platesLb = ps) else p.copy(barKg = b ?: -1.0, platesKg = ps), b, ps)
            },
            modifier = Modifier.testTag("save-plates"),
        ) { Text("Save the bar and plates") }
    }
}

/** Light or dark, and the accent colour (0.10.0). */
@Composable
fun ThemeSection(vm: MainViewModel, settings: AppSettings) {
    val t = settings.theme
    Section("Look") {
        Choices(io.github.randomtools3377.fitness.core.ThemeMode.entries, t.mode, { it.title }, { "theme:${it.name}" }) { vm.setTheme(t.copy(mode = it)) }
        Text("Colour", style = MaterialTheme.typography.labelLarge)
        val accents = io.github.randomtools3377.fitness.core.Accent.entries.filter {
            it != io.github.randomtools3377.fitness.core.Accent.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
        Choices(accents, t.accent, { it.title }, { "accent:${it.name}" }) { vm.setTheme(t.copy(accent = it)) }
    }
}

/** Steps from Health Connect, which works offline (0.10.0). */
@Composable
fun StepsSection(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(io.github.randomtools3377.fitness.steps.HealthSteps.PERMISSIONS)) {
            vm.setStepsOn(true)
        } else {
            vm.message.value = "Steps weren't allowed. You can allow them later in Health Connect."
        }
    }
    Section("Steps") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count steps from the phone")
                Text(
                    "Read from Health Connect, on the phone. Steps add about 35 kcal per 1,000 at 70 kg to the day; " +
                        "steps during a logged workout aren't counted twice. They follow the half-count choice above.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = settings.stepsOn,
                onCheckedChange = { on ->
                    if (!on) {
                        vm.setStepsOn(false)
                    } else if (!io.github.randomtools3377.fitness.steps.HealthSteps.available(context)) {
                        vm.message.value = "Health Connect isn't on this phone. On Android 13 and older, install the Health Connect app first."
                    } else {
                        ask.launch(io.github.randomtools3377.fitness.steps.HealthSteps.PERMISSIONS)
                    }
                },
                modifier = Modifier.testTag("steps-on"),
            )
        }
    }
}
