package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.Amount
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.SavedMeal
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.formatAmount

/** Where a food comes from, in a few words. */
fun sourceOf(food: Food): String = when (food.builtInId?.firstOrNull()) {
    null -> "Your food"
    'C' -> "European (CIQUAL)"
    'P' -> "Packaged (Open Food Facts)"
    'R' -> "Your recipe"
    else -> "USDA"
}

/** What adding a food is for: the diary, the plan for a day ahead, or a recipe. */
enum class AddMode { LOG, PLAN, RECIPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    vm: MainViewModel,
    meal: Meal,
    openFoodKey: String?,
    modifier: Modifier,
    back: () -> Unit,
    newFood: (barcode: String?) -> Unit,
    mode: AddMode = AddMode.LOG,
    planDay: kotlinx.datetime.LocalDate? = null,
) {
    fun add(f: Food, a: Amount) = when (mode) {
        AddMode.LOG -> { vm.log(f, a, meal); Unit }
        AddMode.PLAN -> { vm.plan(f, a, planDay ?: vm.today(), meal); Unit }
        AddMode.RECIPE -> vm.addToRecipe(f, a)
    }
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val recent by vm.recent.collectAsState()
    val mine by vm.foods.collectAsState()
    val builtIns by vm.builtIns.collectAsState()
    val saved by vm.savedMeals.collectAsState()
    val units by vm.units.collectAsState()
    val european by vm.hasEuropeanFoods.collectAsState()
    var picked by remember { mutableStateOf<Food?>(null) }
    var quick by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scan = rememberScanner { code ->
        if (code != null) scope.launch {
            val f = vm.foodForBarcode(code)
            if (f != null) picked = f else notFound = code
        }
    }

    DisposableEffect(Unit) { onDispose { vm.query.value = "" } }

    // A food just typed in opens its amount straight away.
    LaunchedEffect(openFoodKey, mine) {
        if (openFoodKey != null && picked == null) {
            mine.firstOrNull { it.key == openFoodKey }?.let { picked = it }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when (mode) {
                        AddMode.LOG -> "Add to ${meal.title.lowercase()}"
                        AddMode.PLAN -> "Plan ${meal.title.lowercase()}, ${dayName(planDay ?: vm.today(), vm.today())}"
                        AddMode.RECIPE -> "Add to the recipe"
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = back, modifier = Modifier.testTag("back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            label = { Text("Search foods") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("search"),
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = scan, modifier = Modifier.testTag("scan")) { Text("Scan") }
            OutlinedButton(onClick = { newFood(null) }, modifier = Modifier.testTag("new-food")) { Text("From a label") }
            if (mode != AddMode.RECIPE) OutlinedButton(onClick = { quick = true }, modifier = Modifier.testTag("quick-add")) { Text("Quick add") }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("food-list")) {
            if (query.isBlank()) {
                if (saved.isNotEmpty() && mode != AddMode.RECIPE) {
                    item { Heading("Saved meals") }
                    items(saved, key = { "meal:${it.id}" }) { m ->
                        SavedMealRow(m, units) {
                            if (mode == AddMode.PLAN) vm.planSavedMeal(m, planDay ?: vm.today(), meal) else vm.logSavedMeal(m, meal)
                            back()
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item { Heading("Recent and frequent") }
                    items(recent, key = { "recent:${it.key}" }) { f -> FoodRow(f, units) { picked = f } }
                }
                val others = mine.filter { f -> recent.none { it.key == f.key } }
                if (others.isNotEmpty()) {
                    item { Heading("Your foods") }
                    items(others, key = { "mine:${it.key}" }) { f -> FoodRow(f, units) { picked = f } }
                }
                item {
                    Text(
                        if (builtIns.isEmpty()) "Loading the built-in food list…"
                        else "Type to search ${Units.grouped(builtIns.size)} built-in foods, and your own.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                if (results.isEmpty()) {
                    item {
                        Text(
                            "No food matches. Try fewer words, or type it in from the label.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(results, key = { "result:${it.key}" }) { f -> FoodRow(f, units) { picked = f } }
            }
            item {
                Text(
                    "Built-in foods: USDA FoodData Central (public domain)" +
                        (if (european) " and CIQUAL, Anses (Licence Ouverte 2.0)" else "") +
                        ". Carbs are shown without fibre, as on EU labels.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    picked?.let { f ->
        AmountDialog(
            food = f,
            units = units,
            initial = null,
            confirmText = if (mode == AddMode.PLAN) "Plan" else "Add",
            onConfirm = { a -> add(f, a); picked = null; back() },
            onDismiss = { picked = null },
        )
    }
    notFound?.let { code ->
        BarcodeNotFoundDialog(code, onTypeIn = { notFound = null; newFood(io.github.randomtools3377.fitness.core.Products.normalize(code) ?: code) }, onDismiss = { notFound = null })
    }
    if (quick) {
        QuickAddDialog(
            title = "Quick add",
            units = units,
            initialKcal = null,
            initialName = "",
            onConfirm = { kcal, name ->
                if (mode == AddMode.PLAN) vm.planQuickAdd(kcal, name, planDay ?: vm.today(), meal) else vm.quickAdd(kcal, name, meal)
                quick = false
                back()
            },
            onDismiss = { quick = false },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
fun FoodRow(food: Food, units: UnitSettings, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("food:${food.name}"),
    ) {
        Text(food.displayName(), style = MaterialTheme.typography.bodyLarge)
        Text(
            "${energy(food.per100.kcal, units)} per 100 ${food.unit.short} · ${sourceOf(food)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

@Composable
private fun SavedMealRow(m: SavedMeal, units: UnitSettings, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("meal:${m.name}"),
    ) {
        Text(m.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${energy(m.nutrients.kcal, units)} · ${m.items.joinToString { it.name }}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
    HorizontalDivider()
}

/**
 * How much of a food: grams (or millilitres), or one of the food's own portions. Shows the
 * calories and protein, carbs and fat as the amount changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountDialog(
    food: Food,
    units: UnitSettings,
    initial: Amount?,
    confirmText: String,
    onConfirm: (Amount) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onMove: ((Meal) -> Unit)? = null,
    current: Meal? = null,
) {
    val baseLabel = food.unit.short
    var portion by remember(food.key) { mutableStateOf(initial?.portion ?: food.portions.firstOrNull()?.label) }
    var text by remember(food.key) {
        mutableStateOf(initial?.let { formatAmount(it.quantity) } ?: if (food.portions.isNotEmpty()) "1" else "100")
    }
    val quantity = Units.parse(text)
    val amount = quantity?.takeIf { it > 0 }?.let { Amount(it, portion) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.displayName()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = portion == null,
                        onClick = { if (portion != null) { portion = null; text = "100" } },
                        label = { Text(baseLabel) },
                        modifier = Modifier.testTag("unit:base"),
                    )
                    for (p in food.portions) {
                        androidx.compose.material3.FilterChip(
                            selected = portion == p.label,
                            onClick = { if (portion != p.label) { portion = p.label; text = "1" } },
                            label = { Text("${p.label} (${formatAmount(p.amount)} $baseLabel)") },
                            modifier = Modifier.testTag("unit:${p.label}"),
                        )
                    }
                }
                NumberField(
                    text, { text = it },
                    label = if (portion == null) "Amount" else "How many",
                    tag = "field:amount",
                    suffix = portion?.let { "× $it" } ?: baseLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (amount != null) {
                    val n = food.nutrientsFor(amount)
                    Text(energy(n.kcal, units), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("amount-kcal"))
                    Text(macroLine(n), style = MaterialTheme.typography.bodySmall)
                }
                if (onMove != null && current != null) {
                    Text("Meal", style = MaterialTheme.typography.labelLarge)
                    Choices(Meal.entries, current, { it.title }, { "move:${it.name}" }) { if (it != current) onMove(it) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("delete-entry")) { Text("Remove from the diary") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onConfirm) }, enabled = amount != null, modifier = Modifier.testTag("confirm-amount")) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Quick add: just a number of calories, with an optional name. */
@Composable
fun QuickAddDialog(
    title: String,
    units: UnitSettings,
    initialKcal: Double?,
    initialName: String,
    onConfirm: (kcal: Double, name: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onMove: ((Meal) -> Unit)? = null,
    current: Meal? = null,
) {
    var text by remember {
        mutableStateOf(initialKcal?.let { formatAmount(Units.kcalToShown(it, units.energy)) } ?: "")
    }
    var name by remember { mutableStateOf(if (initialName == "Quick add") "" else initialName) }
    val kcal = typedKcal(text, units.energy)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Calories", "field:quick-kcal", Modifier.fillMaxWidth(), suffix = energyUnit(units))
                TextInput(name, { name = it }, "What it was (optional)", "field:quick-name")
                if (onMove != null && current != null) {
                    Text("Meal", style = MaterialTheme.typography.labelLarge)
                    Choices(Meal.entries, current, { it.title }, { "move:${it.name}" }) { if (it != current) onMove(it) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("delete-entry")) { Text("Remove from the diary") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { kcal?.let { onConfirm(it, name) } }, enabled = kcal != null, modifier = Modifier.testTag("confirm-quick")) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
