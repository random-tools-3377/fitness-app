package io.github.randomtools3377.fitness.ui

import kotlin.math.roundToInt
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.macroGrams
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.Water
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** "Today", "Yesterday", "Tomorrow" or "Mon 9 Nov 2026". */
fun dayName(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minus(DatePeriod(days = 1)) -> "Yesterday"
    today.plus(DatePeriod(days = 1)) -> "Tomorrow"
    else -> {
        val wd = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val m = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "$wd ${d.dayOfMonth} $m ${d.year}"
    }
}

@Composable
fun DiaryScreen(vm: MainViewModel, bvm: BodyViewModel, modifier: Modifier, open: (Route) -> Unit) {
    val day by vm.day.collectAsState()
    val entries by vm.entries.collectAsState()
    val totals by vm.totals.collectAsState()
    val water by vm.water.collectAsState()
    val settings by vm.settings.collectAsState()
    val units by vm.units.collectAsState()
    val workoutBurned by vm.dayBurned.collectAsState()
    val steps by vm.daySteps.collectAsState()
    val stepsKcal by vm.dayStepsKcal.collectAsState()
    val burned = workoutBurned + stepsKcal
    val targetFor by vm.targetFor.collectAsState()
    val why by vm.dayTargetWhy.collectAsState()
    // The day's own target (0.11.0): by weekday, on a training day, or in a diet break.
    val target = targetFor(day)
    val features = LocalFeatures.current
    val dayWorkouts by vm.dayWorkouts.collectAsState()
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var savingMeal by remember { mutableStateOf<Meal?>(null) }
    var dayMenu by remember { mutableStateOf(false) }
    val today = vm.today()

    LazyColumn(modifier.fillMaxSize().testTag("diary-list")) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.day.value = day.minus(DatePeriod(days = 1)) }, modifier = Modifier.testTag("day-prev")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Day before")
                }
                Text(
                    dayName(day, today),
                    Modifier.weight(1f).clickable { vm.day.value = today }.testTag("day-name"),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { vm.day.value = day.plus(DatePeriod(days = 1)) }, modifier = Modifier.testTag("day-next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Day after")
                }
                IconButton(onClick = { open(Route.Settings) }, modifier = Modifier.testTag("open-settings")) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                Box {
                    IconButton(onClick = { dayMenu = true }, modifier = Modifier.testTag("day-menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy a whole day from another day") },
                            onClick = { dayMenu = false; open(Route.Copy(null)) },
                            modifier = Modifier.testTag("copy-day-from"),
                        )
                        if (day != today) {
                            DropdownMenuItem(text = { Text("Back to today") }, onClick = { dayMenu = false; vm.day.value = today })
                        }
                    }
                }
            }
        }

        item {
            SummaryCard(
                totals.total, burned, settings.burnedHalf, target, settings.macroGrams(target ?: 0, vm.latestKg()), settings.extraTargets, units, open, steps, stepsKcal,
                why = why, drinkGrams = settings.drinkGrams ?: io.github.randomtools3377.fitness.core.standardDrinkGrams(java.util.Locale.getDefault().country),
            )
        }
        // Vitamins and minerals from built-in foods (0.11.0).
        if (io.github.randomtools3377.fitness.core.Feature.VITAMINS in features && totals.total.micros.keys.any { k -> io.github.randomtools3377.fitness.core.Micro.VITAMINS_AND_MINERALS.any { it.key == k } }) {
            item(key = "micros") { MicrosCard(totals.total) }
        }

        if (dayWorkouts.isNotEmpty() && io.github.randomtools3377.fitness.core.Feature.WORKOUTS in features) {
            item {
                Section("Workouts") {
                    for (w in dayWorkouts) {
                        Row(Modifier.fillMaxWidth().testTag("day-workout:${w.name}"), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.name, Modifier.weight(1f))
                            Text(burnedText(w.burnedKcal * (if (settings.burnedHalf) 0.5 else 1.0), units), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        if (io.github.randomtools3377.fitness.core.Feature.WATER in features) item {
            val ml = Water.total(water, day)
            val w = settings.water
            Section("Water") {
                LabelValue(
                    "Drunk",
                    "${Units.volume(ml.toDouble(), units.volume)} of ${Units.volume(w.targetMl.toDouble(), units.volume)}",
                    tag = "water-total",
                )
                LinearProgressIndicator(progress = { (ml.toFloat() / w.targetMl.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.addWater(w.glassMl) }, modifier = Modifier.testTag("add-glass")) {
                        Text("+ Glass ${Units.volume(w.glassMl.toDouble(), units.volume)}")
                    }
                    OutlinedButton(onClick = { vm.addWater(w.bottleMl) }, modifier = Modifier.testTag("add-bottle")) {
                        Text("+ Bottle ${Units.volume(w.bottleMl.toDouble(), units.volume)}")
                    }
                }
                if (water.isNotEmpty()) {
                    TextButton(onClick = { vm.undoWater() }, modifier = Modifier.testTag("undo-water")) { Text("Take back the last one") }
                }
            }
        }

        for (meal in Meal.entries) {
            item(key = meal.name) {
                val mealTargets = if (settings.mealSplit.enabled && target != null)
                    settings.mealSplit.targets(settings.macroGrams(target, vm.latestKg())) else null
                MealCard(
                    meal = meal,
                    target = mealTargets?.get(meal),
                    entries = entries.filter { it.meal == meal },
                    total = totals.byMeal.getValue(meal),
                    units = units,
                    add = { open(Route.AddFood(meal)) },
                    copy = { open(Route.Copy(meal)) },
                    sameAsYesterday = { vm.sameAsYesterday(meal) },
                    save = { savingMeal = meal },
                    edit = { editing = it },
                    eat = { vm.eatPlanned(it) },
                )
            }
        }
        // Habits and sleep and mood for the day shown (0.8.0).
        if (io.github.randomtools3377.fitness.core.Feature.HABITS in features) item(key = "habits") { HabitsCard(bvm, day) }
        if (io.github.randomtools3377.fitness.core.Feature.SUPPLEMENTS in features) item(key = "supplements") {
            HabitsCard(bvm, day, io.github.randomtools3377.fitness.core.HabitKind.SUPPLEMENT, "Supplements and medication")
        }
        if (io.github.randomtools3377.fitness.core.Feature.SLEEP_MOOD in features) item(key = "sleep") { SleepMoodCard(bvm, day) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { e -> EditEntryDialog(vm, e, units, onDone = { editing = null }) }

    savingMeal?.let { meal ->
        var name by remember(meal) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingMeal = null },
            title = { Text("Save ${meal.title.lowercase()} as a meal") },
            text = {
                Column {
                    Text("Its foods and amounts are saved together, to add again in one go.")
                    TextInput(name, { name = it }, "Name, like \"Usual breakfast\"", "field:meal-name")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.saveMeal(name, meal); savingMeal = null },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("confirm-save-meal"),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { savingMeal = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryCard(
    eaten: Nutrients,
    burned: Double,
    half: Boolean,
    targetKcal: Int?,
    macroTargets: Nutrients,
    extraTargets: io.github.randomtools3377.fitness.core.ExtraTargets,
    units: UnitSettings,
    open: (Route) -> Unit,
    steps: Long? = null,
    stepsKcal: Double = 0.0,
    why: String? = null,
    drinkGrams: Double = 10.0,
) {
    Section("Calories") {
        LabelValue("Eaten", energy(eaten.kcal, units), tag = "day-kcal")
        val inWorkouts = burned - stepsKcal
        if (inWorkouts > 0) LabelValue(if (half) "Burned in workouts (half)" else "Burned in workouts", energy(inWorkouts, units), tag = "day-burned")
        // Steps from Health Connect (0.10.0).
        if (steps != null) {
            LabelValue(
                "Steps",
                Units.grouped(steps.toInt()) + if (stepsKcal > 0) " · ${energy(stepsKcal, units)}" + (if (half) " (half)" else "") else "",
                tag = "day-steps",
            )
        }
        if (targetKcal == null) {
            Text("Set a daily target to see what is left.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { open(Route.Target) }, modifier = Modifier.testTag("set-target")) { Text("Set my target") }
        } else {
            val left = targetKcal + burned - eaten.kcal
            LabelValue("Target", energy(targetKcal.toDouble(), units), tag = "day-target")
            why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("day-target-why")) }
            LabelValue(
                if (left >= 0) "Left" else "Over",
                energy(kotlin.math.abs(left), units),
                tag = "day-left",
            )
            LinearProgressIndicator(progress = { (eaten.kcal / (targetKcal + burned)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider()
        MacroRow("Protein", eaten.protein, if (targetKcal != null) macroTargets.protein else null)
        MacroRow("Carbs", eaten.carbs, if (targetKcal != null) macroTargets.carbs else null)
        MacroRow("Fat", eaten.fat, if (targetKcal != null) macroTargets.fat else null)
        // Fibre, sugar, saturates and salt (0.7.0): shown when a food gave them or a target is set.
        if (on(io.github.randomtools3377.fitness.core.Feature.EXTRAS)) {
            ExtraRow("Fibre", eaten.fibre, extraTargets.fibre, atLeast = true)
            ExtraRow("Sugar", eaten.sugar, extraTargets.sugar, atLeast = false)
            ExtraRow("Saturates", eaten.satFat, extraTargets.satFat, atLeast = false)
            ExtraRow("Salt", eaten.salt, extraTargets.salt, atLeast = false)
        }
        // Alcohol and caffeine (0.11.0).
        val alcohol = eaten.micro(io.github.randomtools3377.fitness.core.Micro.ALCOHOL)
        if (on(io.github.randomtools3377.fitness.core.Feature.ALCOHOL_CAFFEINE) && (alcohol != null && alcohol > 0 || extraTargets.alcoholDrinks != null)) {
            val drinks = io.github.randomtools3377.fitness.core.Micros.drinks(alcohol ?: 0.0, drinkGrams)
            val limit = extraTargets.alcoholDrinks
            LabelValue(
                "Alcohol",
                "${io.github.randomtools3377.fitness.core.formatAmount(drinks)} standard drinks" + (limit?.let { " of at most ${io.github.randomtools3377.fitness.core.formatAmount(it)}" } ?: ""),
                tag = "day-alcohol",
            )
            if (limit != null && drinks > limit) Text("Over the day's limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        val caffeine = eaten.micro(io.github.randomtools3377.fitness.core.Micro.CAFFEINE)
        if (on(io.github.randomtools3377.fitness.core.Feature.ALCOHOL_CAFFEINE) && (caffeine != null && caffeine > 0 || extraTargets.caffeineMg != null)) {
            val mg = (caffeine ?: 0.0).roundToInt()
            val limit = extraTargets.caffeineMg
            LabelValue("Caffeine", "$mg mg" + (limit?.let { " of at most ${it.roundToInt()} mg" } ?: ""), tag = "day-caffeine")
            if (limit != null && mg > limit) Text("Over the day's limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** The day's vitamins and minerals from built-in foods, as a share of the EU label reference values (0.11.0). */
@Composable
private fun MicrosCard(total: Nutrients) {
    var open by remember { mutableStateOf(false) }
    Section("Vitamins and minerals") {
        Text(
            "From built-in foods only, as a share of the reference values on EU labels. These are a guide, not your own needs.",
            style = MaterialTheme.typography.bodySmall,
        )
        val shown = io.github.randomtools3377.fitness.core.Micro.VITAMINS_AND_MINERALS.filter { total.micro(it) != null }
        for (m in if (open) shown else shown.take(4)) {
            val x = total.micro(m)!!
            val pct = io.github.randomtools3377.fitness.core.Micros.percent(m, x) ?: 0
            Column {
                LabelValue(m.title, "${io.github.randomtools3377.fitness.core.formatAmount(Math.round(x * 10) / 10.0)} ${m.unit} · $pct%", tag = "micro:${m.key}")
                LinearProgressIndicator(progress = { (pct / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
        if (shown.size > 4) {
            TextButton(onClick = { open = !open }, modifier = Modifier.testTag("micros-more")) { Text(if (open) "Show fewer" else "Show all ${shown.size}") }
        }
    }
}

@Composable
private fun ExtraRow(name: String, eaten: Double?, target: Double?, atLeast: Boolean) {
    if (eaten == null && target == null) return
    val g = eaten ?: 0.0
    val shown = if (name == "Salt") "${io.github.randomtools3377.fitness.core.formatAmount(Math.round(g * 100) / 100.0)} g" else Units.grams(g)
    val text = when {
        target == null -> shown
        atLeast -> "$shown of at least ${Units.grams(target)}"
        else -> "$shown of at most ${Units.grams(target)}"
    }
    LabelValue(name, text, tag = "day-${name.lowercase()}")
    if (target != null && !atLeast && g > target) {
        Text("Over the day's limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MacroRow(name: String, eaten: Double, target: Double?) {
    Column {
        LabelValue(name, if (target == null) Units.grams(eaten) else "${Units.grams(eaten)} of ${Units.grams(target)}", tag = "day-${name.lowercase()}")
        if (target != null && target > 0) {
            LinearProgressIndicator(progress = { (eaten / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    target: Nutrients?,
    entries: List<DiaryEntry>,
    total: Nutrients,
    units: UnitSettings,
    add: () -> Unit,
    copy: () -> Unit,
    sameAsYesterday: () -> Unit,
    save: () -> Unit,
    edit: (DiaryEntry) -> Unit,
    eat: (DiaryEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    energy(total.kcal, units),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("meal-kcal:${meal.name}"),
                )
                if (target != null) {
                    // Each meal's own share; going over here doesn't take from the others.
                    val left = target.kcal - total.kcal
                    Text(
                        (if (left >= 0) "${energy(left, units)} left of " else "${energy(-left, units)} over ") + energy(target.kcal, units),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (left >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("meal-left:${meal.name}"),
                    )
                }
            }
            IconButton(onClick = add, modifier = Modifier.testTag("add:${meal.name}")) {
                Icon(Icons.Filled.Add, contentDescription = "Add to ${meal.title}")
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("meal-menu:${meal.name}")) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Same as yesterday") },
                        onClick = { menu = false; sameAsYesterday() },
                        modifier = Modifier.testTag("same-as-yesterday-menu:${meal.name}"),
                    )
                    DropdownMenuItem(
                        text = { Text("Copy from another day") },
                        onClick = { menu = false; copy() },
                        modifier = Modifier.testTag("copy-meal-from:${meal.name}"),
                    )
                    if (entries.any { !it.planned }) {
                        DropdownMenuItem(
                            text = { Text("Save as a meal") },
                            onClick = { menu = false; save() },
                            modifier = Modifier.testTag("save-meal:${meal.name}"),
                        )
                    }
                }
            }
        }
        for (e in entries.filter { it.planned && on(io.github.randomtools3377.fitness.core.Feature.PLANNING) }) {
            // Planned ahead: doesn't count until ticked as eaten.
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("planned:${e.name}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Planned · ${e.describeAmount()} · ${energy(e.nutrients.kcal, units)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { eat(e) }, modifier = Modifier.testTag("eat:${e.name}")) { Text("Eaten") }
            }
        }
        for (e in entries.filter { !it.planned }) {
            Row(
                Modifier.fillMaxWidth().clickable { edit(e) }.padding(vertical = 6.dp).testTag("entry:${e.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        e.describeAmount() + if (e.isQuickAdd) "" else " · " + macroLine(e.nutrients),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(energy(e.nutrients.kcal, units), fontWeight = FontWeight.Medium)
            }
        }
        if (entries.any { !it.planned }) {
            Text(macroLine(total), style = MaterialTheme.typography.bodySmall)
            if (on(io.github.randomtools3377.fitness.core.Feature.EXTRAS)) extrasLine(total).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        } else {
            TextButton(onClick = sameAsYesterday, modifier = Modifier.testTag("same-as-yesterday:${meal.name}")) { Text("Same as yesterday") }
        }
    }
}

/** Changing or removing an entry: its amount, or for a quick add, its calories. */
@Composable
private fun EditEntryDialog(vm: MainViewModel, entry: DiaryEntry, units: UnitSettings, onDone: () -> Unit) {
    var food by remember(entry.id) { mutableStateOf<Food?>(null) }
    var loaded by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        food = vm.foodOf(entry)
        loaded = true
    }
    if (!loaded) return
    val f = food
    if (entry.isQuickAdd || f == null) {
        QuickAddDialog(
            title = if (entry.isQuickAdd) "Quick add" else entry.name,
            units = units,
            initialKcal = entry.nutrients.kcal,
            initialName = entry.name,
            onConfirm = { kcal, name -> vm.changeQuickAdd(entry, kcal, name); onDone() },
            onDelete = { vm.deleteEntry(entry); onDone() },
            onMove = { m -> vm.moveEntry(entry, m); onDone() },
            current = entry.meal,
            onDismiss = onDone,
        )
    } else {
        AmountDialog(
            food = f,
            units = units,
            initial = entry.amount,
            confirmText = "Save",
            onConfirm = { a -> vm.changeEntry(entry, f, a); onDone() },
            onDelete = { vm.deleteEntry(entry); onDone() },
            onMove = { m -> vm.moveEntry(entry, m); onDone() },
            current = entry.meal,
            onDismiss = onDone,
        )
    }
}
