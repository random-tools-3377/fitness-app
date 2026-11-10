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
import io.github.randomtools3377.fitness.core.LabelText
import io.github.randomtools3377.fitness.labels.LabelOcr
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
fun FoodFormScreen(
    vm: MainViewModel,
    editId: Long?,
    modifier: Modifier,
    barcode: String? = null,
    back: () -> Unit,
    saved: (Food) -> Unit,
    /** A built-in food's id, to make the user's own copy of it (0.19.0). */
    copyOf: String? = null,
) {
    val units by vm.units.collectAsState()
    val scope = rememberCoroutineScope()
    var loadedId by remember { mutableStateOf<String?>(null) }
    // Vitamins and minerals of a copied built-in food, which the form doesn't show, kept as they are.
    var keptMicros by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
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
    var fibreText by remember { mutableStateOf("") }
    var sugarText by remember { mutableStateOf("") }
    var satFatText by remember { mutableStateOf("") }
    var saltText by remember { mutableStateOf("") }
    var alcoholText by remember { mutableStateOf("") }
    var caffeineText by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf<String?>(null) }
    val portions = remember { mutableStateListOf<PortionInput>() }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var code by remember { mutableStateOf(barcode) }

    LaunchedEffect(editId, copyOf) {
        val key = editId?.toString() ?: copyOf
        if (key != null && loadedId != key) {
            val source = if (editId != null) vm.food(editId) else copyOf?.let { vm.builtIn(it) }?.let { io.github.randomtools3377.fitness.core.FoodSearch.copyOf(it) }
            source?.let { f ->
                keptMicros = f.per100.micros - io.github.randomtools3377.fitness.core.Micro.ALCOHOL.key - io.github.randomtools3377.fitness.core.Micro.CAFFEINE.key
                name = f.name
                brand = f.brand
                unit = f.unit
                energyText = formatAmount(Units.kcalToShown(f.per100.kcal, energyUnit))
                proteinText = formatAmount(f.per100.protein)
                carbsText = formatAmount(f.per100.carbs)
                fatText = formatAmount(f.per100.fat)
                fibreText = f.per100.fibre?.let { formatAmount(it) }.orEmpty()
                sugarText = f.per100.sugar?.let { formatAmount(it) }.orEmpty()
                satFatText = f.per100.satFat?.let { formatAmount(it) }.orEmpty()
                saltText = f.per100.salt?.let { formatAmount(it) }.orEmpty()
                alcoholText = f.per100.micro(io.github.randomtools3377.fitness.core.Micro.ALCOHOL)?.let { formatAmount(it) }.orEmpty()
                caffeineText = f.per100.micro(io.github.randomtools3377.fitness.core.Micro.CAFFEINE)?.let { formatAmount(it) }.orEmpty()
                code = f.barcode
                portions.clear()
                f.portions.forEach { portions.add(PortionInput(it.label, formatAmount(it.amount))) }
            }
            loadedId = key
        }
    }

    fun save() {
        val energy = Units.parse(energyText)
        val typed = Nutrients(
            kcal = energy?.let { Units.shownToKcal(it, energyUnit) } ?: -1.0,
            protein = Units.parse(proteinText) ?: 0.0,
            carbs = Units.parse(carbsText) ?: 0.0,
            fat = Units.parse(fatText) ?: 0.0,
            fibre = Units.parse(fibreText),
            sugar = Units.parse(sugarText),
            satFat = Units.parse(satFatText),
            salt = Units.parse(saltText),
            micros = buildMap {
                putAll(keptMicros)
                Units.parse(alcoholText)?.let { put(io.github.randomtools3377.fitness.core.Micro.ALCOHOL.key, it) }
                Units.parse(caffeineText)?.let { put(io.github.randomtools3377.fitness.core.Micro.CAFFEINE.key, it) }
            },
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
        val food = Food(id = editId ?: 0, name = name.trim(), brand = brand.trim(), unit = unit, per100 = per100, portions = list, barcode = code)
        scope.launch {
            val f = vm.saveFood(food)
            // A copy of a built-in food stands in for it from now on (0.19.0).
            if (copyOf != null && editId == null) vm.replaceBuiltIn(copyOf, f.id)
            saved(f)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val better = on(io.github.randomtools3377.fitness.core.Feature.AI_LABELS)
    fun readLabel(uri: android.net.Uri) {
        reading = "Reading the label…"
        scope.launch {
            val text = runCatching { LabelOcr.read(context, uri, better) }.getOrDefault("")
            LabelOcr.clear(context)
            val r = LabelText.read(text)
            if (r == null) {
                reading = "No numbers could be read. Try again straight on, close up and in good light, or type them in."
                return@launch
            }
            if (r.millilitres) unit = BaseUnit.MILLILITRE
            val size = r.servingSize
            if (r.perServing && size != null) {
                basis = Basis.PER_SERVING
                servingSize = formatAmount(size)
            } else {
                basis = Basis.PER_100
            }
            r.kcal?.let { energyText = formatAmount(Math.round(Units.kcalToShown(it, energyUnit) * 10) / 10.0) }
            r.protein?.let { proteinText = formatAmount(it) }
            r.carbs?.let { carbsText = formatAmount(it) }
            r.fat?.let { fatText = formatAmount(it) }
            r.fibre?.let { fibreText = formatAmount(it) }
            r.sugar?.let { sugarText = formatAmount(it) }
            r.satFat?.let { satFatText = formatAmount(it) }
            r.salt?.let { saltText = formatAmount(it) }
            reading = buildString {
                append("Read ${r.found} of 8 numbers")
                append(if (r.perServing && size != null) ", per serving of ${formatAmount(size)} ${unit.short}." else ", per 100 ${unit.short}.")
                if (r.fibreTakenOff) append(" The label's carbs included fibre, so the fibre was taken off.")
                append(" Check each one against the label before saving.")
            }
        }
    }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) readLabel(uri) else reading = null
    }
    fun startCamera() {
        val uri = LabelOcr.newPhotoUri(context)
        photoUri = uri
        runCatching { takePhoto.launch(uri) }.onFailure { reading = "No camera app was found. Choose a photo instead." }
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else reading = "The camera wasn't allowed. Choose a photo of the label instead."
    }
    val choosePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) readLabel(uri) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (editId != null) "Change food" else if (copyOf != null) "Your own copy" else "A food from its label") },
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
            code?.let { Text("Barcode $it: scanning it finds this food from now on.", style = MaterialTheme.typography.bodySmall) }
            if (copyOf != null && editId == null) {
                Text(
                    "Change whatever is different about yours, then save. From then on your copy is used instead of the built-in food, " +
                        "which is hidden from the lists. Delete your copy to bring the built-in one back. Days already logged keep their numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("copy-hint"),
                )
            }
            if (on(io.github.randomtools3377.fitness.core.Feature.LABEL_PHOTO)) Text("Read the numbers from a photo of the label", style = MaterialTheme.typography.labelLarge)
            if (on(io.github.randomtools3377.fitness.core.Feature.LABEL_PHOTO)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) startCamera() else askCamera.launch(android.Manifest.permission.CAMERA)
                }, modifier = Modifier.testTag("label-photo")) { Text("Take a photo") }
                OutlinedButton(onClick = {
                    choosePhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.testTag("label-choose")) { Text("Choose a photo") }
            }
            reading?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("label-reading")) }
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
            if (on(io.github.randomtools3377.fitness.core.Feature.EXTRAS) || on(io.github.randomtools3377.fitness.core.Feature.ALCOHOL_CAFFEINE)) Text("Also on the label (optional)", style = MaterialTheme.typography.labelLarge)
            if (on(io.github.randomtools3377.fitness.core.Feature.EXTRAS)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(satFatText, { satFatText = it }, "of which saturates", "field:satfat", Modifier.weight(1f), suffix = "g")
                NumberField(sugarText, { sugarText = it }, "of which sugars", "field:sugar", Modifier.weight(1f), suffix = "g")
            }
            if (on(io.github.randomtools3377.fitness.core.Feature.EXTRAS)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(fibreText, { fibreText = it }, "Fibre", "field:fibre", Modifier.weight(1f), suffix = "g")
                NumberField(saltText, { saltText = it }, "Salt", "field:salt", Modifier.weight(1f), suffix = "g")
            }
            if (on(io.github.randomtools3377.fitness.core.Feature.EXTRAS)) Text("On a US label, salt is sodium (mg) × 2.5 ÷ 1000.", style = MaterialTheme.typography.bodySmall)
            if (on(io.github.randomtools3377.fitness.core.Feature.ALCOHOL_CAFFEINE)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(alcoholText, { alcoholText = it }, "Alcohol", "field:alcohol", Modifier.weight(1f), suffix = "g")
                NumberField(caffeineText, { caffeineText = it }, "Caffeine", "field:caffeine", Modifier.weight(1f), suffix = "mg")
            }
            if (on(io.github.randomtools3377.fitness.core.Feature.ALCOHOL_CAFFEINE)) Text(
                "For drinks: alcohol in grams (a drink of 5% is about 4 g per 100 ml), and caffeine in mg, for the same amount as the numbers above.",
                style = MaterialTheme.typography.bodySmall,
            )

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
    val features = LocalFeatures.current
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
        if (io.github.randomtools3377.fitness.core.Feature.BARCODES in features) item { CountryFilesSection(vm) }
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
