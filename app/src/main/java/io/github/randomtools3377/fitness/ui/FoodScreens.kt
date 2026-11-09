package io.github.randomtools3377.fitness.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.BaseUnit
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.EnergyUnit
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Label
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.Portion
import io.github.randomtools3377.fitness.core.SavedMeal
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.formatAmount
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private enum class Basis { PER_100, PER_SERVING }

private class PortionInput(label: String, amount: String) {
    var label by mutableStateOf(label)
    var amount by mutableStateOf(amount)
}

/**
 * Typing in a food from its label. The numbers can be per 100 g (or 100 ml) or per serving, in
 * kJ or kcal, as the label gives them. Carbs are without fibre, as on EU labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodFormScreen(vm: MainViewModel, editId: Long?, modifier: Modifier, back: () -> Unit, saved: (Food) -> Unit) {
    val units by vm.units.collectAsState()
    val scope = rememberCoroutineScope()
    var loadedId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(BaseUnit.GRAM) }
    var basis by remember { mutableStateOf(Basis.PER_100) }
    var servingSize by remember { mutableStateOf("") }
    var servingLabel by remember { mutableStateOf("1 serving") }
    var energyUnit by remember { mutableStateOf(units.energy) }
    var energyText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    val portions = remember { mutableStateListOf<PortionInput>() }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(editId) {
        if (editId != null && loadedId != editId) {
            vm.food(editId)?.let { f ->
                name = f.name
                brand = f.brand
                unit = f.unit
                energyText = formatAmount(Units.kcalToShown(f.per100.kcal, energyUnit))
                proteinText = formatAmount(f.per100.protein)
                carbsText = formatAmount(f.per100.carbs)
                fatText = formatAmount(f.per100.fat)
                portions.clear()
                f.portions.forEach { portions.add(PortionInput(it.label, formatAmount(it.amount))) }
            }
            loadedId = editId
        }
    }

    fun save() {
        val energy = Units.parse(energyText)
        val typed = Nutrients(
            kcal = energy?.let { Units.shownToKcal(it, energyUnit) } ?: -1.0,
            protein = Units.parse(proteinText) ?: 0.0,
            carbs = Units.parse(carbsText) ?: 0.0,
            fat = Units.parse(fatText) ?: 0.0,
        )
        val out = mutableListOf<String>()
        if (energy == null) out += "Type the energy (calories) from the label."
        val size = Units.parse(servingSize)
        if (basis == Basis.PER_SERVING && (size == null || size <= 0)) out += "Type the serving size in ${unit.short}."
        val list = portions.mapNotNull { p ->
            val a = Units.parse(p.amount)
            if (p.label.isBlank() && p.amount.isBlank()) null
            else if (p.label.isBlank() || a == null || a <= 0) { out += "Each portion needs a name and a size in ${unit.short}."; null }
            else Portion(p.label.trim(), a)
        }.toMutableList()
        if (out.isNotEmpty()) { problems = out; return }
        val per100 = if (basis == Basis.PER_100) typed else Label.per100(typed, size!!)
        val p = Label.problems(name, per100, unit)
        if (p.isNotEmpty()) { problems = p; return }
        if (basis == Basis.PER_SERVING) {
            val label = servingLabel.trim().ifBlank { "1 serving" }
            if (list.none { it.label == label }) list.add(0, Portion(label, size!!))
        }
        val food = Food(id = editId ?: 0, name = name.trim(), brand = brand.trim(), unit = unit, per100 = per100, portions = list)
        scope.launch { saved(vm.saveFood(food)) }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (editId == null) "A food from its label" else "Change food") },
            navigationIcon = {
                IconButton(onClick = back, modifier = Modifier.testTag("back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("food-form"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextInput(name, { name = it }, "Name", "field:name")
            TextInput(brand, { brand = it }, "Brand (optional)", "field:brand")
            Text("Measured in", style = MaterialTheme.typography.labelLarge)
            Choices(BaseUnit.entries, unit, { if (it == BaseUnit.GRAM) "Grams (food)" else "Millilitres (drink)" }, { "base:${it.name}" }) { unit = it }
            Text("The label's numbers are", style = MaterialTheme.typography.labelLarge)
            Choices(Basis.entries, basis, { if (it == Basis.PER_100) "Per 100 ${unit.short}" else "Per serving" }, { "basis:${it.name}" }) { basis = it }
            if (basis == Basis.PER_SERVING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(servingSize, { servingSize = it }, "Serving size", "field:serving-size", Modifier.weight(1f), suffix = unit.short)
                    androidx.compose.material3.OutlinedTextField(
                        value = servingLabel,
                        onValueChange = { servingLabel = it },
                        label = { Text("Called") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field:serving-label"),
                    )
                }
            }
            Text("Energy in", style = MaterialTheme.typography.labelLarge)
            Choices(EnergyUnit.entries, energyUnit, { it.short }, { "energy:${it.name}" }) { new ->
                // Keep the same energy when switching, so a number already typed isn't lost.
                Units.parse(energyText)?.let { x -> energyText = formatAmount(Units.kcalToShown(Units.shownToKcal(x, energyUnit), new)) }
                energyUnit = new
            }
            NumberField(energyText, { energyText = it }, "Energy", "field:kcal", Modifier.fillMaxWidth(), suffix = energyUnit.short)
            NumberField(proteinText, { proteinText = it }, "Protein", "field:protein", Modifier.fillMaxWidth(), suffix = "g")
            NumberField(carbsText, { carbsText = it }, "Carbohydrate", "field:carbs", Modifier.fillMaxWidth(), suffix = "g")
            Text(
                "Carbs are counted without fibre, as on EU labels. On a US label, take the fibre off the total carbohydrate.",
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField(fatText, { fatText = it }, "Fat", "field:fat", Modifier.fillMaxWidth(), suffix = "g")

            Text("Its own portions (optional), like \"1 slice\" of 30 g", style = MaterialTheme.typography.labelLarge)
            portions.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = p.label, onValueChange = { p.label = it }, label = { Text("Portion") }, singleLine = true,
                        modifier = Modifier.weight(1.4f).testTag("field:portion-label-$i"),
                    )
                    NumberField(p.amount, { p.amount = it }, "Size", "field:portion-size-$i", Modifier.weight(1f), suffix = unit.short)
                    IconButton(onClick = { portions.removeAt(i) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove portion") }
                }
            }
            OutlinedButton(onClick = { portions.add(PortionInput("", "")) }, modifier = Modifier.testTag("add-portion")) { Text("Add a portion") }

            for (p in problems) Text(p, color = MaterialTheme.colorScheme.error)
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().testTag("save-food")) { Text("Save") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The Foods tab: the user's own foods and saved meals. */
@Composable
fun FoodsScreen(vm: MainViewModel, modifier: Modifier, open: (Route) -> Unit) {
    val mine by vm.foods.collectAsState()
    val saved by vm.savedMeals.collectAsState()
    val units by vm.units.collectAsState()
    var deleting by remember { mutableStateOf<Food?>(null) }
    var deletingMeal by remember { mutableStateOf<SavedMeal?>(null) }
    var renaming by remember { mutableStateOf<SavedMeal?>(null) }
    val pendingImport by vm.foodImport.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.previewFoodImport(uri)
    }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.saveFoodTemplate(uri)
    }

    LazyColumn(modifier.fillMaxSize().testTag("foods-list")) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Saved meals", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        }
        if (saved.isEmpty()) {
            item {
                Text(
                    "None yet. In the diary, a meal's ⋮ menu saves it, to add again in one go.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(saved, key = { "m${it.id}" }) { m ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("saved:${m.name}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${energy(m.nutrients.kcal, units)} · ${macroLine(m.nutrients)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { renaming = m }) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
                    IconButton(onClick = { deletingMeal = m }, modifier = Modifier.testTag("delete-saved:${m.name}")) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
                for (item in m.items) {
                    Text("• ${item.name}, ${item.amount.describe(item.unit)}: ${energy(item.nutrients.kcal, units)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your foods", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { open(Route.FoodForm()) }, modifier = Modifier.testTag("new-food-tab")) { Text("New food") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Have your own list? Import it from a spreadsheet saved as CSV. Start from the template, " +
                        "one food per row.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
                        modifier = Modifier.testTag("import-foods"),
                    ) { Text("Import a spreadsheet") }
                    TextButton(onClick = { templateLauncher.launch("fitness-foods-template.csv") }, modifier = Modifier.testTag("food-template")) {
                        Text("Save a template")
                    }
                }
            }
        }
        if (mine.isEmpty()) {
            item {
                Text(
                    "Foods you type in from a label are kept here, with their portions.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(mine, key = { "f${it.id}" }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { open(Route.FoodForm(editId = f.id)) }.padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("my-food:${f.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.displayName(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${energy(f.per100.kcal, units)} per 100 ${f.unit.short} · ${macroLine(f.per100)}" +
                            if (f.portions.isEmpty()) "" else " · " + f.portions.joinToString { it.label },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { deleting = f }, modifier = Modifier.testTag("delete-food:${f.name}")) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    deleting?.let { f ->
        ConfirmDialog(
            "Delete ${f.name}?",
            "It goes from your foods. What you have already logged stays in the diary.",
            "Delete",
            onConfirm = { vm.deleteFood(f); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    deletingMeal?.let { m ->
        ConfirmDialog(
            "Delete ${m.name}?",
            "The saved meal goes. What you have already logged stays in the diary.",
            "Delete",
            onConfirm = { vm.deleteSavedMeal(m); deletingMeal = null },
            onDismiss = { deletingMeal = null },
        )
    }
    pendingImport?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.foodImport.value = null },
            title = { Text("Import foods") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.summary(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("food-import-summary"))
                    if (r.foods.isNotEmpty()) {
                        Text(
                            r.foods.take(5).joinToString { it.displayName() } + if (r.foods.size > 5) ", and ${r.foods.size - 5} more" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (r.problems.isNotEmpty()) {
                        Text("Left out:", style = MaterialTheme.typography.labelLarge)
                        for (p in r.problems.take(12)) {
                            Text((if (p.line > 0) "Row ${p.line}: " else "") + p.reason, style = MaterialTheme.typography.bodySmall)
                        }
                        if (r.problems.size > 12) Text("…and ${r.problems.size - 12} more.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Nothing you already have is changed.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmFoodImport() }, enabled = r.foods.isNotEmpty(), modifier = Modifier.testTag("confirm-food-import")) {
                    Text(if (r.foods.size == 1) "Add 1 food" else "Add ${r.foods.size} foods")
                }
            },
            dismissButton = { TextButton(onClick = { vm.foodImport.value = null }) { Text("Cancel") } },
        )
    }
    renaming?.let { m ->
        var name by remember(m.id) { mutableStateOf(m.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { TextInput(name, { name = it }, "Name", "field:rename") },
            confirmButton = {
                TextButton(onClick = { vm.renameSavedMeal(m, name); renaming = null }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Copying from another day: one meal ([meal], into the same meal of the day shown), or any meal or
 * the whole day when [meal] is null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyScreen(vm: MainViewModel, meal: Meal?, modifier: Modifier, back: () -> Unit) {
    val target by vm.day.collectAsState()
    val units by vm.units.collectAsState()
    var from by remember { mutableStateOf(target.minus(DatePeriod(days = 1))) }
    var entries by remember { mutableStateOf<List<DiaryEntry>?>(null) }
    LaunchedEffect(from) {
        entries = null
        entries = vm.entriesOn(from)
    }
    val today = vm.today()

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (meal == null) "Copy from another day" else "Copy ${meal.title.lowercase()} from another day") },
            navigationIcon = {
                IconButton(onClick = back, modifier = Modifier.testTag("back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { from = from.minus(DatePeriod(days = 1)) }, modifier = Modifier.testTag("copy-prev")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Day before")
            }
            Text(
                "From: " + dayName(from, today),
                Modifier.weight(1f).testTag("copy-from-day"),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { from = from.plus(DatePeriod(days = 1)) }, modifier = Modifier.testTag("copy-next")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Day after")
            }
        }
        Text("To: " + dayName(target, today), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        val list = entries
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                list == null -> item { Text("…", Modifier.padding(16.dp)) }
                list.isEmpty() -> item { Text("Nothing was logged that day.", Modifier.padding(16.dp)) }
                else -> {
                    val meals = if (meal == null) Meal.entries else listOf(meal)
                    for (m in meals) {
                        val part = list.filter { it.meal == m }
                        if (part.isEmpty()) continue
                        item(key = m.name) {
                            Section(m.title) {
                                for (e in part) Text("${e.name}, ${e.describeAmount()}: ${energy(e.nutrients.kcal, units)}", style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { vm.copyFrom(from, m, meal); back() },
                                    modifier = Modifier.testTag("copy-meal:${m.name}"),
                                ) { Text("Copy ${m.title.lowercase()} (${energy(part.sumOf { it.nutrients.kcal }, units)})") }
                            }
                        }
                    }
                    if (meal == null) {
                        item {
                            Section {
                                Button(onClick = { vm.copyFrom(from, null, null); back() }, modifier = Modifier.fillMaxWidth().testTag("copy-day")) {
                                    Text("Copy the whole day (${energy(list.sumOf { it.nutrients.kcal }, units)})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
