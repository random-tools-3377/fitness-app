package io.github.randomtools3377.fitness.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Plan
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.formatAmount
import io.github.randomtools3377.fitness.core.total
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The Plan tab: meals planned ahead, the shopping list, and recipes. */
@Composable
fun PlanScreen(vm: MainViewModel, modifier: Modifier, open: (Route) -> Unit) {
    val part by vm.planPart.collectAsState()
    // Only the parts that are on (0.15.0).
    val parts = buildList {
        if (on(io.github.randomtools3377.fitness.core.Feature.PLANNING)) { add("meals"); add("shopping"); add("recipes") }
        if (on(io.github.randomtools3377.fitness.core.Feature.FASTING)) add("fasting")
    }
    androidx.compose.runtime.LaunchedEffect(parts) { if (part !in parts && parts.isNotEmpty()) vm.planPart.value = parts.first() }
    Column(modifier.fillMaxSize()) {
        if (parts.size > 1) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Choices(
                parts, part,
                { when (it) { "meals" -> "Meals"; "shopping" -> "Shopping list"; "recipes" -> "Recipes"; else -> "Fasting" } },
                { "plan:$it" },
            ) { vm.planPart.value = it }
        }
        when (part) {
            "meals" -> PlanMeals(vm, open)
            "shopping" -> ShoppingList(vm)
            "recipes" -> RecipeList(vm, open)
            else -> FastingPart(vm)
        }
    }
}

@Composable
private fun PlanMeals(vm: MainViewModel, open: (Route) -> Unit) {
    val from by vm.planDay.collectAsState()
    val week by vm.planWeek.collectAsState()
    val settings by vm.settings.collectAsState()
    val targetFor by vm.targetFor.collectAsState()
    val units by vm.units.collectAsState()
    val today = vm.today()
    LazyColumn(Modifier.fillMaxSize().testTag("plan-list")) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.planDay.value = from.minus(DatePeriod(days = 7)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Week before") }
                Text("From ${dayName(from, today)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.planDay.value = from.plus(DatePeriod(days = 7)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Week after") }
            }
            Text(
                "Plan foods, saved meals and recipes for the days ahead. On the day, tick a planned item to log it as eaten; " +
                    "unticked ones don't count.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.copyPlan(from, 7, 7) }, modifier = Modifier.testTag("copy-week")) { Text("Copy this week to the next") }
            }
        }
        for (i in 0 until 7) {
            val day = from.plus(DatePeriod(days = i))
            val items = week.filter { it.day == day }
            item(key = day.toString()) {
                val planned = Plan.planned(items)
                val eaten = items.filter { !it.planned }.map { it.nutrients }.total()
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dayName(day, today), style = MaterialTheme.typography.titleMedium)
                            val target = targetFor(day)
                            Text(
                                "Planned ${energy(planned.kcal, units)}" +
                                    (if (eaten.kcal > 0) " · eaten ${energy(eaten.kcal, units)}" else "") +
                                    (target?.let { " · target ${energy(it.toDouble(), units)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("planned:$day"),
                            )
                            if (planned.kcal > 0) Text(macroLine(planned), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.copyPlan(day, 1, 1) }) { Text("Copy to next day") }
                    }
                    for (meal in Meal.entries) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(meal.title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { open(Route.AddFood(meal, mode = AddMode.PLAN, planDay = day)) }, modifier = Modifier.testTag("plan-add:$day:${meal.name}")) {
                                Icon(Icons.Filled.Add, contentDescription = "Plan ${meal.title}")
                            }
                        }
                        for (e in items.filter { it.meal == meal }) PlannedRow(vm, e, units)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A planned item: tick it to log it as eaten, or take it off the plan. */
@Composable
fun PlannedRow(vm: MainViewModel, e: DiaryEntry, units: io.github.randomtools3377.fitness.core.UnitSettings) {
    Row(Modifier.fillMaxWidth().testTag("planned-item:${e.name}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.name, color = if (e.planned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text("${e.describeAmount()} · ${energy(e.nutrients.kcal, units)}${if (e.planned) " · planned" else " · eaten"}", style = MaterialTheme.typography.bodySmall)
        }
        if (e.planned) {
            IconButton(onClick = { vm.eatPlanned(e) }, modifier = Modifier.testTag("eat:${e.name}")) { Icon(Icons.Filled.Check, contentDescription = "Eaten") }
            IconButton(onClick = { vm.deleteEntry(e) }) { Icon(Icons.Filled.Close, contentDescription = "Take off the plan") }
        }
    }
}

@Composable
private fun ShoppingList(vm: MainViewModel) {
    val items by vm.shopping.collectAsState()
    val units by vm.units.collectAsState()
    val today = vm.today()
    var from by remember { mutableStateOf(today) }
    var days by remember { mutableStateOf(7) }
    var own by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().testTag("shopping-list")) {
        item {
            Section("Make a list") {
                Text("From the meals planned for:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { from = from.minus(DatePeriod(days = 1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier") }
                    Text("${dayName(from, today)}, for", Modifier.weight(1f))
                    IconButton(onClick = { from = from.plus(DatePeriod(days = 1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later") }
                }
                Choices(listOf(1, 3, 7, 14), days, { if (it == 1) "1 day" else "$it days" }, { "shop-days:$it" }) { days = it }
                Button(onClick = { vm.makeShoppingList(from, from.plus(DatePeriod(days = days - 1))) }, modifier = Modifier.testTag("make-list")) {
                    Text("Make the list")
                }
                Text(
                    "The same food is added up across the days, and recipes are broken into their foods. Making a new list " +
                        "clears what you ticked; your own items stay.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(items, key = { it.id }) { i ->
            Row(Modifier.fillMaxWidth().clickable { vm.tickShopping(i) }.padding(horizontal = 16.dp, vertical = 2.dp).testTag("shop:${i.name}"), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i.ticked, onCheckedChange = { vm.tickShopping(i) })
                Text(
                    i.name, Modifier.weight(1f),
                    textDecoration = if (i.ticked) TextDecoration.LineThrough else null,
                    color = if (i.ticked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                i.grams?.let { g ->
                    Text(
                        if (i.unit.name == "MILLILITRE") Units.volume(g, units.volume) else "${formatAmount(Math.round(g).toDouble())} g",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (i.own) IconButton(onClick = { vm.deleteShopping(i) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(own, { own = it }, label = { Text("Add your own, like washing-up liquid") }, singleLine = true, modifier = Modifier.weight(1f).testTag("field:own-item"))
                IconButton(onClick = { vm.addOwnShopping(own); own = "" }, modifier = Modifier.testTag("add-own-item")) { Icon(Icons.Filled.Add, contentDescription = "Add") }
            }
        }
    }
}

@Composable
private fun RecipeList(vm: MainViewModel, open: (Route) -> Unit) {
    val recipes by vm.recipes.collectAsState()
    val units by vm.units.collectAsState()
    LazyColumn(Modifier.fillMaxSize().testTag("recipe-list")) {
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "A recipe adds up its foods and splits them into servings. Log or plan it like any food.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.editRecipe(null); open(Route.Recipe) }, modifier = Modifier.testTag("new-recipe")) { Text("New recipe") }
            }
        }
        items(recipes, key = { it.id }) { r ->
            Column(Modifier.fillMaxWidth().clickable { vm.editRecipe(r); open(Route.Recipe) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("recipe:${r.name}")) {
                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${formatAmount(r.servings)} servings · ${energy(r.perServing.kcal, units)} a serving · ${macroLine(r.perServing)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
    }
}

/** Writing or changing a recipe: its foods, servings, the weighed dish if known, and notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(vm: MainViewModel, modifier: Modifier, back: () -> Unit, addFood: () -> Unit) {
    val draft by vm.recipeDraft.collectAsState()
    val units by vm.units.collectAsState()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    val r = draft
    if (r == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { back() }
        return
    }
    var servings by remember(r.id) { mutableStateOf(formatAmount(r.servings)) }
    var cooked by remember(r.id) { mutableStateOf(r.cookedGrams?.let { formatAmount(it) } ?: "") }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (r.id == 0L) "New recipe" else "Recipe") },
            navigationIcon = { IconButton(onClick = { vm.recipeDraft.value = null; back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(onClick = { scope.launch { if (vm.saveRecipe()) back() } }, modifier = Modifier.testTag("save-recipe")) { Text("Save") }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(r.name, { vm.recipeDraft.value = r.copy(name = it) }, "Name", "field:recipe-name")
            NumberField(servings, { t -> servings = t; Units.parse(t)?.let { vm.recipeDraft.value = r.copy(servings = it) } }, "Servings it makes", "field:servings", Modifier.fillMaxWidth())
            Text("Foods", style = MaterialTheme.typography.titleMedium)
            r.items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name)
                        Text("${item.amount.describe(item.unit)} · ${energy(item.nutrients.kcal, units)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.recipeDraft.value = r.copy(items = r.items.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove")
                    }
                }
            }
            OutlinedButton(onClick = addFood, modifier = Modifier.testTag("recipe-add-food")) { Text("Add a food") }
            HorizontalDivider()
            Text(
                "In all: ${energy(r.total.kcal, units)}. A serving: ${energy(r.perServing.kcal, units)}, ${macroLine(r.perServing)}",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("recipe-per-serving"),
            )
            NumberField(
                cooked, { t -> cooked = t; vm.recipeDraft.value = r.copy(cookedGrams = Units.parse(t)?.takeIf { it > 0 }) },
                "Weight of the finished dish (optional)", "field:cooked", Modifier.fillMaxWidth(), suffix = "g",
            )
            Text(
                "Weigh the cooked dish once to log it by weight, which allows for water lost or gained in cooking.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = r.notes, onValueChange = { vm.recipeDraft.value = r.copy(notes = it) },
                label = { Text("Method (optional)") }, modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Text("Changing a recipe applies from now on; days already logged keep what they were.", style = MaterialTheme.typography.bodySmall)
            if (r.id != 0L) TextButton(onClick = { deleting = true }) { Text("Delete this recipe") }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (deleting) {
        ConfirmDialog("Delete ${r.name}?", "What you already logged keeps its numbers.", "Delete", { vm.deleteRecipe(r); deleting = false; vm.recipeDraft.value = null; back() }, { deleting = false })
    }
}
