@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.fitness.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.ai.AiFoods
import io.github.randomtools3377.fitness.ai.LocalAi
import io.github.randomtools3377.fitness.ai.Recorder
import io.github.randomtools3377.fitness.core.Amount
import io.github.randomtools3377.fitness.core.Feature
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.FoodSearch
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Recipe
import io.github.randomtools3377.fitness.core.formatAmount
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Which AI way of adding foods (0.18.0 onwards). */
enum class AiKind { DESCRIBE, PLATE, RECIPE }

/**
 * Adding foods with the optional on-phone AI: describe a meal in words or by voice, a photo of a
 * plate, or a pasted recipe. Every food found is shown to check and change; nothing is added
 * until the user says so.
 */
@Composable
fun AiFoodsScreen(
    vm: MainViewModel,
    kind: AiKind,
    meal: Meal,
    mode: AddMode,
    planDay: kotlinx.datetime.LocalDate?,
    modifier: Modifier,
    back: () -> Unit,
    openRecipe: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mine by vm.foods.collectAsState()
    val builtIns by vm.visibleBuiltIns.collectAsState()
    val usages by vm.usage.collectAsState()
    val settings by vm.settings.collectAsState()
    val units by vm.units.collectAsState()
    val installed by vm.aiInstalled.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<AiFoods.Row>>(emptyList()) }
    var working by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var recipeName by rememberSaveable { mutableStateOf("") }
    var recipeServings by rememberSaveable { mutableStateOf("") }
    var recipeMethod by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf<Int?>(null) }
    val recorder = remember { Recorder() }
    var listening by remember { mutableStateOf(false) }

    fun pool() = AiFoods.Pool((mine.filter { !it.hidden }) + builtIns, usages, settings.favourites, System.currentTimeMillis())

    fun run(label: String, block: suspend () -> Unit) {
        problem = null
        working = label
        job = scope.launch {
            try {
                block()
            } catch (e: LocalAi.Unavailable) {
                problem = e.message
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                problem = "The AI couldn't do it this time. Try again, or add the foods the usual way."
            } finally {
                working = null
            }
        }
    }

    DisposableEffect(Unit) { onDispose { LocalAi.stop(); recorder.stop() } }

    val needs = when (kind) {
        AiKind.DESCRIBE, AiKind.RECIPE -> "text"
        AiKind.PLATE -> "plate"
    }
    val voice = kind == AiKind.DESCRIBE && on(Feature.AI_VOICE) && "speech" in installed

    val photoUri = remember { mutableStateOf<android.net.Uri?>(null) }
    fun plate(uri: android.net.Uri) = run("Looking at the photo…") { rows = AiFoods.fromPlate(context, uri, pool()) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> photoUri.value?.let { if (ok) plate(it) } }
    val choosePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) plate(uri) }

    fun listen() {
        listening = true
        run("Listening… tap Stop when you're done") {
            val wav = recorder.record()
            listening = false
            working = "Writing down what you said…"
            val heard = AiFoods.hear(context, wav)
            text = listOf(text.trim(), heard).filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else problem = "Saying a meal needs the microphone. You can allow it in the phone's settings, or type instead."
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when (kind) {
                        AiKind.DESCRIBE -> "Describe ${meal.title.lowercase()}"
                        AiKind.PLATE -> "Photo of a plate"
                        AiKind.RECIPE -> "Paste a recipe"
                    },
                )
            },
            navigationIcon = { IconButton(onClick = back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("ai-foods"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (needs !in installed) {
                item {
                    Text(
                        "This needs its AI file. Open Settings → Features → AI on this phone to add it.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("ai-missing"),
                    )
                }
            }
            item {
                when (kind) {
                    AiKind.DESCRIBE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("What did you have?") },
                            placeholder = { Text("Two eggs and a slice of toast with butter") },
                            modifier = Modifier.fillMaxWidth().testTag("field:describe"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { run("Finding the foods… this can take a little while") { rows = AiFoods.fromText(context, text, pool()) } },
                                enabled = text.isNotBlank() && working == null && "text" in installed,
                                modifier = Modifier.testTag("ai-find"),
                            ) { Text("Find the foods") }
                            if (voice) {
                                if (listening) {
                                    OutlinedButton(onClick = { recorder.stop() }, modifier = Modifier.testTag("ai-stop-listening")) { Text("Stop") }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                                            else askMic.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        enabled = working == null,
                                        modifier = Modifier.testTag("ai-say"),
                                    ) { Text("Say it") }
                                }
                            }
                        }
                        if (voice) Text("What you say is turned into text on the phone, then forgotten.", style = MaterialTheme.typography.bodySmall)
                    }
                    AiKind.PLATE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val uri = io.github.randomtools3377.fitness.labels.LabelOcr.newPhotoUri(context)
                                photoUri.value = uri
                                takePhoto.launch(uri)
                            },
                            enabled = working == null && "plate" in installed,
                            modifier = Modifier.testTag("ai-take-photo"),
                        ) { Text("Take a photo") }
                        OutlinedButton(
                            onClick = { choosePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = working == null && "plate" in installed,
                            modifier = Modifier.testTag("ai-choose-photo"),
                        ) { Text("Choose a photo") }
                    }
                    AiKind.RECIPE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("The recipe's text") },
                            placeholder = { Text("Paste it here: its name, ingredients and method") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("field:recipe-text"),
                        )
                        Button(
                            onClick = {
                                run("Reading the recipe… this can take a minute") {
                                    val (parts, found) = AiFoods.fromRecipe(context, text, pool())
                                    recipeName = parts.name
                                    recipeServings = parts.servings?.toString().orEmpty()
                                    recipeMethod = parts.method
                                    rows = found
                                }
                            },
                            enabled = text.isNotBlank() && working == null && "text" in installed,
                            modifier = Modifier.testTag("ai-read-recipe"),
                        ) { Text("Read the ingredients") }
                    }
                }
            }
            working?.let { w ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(w, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (!listening) TextButton(onClick = { LocalAi.stop(); job?.cancel() }) { Text("Cancel") }
                    }
                }
            }
            problem?.let { p -> item { Text(p, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-problem")) } }
            if (rows.isNotEmpty()) {
                if (kind == AiKind.RECIPE) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextInput(recipeName, { recipeName = it }, "Recipe name", "field:ai-recipe-name", Modifier.weight(2f))
                            NumberField(recipeServings, { recipeServings = it }, "Servings", "field:ai-recipe-servings", Modifier.weight(1f), decimals = false)
                        }
                    }
                }
                item {
                    Text(
                        "Check each one: tap a food to pick another, and change any amount. Untick what you didn't have.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                itemsIndexed(rows, key = { i, r -> "$i:${r.said}" }) { i, r ->
                    AiRowView(r, units, onInclude = { inc -> rows = rows.toMutableList().also { it[i] = r.copy(include = inc) } },
                        onAmount = { a -> rows = rows.toMutableList().also { it[i] = r.copy(amount = a, guessed = false) } },
                        onChoose = { choosing = i })
                }
                item {
                    val chosen = rows.filter { it.include && it.food != null }
                    val total = chosen.sumOf { r -> runCatching { r.food!!.nutrientsFor(r.amount).kcal }.getOrDefault(0.0) }
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("In all: ${energy(total, units)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("ai-total"))
                        Button(
                            onClick = {
                                if (kind == AiKind.RECIPE) {
                                    vm.recipeDraft.value = Recipe(
                                        name = recipeName.trim(),
                                        servings = recipeServings.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0,
                                        items = chosen.map { Recipe.item(it.food!!, it.amount) },
                                        notes = recipeMethod,
                                    )
                                    openRecipe()
                                } else {
                                    for (r in chosen) when (mode) {
                                        AddMode.LOG -> vm.log(r.food!!, r.amount, meal)
                                        AddMode.PLAN -> vm.plan(r.food!!, r.amount, planDay ?: vm.today(), meal)
                                        AddMode.RECIPE -> vm.addToRecipe(r.food!!, r.amount)
                                    }
                                    back()
                                }
                            },
                            enabled = chosen.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("ai-add"),
                        ) {
                            Text(
                                when {
                                    kind == AiKind.RECIPE -> "Check and save the recipe"
                                    mode == AddMode.PLAN -> "Plan ${chosen.size} ${if (chosen.size == 1) "food" else "foods"}"
                                    else -> "Add ${chosen.size} ${if (chosen.size == 1) "food" else "foods"}"
                                },
                            )
                        }
                        Text("Found by the AI on this phone. The numbers come from the app's food data.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    choosing?.let { i ->
        val r = rows.getOrNull(i)
        if (r == null) {
            choosing = null
        } else {
            ChooseFoodDialog(r, pool(), onPick = { f -> rows = rows.toMutableList().also { it[i] = AiFoods.choose(r, f) }; choosing = null }, onDismiss = { choosing = null })
        }
    }
}

@Composable
private fun AiRowView(
    r: AiFoods.Row,
    units: io.github.randomtools3377.fitness.core.UnitSettings,
    onInclude: (Boolean) -> Unit,
    onAmount: (Amount) -> Unit,
    onChoose: () -> Unit,
) {
    val food = r.food
    Column(Modifier.fillMaxWidth().testTag("ai-row:${r.said}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.include && food != null, onCheckedChange = onInclude, enabled = food != null)
            Column(Modifier.weight(1f).clickable(onClick = onChoose)) {
                Text(food?.displayName() ?: "No food found: tap to search", style = MaterialTheme.typography.bodyLarge)
                Text("You said: ${r.said}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (food != null) {
            var qty by remember(r.amount) { mutableStateOf(formatAmount(r.amount.quantity)) }
            Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    qty,
                    { v -> qty = v; v.toDoubleOrNull()?.takeIf { it > 0 }?.let { onAmount(r.amount.copy(quantity = it)) } },
                    if (r.amount.portion != null) "How many" else food.unit.short,
                    "field:ai-amount:${r.said}",
                    Modifier.width(110.dp),
                )
                Text(
                    "  ${r.amount.portion ?: food.unit.short} · ${energy(runCatching { food.nutrientsFor(r.amount).kcal }.getOrDefault(0.0), units)}" +
                        if (r.guessed) " · a guess, please check" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Another food for a row: the others that might fit, or a search of all the foods. */
@Composable
private fun ChooseFoodDialog(r: AiFoods.Row, pool: AiFoods.Pool, onPick: (Food) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf(r.said.split(' ', limit = 3).getOrNull(2).orEmpty()) }
    val results = remember(query) {
        if (query.isBlank()) r.candidates else FoodSearch.search(query, pool.foods, pool.usages, pool.now, 30, pool.favourites)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which food?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search foods") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("field:ai-search"))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(results, key = { _, f -> f.key }) { _, f ->
                        Text(f.displayName(), Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
