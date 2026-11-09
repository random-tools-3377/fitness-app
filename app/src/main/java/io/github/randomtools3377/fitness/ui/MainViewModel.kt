package io.github.randomtools3377.fitness.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.core.Amount
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.Backup
import io.github.randomtools3377.fitness.core.BackupError
import io.github.randomtools3377.fitness.core.DayTotals
import io.github.randomtools3377.fitness.core.Diary
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.FoodCsv
import io.github.randomtools3377.fitness.core.FoodSearch
import io.github.randomtools3377.fitness.core.MacroTargets
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.Profile
import io.github.randomtools3377.fitness.core.SavedMeal
import io.github.randomtools3377.fitness.core.TargetResult
import io.github.randomtools3377.fitness.core.Targets
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Usage
import io.github.randomtools3377.fitness.core.WaterEntry
import io.github.randomtools3377.fitness.core.WaterSettings
import io.github.randomtools3377.fitness.core.WeighIn
import io.github.randomtools3377.fitness.core.Weight
import io.github.randomtools3377.fitness.core.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale

/** A new worked-out target, offered after a weigh-in. The app never changes the target by itself. */
data class TargetOffer(val currentKcal: Int?, val newKcal: Int)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitnessApplication).container.repository
    private val resolver = app.contentResolver

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** The day the diary shows. */
    val day = MutableStateFlow(today())
    private var lastOpened = today()

    /** Short messages shown at the bottom of the screen. */
    val message = MutableStateFlow<String?>(null)
    val targetOffer = MutableStateFlow<TargetOffer?>(null)

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** The units in use: the user's choice, or the usual ones for the phone's region. */
    val units: StateFlow<UnitSettings> = settings
        .map { it.units ?: UnitSettings.forRegion(Locale.getDefault().country) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSettings.forRegion(Locale.getDefault().country))

    val entries: StateFlow<List<DiaryEntry>> = day.flatMapLatest { repo.entries(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val totals: StateFlow<DayTotals> = entries.map { DayTotals.of(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayTotals.of(emptyList()))
    val water: StateFlow<List<WaterEntry>> = day.flatMapLatest { repo.water(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** The day's finished workouts. */
    val dayWorkouts: StateFlow<List<Workout>> = day.flatMapLatest { d -> repo.workouts.map { all -> all.filter { it.finished && it.day == d } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Calories burned in the day's workouts, added to the day's allowance (half, if chosen). */
    val dayBurned: StateFlow<Double> = combine(dayWorkouts, settings) { list, s ->
        list.sumOf { it.burnedKcal } * (if (s.burnedHalf) 0.5 else 1.0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun setBurnedHalf(half: Boolean) = launchSafely { repo.updateSettings { it.copy(burnedHalf = half) } }

    val weighIns: StateFlow<List<WeighIn>> = repo.weighIns.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val foods: StateFlow<List<Food>> = repo.foods.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedMeals: StateFlow<List<SavedMeal>> = repo.savedMeals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val usage: StateFlow<Map<String, Usage>> = repo.usage.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The built-in foods, empty until the list has been read. */
    val builtIns = MutableStateFlow<List<Food>>(emptyList())

    /** Whether the built-in list holds CIQUAL's European foods, for the credits. */
    val hasEuropeanFoods: StateFlow<Boolean> = builtIns.map { list -> list.any { it.builtInId?.startsWith("C") == true } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Barcodes and country files (0.3.0).

    val countries: StateFlow<List<io.github.randomtools3377.fitness.data.CountryRow>> =
        repo.countries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Products read so far while a country file is being added, or null when none is. */
    val countryImport = MutableStateFlow<Int?>(null)

    fun importCountry(uri: Uri) {
        if (countryImport.value != null) return
        countryImport.value = 0
        viewModelScope.launch {
            try {
                val (name, count) = repo.importCountry(getApplication<Application>().contentResolver, uri, now()) { countryImport.value = it }
                message.value = "Added $name: ${io.github.randomtools3377.fitness.core.Units.grouped(count)} products"
            } catch (e: io.github.randomtools3377.fitness.data.CountryFiles.NotACountryFile) {
                message.value = e.message
            } catch (e: Exception) {
                message.value = "That file could not be read. Download it again and try once more."
            } finally {
                countryImport.value = null
            }
        }
    }

    fun removeCountry(name: String) = launchSafely {
        repo.removeCountry(name)
        message.value = "Removed $name"
    }

    /** The food for a scanned barcode, or null if it is in no country file and none of your foods. */
    suspend fun foodForBarcode(raw: String): Food? {
        val code = io.github.randomtools3377.fitness.core.Products.normalize(raw) ?: return null
        return repo.foodByBarcode(code)
    }

    /** Products from country files that were logged before, so they show among recent foods. */
    private val usedProducts: StateFlow<List<Food>> = usage.map { u ->
        repo.productFoods(u.keys.filter { it.startsWith("b:P") }.map { it.removePrefix("b:P") })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What is typed in the food search. */
    val query = MutableStateFlow("")

    /** Search results for [query]: the user's foods and the built-in ones, best first. */
    val results: StateFlow<List<Food>> = combine(query.debounce(120), foods, builtIns, usage, usedProducts) { q, mine, built, used, products ->
        FoodSearch.search(q, mine + products + built, used, now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Foods used before, the most recent and most used first. */
    val recent: StateFlow<List<Food>> = combine(foods, builtIns, usage, usedProducts) { mine, built, used, products ->
        FoodSearch.recent(mine + products + built, used, now()).take(30)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // Reminders are set again each time the app starts, in case the phone dropped them.
            runCatching { io.github.randomtools3377.fitness.reminders.ReminderScheduler.schedule(getApplication(), repo.settingsOnce().reminders) }
        }
        viewModelScope.launch {
            builtIns.value = runCatching { repo.builtInFoods() }.getOrElse {
                message.value = "The built-in food list could not be read."
                emptyList()
            }
        }
    }

    // 0.4.0: reminders, the privacy lock, the automatic backup.

    /** A reminder that opened the app: what it was about, like "WEIGH_IN". */
    val openFromReminder = MutableStateFlow<String?>(null)

    /** Tabs unlocked since the app was last opened. Leaving the app locks them again. */
    val unlocked = MutableStateFlow<Set<String>>(emptySet())

    fun markUnlocked(tab: String) { unlocked.value = unlocked.value + tab }
    fun lockAgain() { unlocked.value = emptySet() }

    fun setLocked(tabs: Set<String>) = launchSafely { repo.updateSettings { it.copy(locked = tabs) } }

    fun setReminders(r: io.github.randomtools3377.fitness.core.ReminderSettings) = launchSafely {
        repo.updateSettings { it.copy(reminders = r) }
        io.github.randomtools3377.fitness.reminders.ReminderScheduler.schedule(getApplication(), r)
    }

    fun setWorkoutsPerWeek(n: Int) = launchSafely {
        require(n in 1..14) { "Choose between 1 and 14 workouts a week." }
        repo.updateSettings { it.copy(workoutsPerWeek = n) }
    }

    /** The automatic backup's folder, by its name, or null when it is off. */
    val backupFolder = MutableStateFlow(io.github.randomtools3377.fitness.data.Backups.folderName(app))
    val lastBackup = MutableStateFlow(io.github.randomtools3377.fitness.data.Backups.lastBackup(app))

    fun setBackupFolder(uri: Uri?) = viewModelScope.launch {
        try {
            io.github.randomtools3377.fitness.data.Backups.setFolder(getApplication(), uri)
            backupFolder.value = io.github.randomtools3377.fitness.data.Backups.folderName(getApplication())
            if (uri != null) autoBackup()
        } catch (e: Exception) {
            message.value = "That folder can't be used. Choose another one."
        }
    }

    private suspend fun autoBackup() {
        val r = io.github.randomtools3377.fitness.data.Backups.autoBackupIfDue(getApplication(), today()) { repo.export(now()) }
        if (r == io.github.randomtools3377.fitness.data.Backups.AutoResult.FolderGone) {
            message.value = "The automatic backup couldn't write to its folder. Choose the folder again in Settings."
        }
        lastBackup.value = io.github.randomtools3377.fitness.data.Backups.lastBackup(getApplication())
    }

    fun onAppOpened() {
        viewModelScope.launch { runCatching { autoBackup() } }
        val t = today()
        if (t != lastOpened) {
            // A new day began while the app was closed: show it, unless another day was picked.
            if (day.value == lastOpened) day.value = t
            lastOpened = t
        }
    }

    fun show(text: String) {
        message.value = text
    }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            message.value = e.message ?: "That didn't work."
        }
    }

    // The diary.

    fun log(food: Food, amount: Amount, meal: Meal) = launchSafely {
        repo.addEntries(listOf(Diary.entry(food, amount, day.value, meal, now())))
        message.value = "Added ${food.name} to ${meal.title.lowercase()}"
    }

    fun quickAdd(kcal: Double, name: String, meal: Meal) = launchSafely {
        repo.addEntries(listOf(Diary.quickAdd(kcal, day.value, meal, now(), name)))
        message.value = "Added to ${meal.title.lowercase()}"
    }

    suspend fun foodOf(entry: DiaryEntry): Food? = repo.foodOf(entry)

    fun changeEntry(entry: DiaryEntry, food: Food, amount: Amount) = launchSafely {
        repo.updateEntry(Diary.changeAmount(entry, food, amount))
    }

    fun changeQuickAdd(entry: DiaryEntry, kcal: Double, name: String) = launchSafely {
        require(kcal > 0) { "Type a number of calories above 0." }
        repo.updateEntry(entry.copy(name = name.ifBlank { "Quick add" }, nutrients = Nutrients(kcal = kcal)))
    }

    fun moveEntry(entry: DiaryEntry, meal: Meal) = launchSafely { repo.updateEntry(entry.copy(meal = meal)) }

    fun deleteEntry(entry: DiaryEntry) = launchSafely {
        repo.deleteEntry(entry.id)
        message.value = "Removed ${entry.name}"
    }

    suspend fun entriesOn(d: LocalDate): List<DiaryEntry> = repo.entriesOnce(d)

    /** Copies a meal ([fromMeal]), or the whole day when null, from [fromDay] to the day shown. */
    fun copyFrom(fromDay: LocalDate, fromMeal: Meal?, toMeal: Meal?) = launchSafely {
        val source = repo.entriesOnce(fromDay).filter { fromMeal == null || it.meal == fromMeal }
        require(source.isNotEmpty()) { "There is nothing to copy on that day." }
        repo.addEntries(Diary.copy(source, day.value, now(), toMeal))
        message.value = if (fromMeal == null) "Copied ${source.size} items" else "Copied ${fromMeal.title.lowercase()}"
    }

    // Saved meals.

    fun saveMeal(name: String, meal: Meal) = launchSafely {
        val items = entries.value.filter { it.meal == meal }
        repo.saveMeal(SavedMeal.from(name, items, now()))
        message.value = "Saved \"${name.trim()}\""
    }

    fun logSavedMeal(saved: SavedMeal, meal: Meal) = launchSafely {
        repo.addEntries(saved.toEntries(day.value, meal, now()))
        message.value = "Added ${saved.name} to ${meal.title.lowercase()}"
    }

    fun renameSavedMeal(saved: SavedMeal, name: String) = launchSafely {
        require(name.isNotBlank()) { "Give the meal a name." }
        repo.renameSavedMeal(saved.id, name.trim())
    }

    fun deleteSavedMeal(saved: SavedMeal) = launchSafely { repo.deleteSavedMeal(saved.id) }

    // Foods the user typed in.

    /** Saves the food and gives it back with its id. */
    suspend fun saveFood(food: Food): Food {
        val id = repo.saveFood(food, now())
        return food.copy(id = id)
    }

    suspend fun food(id: Long): Food? = repo.food(id)

    fun deleteFood(food: Food) = launchSafely {
        repo.deleteFood(food.id)
        message.value = "Deleted ${food.name}"
    }

    // Importing the user's own food list from a spreadsheet (CSV).

    /** What a chosen spreadsheet holds, shown before anything is added. */
    val foodImport = MutableStateFlow<FoodCsv.Result?>(null)

    fun previewFoodImport(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val parsed = withContext(Dispatchers.Default) { FoodCsv.withoutExisting(FoodCsv.parse(text), repo.foods.first()) }
            foodImport.value = parsed
        } catch (e: Exception) {
            message.value = "The file could not be read."
        }
    }

    fun confirmFoodImport() = viewModelScope.launch {
        val r = foodImport.value ?: return@launch
        foodImport.value = null
        if (r.foods.isEmpty()) return@launch
        repo.addFoods(r.foods, now())
        message.value = if (r.foods.size == 1) "Added 1 food" else "Added ${r.foods.size} foods"
    }

    fun saveFoodTemplate(uri: Uri) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")!!.use { it.write(FoodCsv.TEMPLATE.toByteArray(Charsets.UTF_8)) }
            }
            message.value = "Template saved. Fill it in, save it as CSV, then import it."
        } catch (e: Exception) {
            message.value = "The file could not be written."
        }
    }

    // Water.

    fun addWater(ml: Int) = launchSafely { repo.addWater(day.value, ml, now()) }
    fun undoWater() = launchSafely { repo.undoWater(day.value) }

    // Body weight and the target.

    /** The weight the target is worked out from: the latest weigh-in, or the one typed with the profile. */
    fun latestKg(): Double? = Weight.latest(weighIns.value)?.kg ?: settings.value.profile?.weightKg

    fun workedOut(profile: Profile, weightKg: Double = profile.weightKg): TargetResult =
        Targets.calculate(profile, today().year, weightKg)

    fun addWeighIn(kg: Double, on: LocalDate) = launchSafely {
        require(kg in 20.0..400.0) { "Type a weight between 20 and 400 kg (44 to 880 lb)." }
        repo.addWeighIn(WeighIn(day = on, kg = kg, at = now()))
        message.value = "Weigh-in saved"
        offerNewTarget()
    }

    fun deleteWeighIn(w: WeighIn) = launchSafely { repo.deleteWeighIn(w.id) }

    /** After a weigh-in: offers the new worked-out target when it moved 50 kcal or more. */
    private suspend fun offerNewTarget() {
        val s = repo.settingsOnce()
        val profile = s.profile ?: return
        val latest = Weight.latest(repo.weighInsOnce())?.kg ?: return
        val newKcal = workedOut(profile, latest).targetKcal
        if (Targets.shouldOffer(s.lastWorkedOutKcal, newKcal) && newKcal != s.targetKcal) {
            targetOffer.value = TargetOffer(s.targetKcal, newKcal)
        }
    }

    fun answerOffer(accept: Boolean) = launchSafely {
        val offer = targetOffer.value ?: return@launchSafely
        targetOffer.value = null
        repo.updateSettings {
            if (accept) it.copy(targetKcal = offer.newKcal, lastWorkedOutKcal = offer.newKcal) else it.copy(lastWorkedOutKcal = offer.newKcal)
        }
        if (accept) message.value = "Target changed"
    }

    /**
     * Saves the body details. When the weight typed differs from the latest weigh-in, it is saved
     * as today's weigh-in, so the latest weigh-in is always the one the target uses. [targetKcal]
     * is the target chosen: the worked-out one or the user's own.
     */
    fun saveProfile(profile: Profile, targetKcal: Int) = launchSafely {
        val latest = Weight.latest(repo.weighInsOnce())
        if (latest == null || kotlin.math.abs(latest.kg - profile.weightKg) > 0.05) {
            repo.addWeighIn(WeighIn(day = today(), kg = profile.weightKg, at = now()))
        }
        val worked = workedOut(profile).targetKcal
        repo.updateSettings { it.copy(profile = profile, targetKcal = targetKcal, lastWorkedOutKcal = worked) }
        message.value = "Target saved"
    }

    fun setOwnTarget(kcal: Int) = launchSafely {
        require(kcal in 500..10_000) { "Type a target between 500 and 10,000 kcal." }
        repo.updateSettings { it.copy(targetKcal = kcal) }
        message.value = "Target saved"
    }

    fun setMacros(m: MacroTargets) = launchSafely {
        m.problem()?.let { throw IllegalArgumentException(it) }
        repo.updateSettings { it.copy(macros = m) }
        message.value = "Saved"
    }

    fun setWater(w: WaterSettings) = launchSafely {
        require(w.targetMl in 250..10_000) { "Type a water target between 250 ml and 10 litres." }
        require(w.glassMl in 10..3000 && w.bottleMl in 10..3000) { "Type sizes between 10 ml and 3 litres." }
        repo.updateSettings { it.copy(water = w) }
        message.value = "Saved"
    }

    /** Null follows the phone's region again. */
    fun setUnits(u: UnitSettings?) = launchSafely { repo.updateSettings { it.copy(units = u) } }

    // Export and import.

    fun exportTo(uri: Uri) = viewModelScope.launch {
        try {
            val text = repo.export(now())
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            io.github.randomtools3377.fitness.data.Backups.markBackedUp(getApplication())
            lastBackup.value = io.github.randomtools3377.fitness.data.Backups.lastBackup(getApplication())
            message.value = "Exported everything to the file"
        } catch (e: Exception) {
            message.value = "The file could not be written."
        }
    }

    /** Reads a backup file to show what is in it before anything is replaced. */
    suspend fun readBackup(uri: Uri): Result<Backup> = withContext(Dispatchers.IO) {
        try {
            val text = resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            Result.success(Backup.decode(text))
        } catch (e: BackupError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(BackupError("The file could not be read."))
        }
    }

    fun importBackup(backup: Backup) = viewModelScope.launch {
        try {
            repo.import(backup, now())
            message.value = "Imported. Everything now matches the file."
        } catch (e: Exception) {
            message.value = "The import failed, so nothing was changed."
        }
    }
}
