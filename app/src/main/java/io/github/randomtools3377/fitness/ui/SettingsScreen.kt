package io.github.randomtools3377.fitness.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.BuildConfig
import io.github.randomtools3377.fitness.core.Activity
import io.github.randomtools3377.fitness.core.Backup
import io.github.randomtools3377.fitness.core.DistanceUnit
import io.github.randomtools3377.fitness.core.EnergyUnit
import io.github.randomtools3377.fitness.core.HeightUnit
import io.github.randomtools3377.fitness.core.MacroMode
import io.github.randomtools3377.fitness.core.MacroTargets
import io.github.randomtools3377.fitness.core.Profile
import io.github.randomtools3377.fitness.core.Sex
import io.github.randomtools3377.fitness.core.Targets
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.VolumeUnit
import io.github.randomtools3377.fitness.core.WaterSettings
import io.github.randomtools3377.fitness.core.WeightGoal
import io.github.randomtools3377.fitness.core.WeightUnit
import io.github.randomtools3377.fitness.core.formatAmount
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier, open: (Route) -> Unit) {
    val settings by vm.settings.collectAsState()
    val units by vm.units.collectAsState()
    val european by vm.hasEuropeanFoods.collectAsState()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf<Backup?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            vm.readBackup(uri).fold(onSuccess = { importing = it }, onFailure = { importError = it.message })
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings")) {
        Section("Daily calorie target") {
            Text(
                settings.targetKcal?.let { energy(it.toDouble(), units) } ?: "Not set yet",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("settings-target"),
            )
            Button(onClick = { open(Route.Target) }, modifier = Modifier.testTag("open-target")) {
                Text(if (settings.targetKcal == null) "Work it out" else "Change it")
            }
        }

        MacroSection(settings.macros, settings.targetKcal ?: 2000, onSave = vm::setMacros)
        WaterSection(settings.water, units.volume, onSave = vm::setWater)

        Section("Units") {
            Text(
                if (settings.units == null) "Following the phone's region." else "Chosen here.",
                style = MaterialTheme.typography.bodySmall,
            )
            Choices(WeightUnit.entries, units.weight, { it.short }, { "units:${it.name}" }) { vm.setUnits(units.copy(weight = it)) }
            Choices(HeightUnit.entries, units.height, { it.short }, { "units:${it.name}" }) { vm.setUnits(units.copy(height = it)) }
            Choices(EnergyUnit.entries, units.energy, { it.short }, { "units:${it.name}" }) { vm.setUnits(units.copy(energy = it)) }
            Choices(VolumeUnit.entries, units.volume, { it.short }, { "units:${it.name}" }) { vm.setUnits(units.copy(volume = it)) }
            Choices(DistanceUnit.entries, units.distance, { it.short }, { "units:${it.name}" }) { vm.setUnits(units.copy(distance = it)) }
            if (settings.units != null) {
                TextButton(onClick = { vm.setUnits(null) }) { Text("Follow the phone's region again") }
            }
        }

        Section("Your data") {
            Text(
                "Everything stays on this phone. Export saves it all to a file you choose, which can be " +
                    "imported again here or on another phone. Phone backups leave the app out, so keep the file safe.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("fitness-backup-${vm.today()}.json") }, modifier = Modifier.testTag("export")) {
                    Text("Export")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.testTag("import")) {
                    Text("Import")
                }
            }
        }

        Section("About") {
            Text("Fitness App ${BuildConfig.VERSION_NAME}. Free, with no internet access.", style = MaterialTheme.typography.bodySmall)
            Text(
                "Built-in foods: USDA FoodData Central (U.S. Department of Agriculture, Agricultural Research Service), " +
                    "public domain" +
                    (if (european) "; and the ANSES-CIQUAL French food composition table (Anses), under the Licence Ouverte / Etalab Open Licence 2.0" else "") +
                    ". Carbs are shown without fibre, as on EU labels; values given as traces are shown as 0. " +
                    "The sources don't endorse this app.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("credits"),
            )
            Text(
                "The calorie target is an estimate (the Mifflin–St Jeor formula). It is not medical advice.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    importing?.let { b ->
        ConfirmDialog(
            "Replace everything with this file?",
            "It holds ${b.entries.size} diary entries, ${b.foods.count { !it.hidden }} of your foods, " +
                "${b.savedMeals.size} saved meals and ${b.weighIns.size} weigh-ins. What is in the app now is replaced. " +
                "Export first if you want to keep it.",
            "Replace",
            onConfirm = { vm.importBackup(b); importing = null },
            onDismiss = { importing = null },
            confirmTag = "confirm-import",
        )
    }
    importError?.let { e ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("That file can't be imported") },
            text = { Text(e) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun MacroSection(current: MacroTargets, kcal: Int, onSave: (MacroTargets) -> Unit) {
    var mode by remember(current) { mutableStateOf(current.mode) }
    var p by remember(current) { mutableStateOf(formatAmount(current.protein)) }
    var c by remember(current) { mutableStateOf(formatAmount(current.carbs)) }
    var f by remember(current) { mutableStateOf(formatAmount(current.fat)) }
    fun typed() = MacroTargets(mode, Units.parse(p) ?: -1.0, Units.parse(c) ?: -1.0, Units.parse(f) ?: -1.0)
    Section("Protein, carbs and fat") {
        Choices(MacroMode.entries, mode, { if (it == MacroMode.PERCENT) "Percent of calories" else "Grams" }, { "macro-mode:${it.name}" }) { new ->
            val t = typed()
            if (t.problem() == null) {
                val switched = t.switchTo(new, kcal)
                p = formatAmount(switched.protein); c = formatAmount(switched.carbs); f = formatAmount(switched.fat)
            }
            mode = new
        }
        val suffix = if (mode == MacroMode.PERCENT) "%" else "g"
        NumberField(p, { p = it }, "Protein", "field:macro-protein", Modifier.fillMaxWidth(), suffix = suffix)
        NumberField(c, { c = it }, "Carbs", "field:macro-carbs", Modifier.fillMaxWidth(), suffix = suffix)
        NumberField(f, { f = it }, "Fat", "field:macro-fat", Modifier.fillMaxWidth(), suffix = suffix)
        val t = typed()
        val problem = t.problem()
        if (problem != null) {
            Text(problem, color = MaterialTheme.colorScheme.error)
        } else if (mode == MacroMode.PERCENT) {
            val g = t.grams(kcal)
            Text("At $kcal kcal: ${macroLine(g)} a day.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { onSave(t) }, enabled = problem == null, modifier = Modifier.testTag("save-macros")) { Text("Save") }
    }
}

@Composable
private fun WaterSection(current: WaterSettings, volume: VolumeUnit, onSave: (WaterSettings) -> Unit) {
    fun shown(ml: Int) = formatAmount(Units.mlToShown(ml.toDouble(), volume))
    var target by remember(current, volume) { mutableStateOf(shown(current.targetMl)) }
    var glass by remember(current, volume) { mutableStateOf(shown(current.glassMl)) }
    var bottle by remember(current, volume) { mutableStateOf(shown(current.bottleMl)) }
    fun ml(text: String) = Units.parse(text)?.let { Units.shownToMl(it, volume).roundToInt() } ?: 0
    Section("Water") {
        NumberField(target, { target = it }, "Daily target", "field:water-target", Modifier.fillMaxWidth(), suffix = volume.short)
        NumberField(glass, { glass = it }, "A glass", "field:water-glass", Modifier.fillMaxWidth(), suffix = volume.short)
        NumberField(bottle, { bottle = it }, "A bottle", "field:water-bottle", Modifier.fillMaxWidth(), suffix = volume.short)
        Button(onClick = { onSave(WaterSettings(ml(target), ml(glass), ml(bottle))) }, modifier = Modifier.testTag("save-water")) { Text("Save") }
    }
}

/** Working out the daily calorie target from the body details, or typing one's own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetScreen(vm: MainViewModel, modifier: Modifier, back: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val units by vm.units.collectAsState()
    val profile = settings.profile
    val year = vm.today().year
    var sex by remember { mutableStateOf(profile?.sex ?: Sex.FEMALE) }
    var birth by remember { mutableStateOf(profile?.birthYear?.toString() ?: "") }
    var cm by remember { mutableStateOf(profile?.heightCm?.let { formatAmount(it) } ?: "") }
    val ftIn = profile?.heightCm?.let { Units.cmToFeetInches(it) }
    var feet by remember { mutableStateOf(ftIn?.first?.toString() ?: "") }
    var inches by remember { mutableStateOf(ftIn?.second?.toString() ?: "") }
    var weight by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(profile?.activity ?: Activity.SEDENTARY) }
    var goal by remember { mutableStateOf(profile?.goal ?: WeightGoal.KEEP) }
    var rate by remember { mutableStateOf(profile?.kgPerWeek ?: 0.5) }
    var own by remember { mutableStateOf("") }

    // The weight starts at the latest weigh-in.
    LaunchedEffect(Unit) {
        vm.latestKg()?.let { weight = Units.oneDecimal(Units.kgToShown(it, units.weight)) }
    }

    val heightCm = when (units.height) {
        HeightUnit.CM -> Units.parse(cm)
        HeightUnit.FT_IN -> Units.parse(feet)?.let { f -> Units.feetInchesToCm(f.toInt(), Units.parse(inches) ?: 0.0) }
    }
    val weightKg = Units.parse(weight)?.let { Units.shownToKg(it, units.weight) }
    val birthYear = birth.toIntOrNull()
    val age = birthYear?.let { Targets.age(it, year) }
    val problems = Targets.problems(age, heightCm, weightKg)
    val newProfile = if (problems.isEmpty()) Profile(sex, birthYear!!, heightCm!!, weightKg!!, activity, goal, rate) else null
    val result = newProfile?.let { vm.workedOut(it) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Daily calorie target") },
            navigationIcon = {
                IconButton(onClick = back, modifier = Modifier.testTag("back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("target-form"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sex", style = MaterialTheme.typography.labelLarge)
            Choices(Sex.entries, sex, { it.title }, { "sex:${it.name}" }) { sex = it }
            NumberField(birth, { birth = it }, "Year of birth", "field:birth-year", Modifier.fillMaxWidth(), decimals = false)
            when (units.height) {
                HeightUnit.CM -> NumberField(cm, { cm = it }, "Height", "field:height", Modifier.fillMaxWidth(), suffix = "cm")
                HeightUnit.FT_IN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(feet, { feet = it }, "Height", "field:height-ft", Modifier.weight(1f), suffix = "ft", decimals = false)
                    NumberField(inches, { inches = it }, "", "field:height-in", Modifier.weight(1f), suffix = "in")
                }
            }
            NumberField(weight, { weight = it }, "Weight", "field:target-weight", Modifier.fillMaxWidth(), suffix = units.weight.short)
            Text("How active you are", style = MaterialTheme.typography.labelLarge)
            for (a in Activity.entries) {
                Row(
                    Modifier.fillMaxWidth().selectable(selected = a == activity, onClick = { activity = a }, role = Role.RadioButton)
                        .testTag("activity:${a.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = a == activity, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(a.title)
                        Text(a.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Your aim", style = MaterialTheme.typography.labelLarge)
            Choices(WeightGoal.entries, goal, { it.title }, { "goal:${it.name}" }) { goal = it }
            if (goal != WeightGoal.KEEP) {
                Choices(Targets.RATES, rate, { rateLabel(it, units.weight) }, { "rate:$it" }) { rate = it }
            }

            for (p in problems) Text(p, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            if (result != null && newProfile != null) {
                Section {
                    LabelValue("Resting burn", energy(result.restingKcal.toDouble(), units))
                    LabelValue("× ${formatAmount(activity.factor)} for being ${activity.title.lowercase()}", energy(result.maintenanceKcal.toDouble(), units))
                    if (result.adjustmentKcal != 0) {
                        LabelValue(
                            (if (result.adjustmentKcal < 0) "To lose " else "To gain ") + rateLabel(rate, units.weight),
                            (if (result.adjustmentKcal < 0) "− " else "+ ") + energy(kotlin.math.abs(result.adjustmentKcal).toDouble(), units),
                        )
                    }
                    LabelValue("Suggested target", energy(result.targetKcal.toDouble(), units), tag = "worked-out")
                    result.minimumReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = { vm.saveProfile(newProfile, result.targetKcal); back() },
                        modifier = Modifier.fillMaxWidth().testTag("use-target"),
                    ) { Text("Use ${energy(result.targetKcal.toDouble(), units)}") }
                }
            }

            Text("Or type your own number", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(own, { own = it }, "My target", "field:own-target", Modifier.weight(1f), suffix = energyUnit(units), decimals = false)
                val kcal = typedKcal(own, units.energy)?.roundToInt()
                OutlinedButton(
                    onClick = {
                        if (kcal != null) {
                            // Keep the body details too, when they are filled in, for offering new numbers later.
                            if (newProfile != null) vm.saveProfile(newProfile, kcal) else vm.setOwnTarget(kcal)
                            back()
                        }
                    },
                    enabled = kcal != null && kcal in 500..10_000,
                    modifier = Modifier.testTag("save-own-target"),
                ) { Text("Save") }
            }
            Text(
                "The target is an estimate to start from. Weigh in now and then: when your weight moves the worked-out " +
                    "number by 50 kcal or more, the app offers the new number. It never changes your target by itself.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** "0.5 kg a week", "1.1 lb a week". */
private fun rateLabel(kg: Double, unit: WeightUnit): String =
    if (unit == WeightUnit.KG) "${formatAmount(kg)} kg a week" else "${Units.oneDecimal(Units.kgToShown(kg, unit))} lb a week"
