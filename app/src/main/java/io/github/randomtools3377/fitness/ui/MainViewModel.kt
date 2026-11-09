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
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.minus
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

    /** True once the settings have been read, so the first tab isn't picked from the defaults (0.17.0). */
    val settingsLoaded: StateFlow<Boolean> = repo.settings.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A tab to go to, such as a hidden one opened from Settings (0.17.0). */
    val goToTab = MutableStateFlow<String?>(null)

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

    private val noTargets: (LocalDate) -> Int? = { null }

    /** Each day's calorie target: by weekday, on training days, and in a diet break (0.11.0). */
    val targetFor: StateFlow<(LocalDate) -> Int?> = combine(settings, repo.weighIns, repo.workouts) { s, w, wo ->
        io.github.randomtools3377.fitness.core.DayTarget.resolver(
            s, io.github.randomtools3377.fitness.core.Weight.latest(w)?.kg, wo.filter { it.finished }.map { it.day }.toSet(), today().year,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, noTargets)

    /** Why the shown day's target differs from the usual one, if it does. */
    val dayTargetWhy: StateFlow<String?> = combine(day, settings, repo.weighIns, repo.workouts) { d, s, w, wo ->
        io.github.randomtools3377.fitness.core.DayTarget.whyFor(
            s, io.github.randomtools3377.fitness.core.Weight.latest(w)?.kg, wo.filter { it.finished }.map { it.day }.toSet(), today().year, d,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setDayTargets(t: io.github.randomtools3377.fitness.core.DayTargets) = launchSafely {
        t.weekdayKcal.values.forEach { require(it in 800..10000) { "A day's target is between 800 and 10,000 kcal." } }
        t.trainingExtraKcal?.let { require(it in -1000..3000) { "The training-day extra is up to 3,000 kcal." } }
        repo.updateSettings { it.copy(dayTargets = t) }
        message.value = "Saved"
    }

    fun startDietBreak(weeks: Int) = launchSafely {
        val s = settings.value
        require(s.profile != null) { "Fill in your details under the daily target first, so maintenance can be worked out." }
        repo.updateSettings { it.copy(dietBreak = io.github.randomtools3377.fitness.core.DietBreak(today(), weeks)) }
        message.value = "Diet break started"
    }

    fun endDietBreak() = launchSafely { repo.updateSettings { it.copy(dietBreak = null) } }

    fun setDrinkGrams(g: Double?) = launchSafely {
        g?.let { require(it in 1.0..50.0) { "A standard drink is between 1 and 50 g of alcohol." } }
        repo.updateSettings { it.copy(drinkGrams = g) }
    }

    /** The day's steps and the calories they add (0.10.0), read from Health Connect when steps are on. */
    val daySteps = MutableStateFlow<Long?>(null)
    val dayStepsKcal = MutableStateFlow(0.0)

    /** Reads the day's steps again: when the app comes back to the front, or the day changes. */
    fun refreshSteps() = viewModelScope.launch {
        val s = repo.settingsOnce()
        val d = day.value
        if (!s.stepsOn) {
            daySteps.value = null
            dayStepsKcal.value = 0.0
        } else {
            daySteps.value = runCatching { repo.allStepsOn(d, s) }.getOrDefault(0)
            dayStepsKcal.value = runCatching { repo.stepsKcal(d, s) }.getOrDefault(0.0)
        }
        io.github.randomtools3377.fitness.widget.TodayWidget.refresh(getApplication())
    }

    fun setStepsOn(on: Boolean) = launchSafely {
        repo.updateSettings { it.copy(stepsOn = on) }
        refreshSteps()
    }

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
    private val usedProducts: StateFlow<List<Food>> = combine(usage, settings) { u, s -> u.keys + s.favourites }.distinctUntilChanged().map { keys ->
        repo.productFoods(keys.filter { it.startsWith("b:P") }.map { it.removePrefix("b:P") })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How often each food was used, with the user's favourites (0.7.0). */
    private val usageAndFavourites = combine(usage, settings) { u, s -> u to s.favourites }.distinctUntilChanged()

    /** Stars or unstars a food, so it comes first in the search. */
    fun setFavourite(food: Food, on: Boolean) = launchSafely {
        repo.updateSettings { it.copy(favourites = if (on) it.favourites + food.key else it.favourites - food.key) }
    }

    /** What is typed in the food search. */
    val query = MutableStateFlow("")

    /** Search results for [query]: the user's foods and the built-in ones, best first. */
    private val recipeFoods: StateFlow<List<Food>> = repo.recipes.map { list -> list.map { it.toFood() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val results: StateFlow<List<Food>> = combine(query.debounce(120), combine(foods, recipeFoods) { a, b -> b + a }, builtIns, usageAndFavourites, usedProducts) { q, mine, built, (used, favs), products ->
        FoodSearch.search(q, mine + products + built, used, now(), favourites = favs)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Foods used before, the most recent and most used first. */
    val recent: StateFlow<List<Food>> = combine(combine(foods, recipeFoods) { a, b -> b + a }, builtIns, usageAndFavourites, usedProducts) { mine, built, (used, favs), products ->
        FoodSearch.recent(mine + products + built, used, now(), favs).take(30 + favs.size)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // The first time 0.15.0 runs: a simple start, plus every part already in use.
        viewModelScope.launch {
            val s = repo.settingsOnce()
            if (s.features == null) {
                val first = io.github.randomtools3377.fitness.core.Features.firstChoice(s, repo.featureUse())
                repo.updateSettings { it.copy(features = io.github.randomtools3377.fitness.core.Features.encode(first)) }
            }
        }
        // Steps follow the day shown; the home-screen widget follows the day's food and water (0.10.0).
        viewModelScope.launch { day.collect { refreshSteps() } }
        viewModelScope.launch {
            combine(repo.allEntries, repo.allWater, settings) { _, _, _ -> Unit }
                .debounce(500)
                .collect { io.github.randomtools3377.fitness.widget.TodayWidget.refresh(getApplication()) }
        }
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

    // 0.6.0: meal targets and fasting.

    val fasts: StateFlow<List<io.github.randomtools3377.fitness.core.Fast>> = repo.fasts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The fast going on now, if any. */
    val activeFast: StateFlow<io.github.randomtools3377.fitness.core.Fast?> = fasts.map { list -> list.lastOrNull { it.endedAt == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Which part of the Plan tab shows: meals, shopping, recipes or fasting. */
    val planPart = MutableStateFlow("meals")

    /** Set when food is logged during a fast, to ask whether to end it. */
    val askEndFast = MutableStateFlow(false)

    fun startFast(hours: Double) = launchSafely {
        require(hours in 1.0..72.0) { "Choose between 1 and 72 hours." }
        if (activeFast.value != null) return@launchSafely
        val f = io.github.randomtools3377.fitness.core.Fast(startedAt = now(), plannedHours = hours)
        repo.saveFast(f)
        repo.updateSettings { it.copy(fastHours = hours) }
        io.github.randomtools3377.fitness.fasting.FastAlarm.set(getApplication(), f.endsAt())
        message.value = "Fast started"
    }

    fun endFast() = launchSafely {
        val f = activeFast.value ?: return@launchSafely
        val ended = f.copy(endedAt = now())
        repo.saveFast(ended)
        io.github.randomtools3377.fitness.fasting.FastAlarm.cancel(getApplication())
        askEndFast.value = false
        message.value = if (ended.finished) "Fast finished: well done" else "Fast ended"
    }

    fun deleteFast(f: io.github.randomtools3377.fitness.core.Fast) = launchSafely { repo.deleteFast(f.id) }

    private fun checkFast() { if (activeFast.value != null) askEndFast.value = true }

    fun setMealSplit(m: io.github.randomtools3377.fitness.core.MealSplit) = launchSafely {
        if (m.enabled) m.problem(settings.value.targetKcal ?: 0)?.let { throw IllegalArgumentException(it) }
        repo.updateSettings { it.copy(mealSplit = m) }
        message.value = "Saved"
    }

    // 0.5.0: planning ahead, recipes and the shopping list.

    /** The first day the plan shows (a week from it). Tomorrow to start. */
    val planDay = MutableStateFlow(today().plus(DatePeriod(days = 1)))

    /** Planned and eaten items for the week from [planDay]. */
    val planWeek: StateFlow<List<DiaryEntry>> = planDay.flatMapLatest { d -> repo.entriesBetween(d, d.plus(DatePeriod(days = 6))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recipes: StateFlow<List<io.github.randomtools3377.fitness.core.Recipe>> = repo.recipes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shopping: StateFlow<List<io.github.randomtools3377.fitness.core.ShoppingItem>> = repo.shopping.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Plans [food] for [day]'s [meal]. */
    fun plan(food: Food, amount: Amount, day: LocalDate, meal: Meal) = launchSafely {
        repo.addEntries(listOf(io.github.randomtools3377.fitness.core.Plan.plan(food, amount, day, meal, now())))
        message.value = "Planned ${food.name}"
    }

    fun planQuickAdd(kcal: Double, name: String, day: LocalDate, meal: Meal) = launchSafely {
        repo.addEntries(listOf(Diary.quickAdd(kcal, day, meal, now(), name).copy(planned = true)))
        message.value = "Planned"
    }

    fun planSavedMeal(saved: SavedMeal, day: LocalDate, meal: Meal) = launchSafely {
        repo.addEntries(saved.toEntries(day, meal, now()).map { it.copy(planned = true) })
        message.value = "Planned ${saved.name}"
    }

    /** Ticks a planned item: it is logged as eaten now. */
    fun eatPlanned(e: DiaryEntry) = launchSafely {
        repo.updateEntry(io.github.randomtools3377.fitness.core.Plan.eat(e, now()))
        message.value = "Logged ${e.name}"
        checkFast()
    }

    /** Copies the planned items of [from] (one day, or the week from it) [days] days later. */
    fun copyPlan(from: LocalDate, length: Int, days: Int) = launchSafely {
        val source = repo.entriesBetweenOnce(from, from.plus(DatePeriod(days = length - 1))).filter { it.planned }
        require(source.isNotEmpty()) { "There is nothing planned to copy." }
        repo.addEntries(io.github.randomtools3377.fitness.core.Plan.copy(source, days, now()))
        message.value = "Copied ${source.size} planned items"
    }

    /** The recipe being written or changed, kept here while foods are picked for it. */
    val recipeDraft = MutableStateFlow<io.github.randomtools3377.fitness.core.Recipe?>(null)

    fun editRecipe(r: io.github.randomtools3377.fitness.core.Recipe?) {
        recipeDraft.value = r ?: io.github.randomtools3377.fitness.core.Recipe(name = "", servings = 4.0, items = emptyList())
    }

    fun addToRecipe(food: Food, amount: Amount) {
        val d = recipeDraft.value ?: return
        recipeDraft.value = d.copy(items = d.items + io.github.randomtools3377.fitness.core.Recipe.item(food, amount))
    }

    /** Saves the draft; gives back true when it was saved. */
    suspend fun saveRecipe(): Boolean {
        val d = recipeDraft.value ?: return false
        val problems = d.problems()
        if (problems.isNotEmpty()) { message.value = problems.first(); return false }
        repo.saveRecipe(d.copy(name = d.name.trim(), createdAt = if (d.createdAt == 0L) now() else d.createdAt))
        message.value = "Saved ${d.name.trim()}"
        recipeDraft.value = null
        return true
    }

    fun deleteRecipe(r: io.github.randomtools3377.fitness.core.Recipe) = launchSafely {
        repo.deleteRecipe(r.id)
        message.value = "Deleted ${r.name}. What was already logged keeps its numbers."
    }

    fun makeShoppingList(from: LocalDate, to: LocalDate) = launchSafely {
        repo.makeShoppingList(from, to)
        message.value = "Shopping list made"
    }

    fun tickShopping(i: io.github.randomtools3377.fitness.core.ShoppingItem) = launchSafely { repo.tickShopping(i.id, !i.ticked) }
    fun addOwnShopping(name: String) = launchSafely { if (name.isNotBlank()) repo.addOwnShopping(name) }
    fun deleteShopping(i: io.github.randomtools3377.fitness.core.ShoppingItem) = launchSafely { repo.deleteShopping(i.id) }

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

    /** Daily targets for fibre, sugar, saturated fat and salt (0.7.0). */
    fun setExtraTargets(t: io.github.randomtools3377.fitness.core.ExtraTargets) = launchSafely {
        listOfNotNull(t.fibre, t.sugar, t.satFat, t.salt).forEach { require(it > 0 && it < 1000) { "Targets are grams a day, more than 0." } }
        repo.updateSettings { it.copy(extraTargets = t) }
        message.value = "Saved"
    }

    /** The bar and plates for the plate calculator (0.9.0). */
    fun setPlates(p: io.github.randomtools3377.fitness.core.PlateSettings, bar: Double?, plates: List<Double>) = launchSafely {
        io.github.randomtools3377.fitness.core.Plates.problem(bar, plates)?.let { throw IllegalArgumentException(it) }
        repo.updateSettings { it.copy(plates = p) }
        message.value = "Saved"
    }

    fun setTheme(t: io.github.randomtools3377.fitness.core.ThemeSettings) = launchSafely { repo.updateSettings { it.copy(theme = t) } }

    /** Changes the look from what it is now, so quick taps don't undo each other (0.17.0). */
    fun changeTheme(change: (io.github.randomtools3377.fitness.core.ThemeSettings) -> io.github.randomtools3377.fitness.core.ThemeSettings) = launchSafely {
        repo.updateSettings { it.copy(theme = change(it.theme)) }
        io.github.randomtools3377.fitness.widget.TodayWidget.refresh(getApplication())
    }

    fun setAppIcon(icon: io.github.randomtools3377.fitness.core.AppIconChoice) {
        io.github.randomtools3377.fitness.AppIcons.apply(getApplication(), icon)
        changeTheme { it.copy(icon = icon) }
    }

    /** Copies a picked photo in as the diary's background (0.17.0). */
    fun pickBackground(uri: Uri) {
        viewModelScope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                io.github.randomtools3377.fitness.data.BackgroundPhoto.add(getApplication(), uri)
            }
            if (ok) {
                changeTheme { it.copy(backdrop = io.github.randomtools3377.fitness.core.Backdrop.PHOTO, photoStamp = now().toString()) }
            } else {
                message.value = "That photo couldn't be read. Try another one."
            }
        }
    }

    /** Periods, for marking the weight chart (0.12.0). */
    val periods: StateFlow<List<io.github.randomtools3377.fitness.core.Period>> = repo.periods.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setCycleOn(on: Boolean) = launchSafely { repo.updateSettings { it.copy(cycleOn = on) } }

    /** The parts of the app that are on (0.15.0). */
    val features: StateFlow<Set<io.github.randomtools3377.fitness.core.Feature>> =
        settings.map { io.github.randomtools3377.fitness.core.Features.enabled(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.randomtools3377.fitness.core.Features.STARTS_ON)

    /** The optional AI model files opened on this phone (0.16.0). */
    val aiInstalled = MutableStateFlow(io.github.randomtools3377.fitness.ai.AiModels.installed(app))
    val aiInstalling = MutableStateFlow(false)

    fun installAi(uri: Uri) {
        if (aiInstalling.value) return
        aiInstalling.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                io.github.randomtools3377.fitness.ai.AiModels.install(app, uri)
                message.value = "Ready: better label reading is on the phone"
            } catch (e: io.github.randomtools3377.fitness.ai.AiModels.NotAModelFile) {
                message.value = e.message
            } catch (e: Exception) {
                message.value = "That file could not be read. Download it again and try once more."
            } finally {
                aiInstalled.value = io.github.randomtools3377.fitness.ai.AiModels.installed(app)
                aiInstalling.value = false
            }
        }
    }

    fun removeAi(id: String) {
        val app = getApplication<Application>()
        io.github.randomtools3377.fitness.ai.AiModels.remove(app, id)
        aiInstalled.value = io.github.randomtools3377.fitness.ai.AiModels.installed(app)
    }

    fun setFeature(f: io.github.randomtools3377.fitness.core.Feature, isOn: Boolean) = launchSafely {
        repo.updateSettings { s ->
            val now = io.github.randomtools3377.fitness.core.Features.enabled(s)
            val next = io.github.randomtools3377.fitness.core.Features.switch(now, f, isOn)
            var out = s.copy(features = io.github.randomtools3377.fitness.core.Features.encode(next))
            // Parts with their own switch follow this one.
            if (f == io.github.randomtools3377.fitness.core.Feature.CYCLE) out = out.copy(cycleOn = isOn)
            if (f == io.github.randomtools3377.fitness.core.Feature.STEPS && !isOn) out = out.copy(stepsOn = false)
            if (f == io.github.randomtools3377.fitness.core.Feature.MEAL_TARGETS && !isOn) out = out.copy(mealSplit = out.mealSplit.copy(enabled = false))
            out
        }
        if (f == io.github.randomtools3377.fitness.core.Feature.STEPS) refreshSteps()
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
        checkFast()
    }

    fun quickAdd(kcal: Double, name: String, meal: Meal) = launchSafely {
        repo.addEntries(listOf(Diary.quickAdd(kcal, day.value, meal, now(), name)))
        message.value = "Added to ${meal.title.lowercase()}"
        checkFast()
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

    /** "Same as yesterday" (0.7.0): copies what was eaten at [meal] the day before the day shown. */
    fun sameAsYesterday(meal: Meal) = launchSafely {
        val yesterday = day.value.minus(kotlinx.datetime.DatePeriod(days = 1))
        val source = repo.entriesOnce(yesterday).filter { it.meal == meal && !it.planned }
        require(source.isNotEmpty()) { "Nothing was logged at ${meal.title.lowercase()} the day before." }
        repo.addEntries(Diary.copy(source, day.value, now(), meal))
        message.value = "Added ${meal.title.lowercase()} from the day before"
        checkFast()
    }

    // Saved meals.

    fun saveMeal(name: String, meal: Meal) = launchSafely {
        val items = entries.value.filter { it.meal == meal && !it.planned }
        repo.saveMeal(SavedMeal.from(name, items, now()))
        message.value = "Saved \"${name.trim()}\""
    }

    fun logSavedMeal(saved: SavedMeal, meal: Meal) = launchSafely {
        repo.addEntries(saved.toEntries(day.value, meal, now()))
        message.value = "Added ${saved.name} to ${meal.title.lowercase()}"
        checkFast()
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

    /** The diary, weigh-ins, measurements, workouts and sleep as spreadsheets (CSV) in one zip file (0.10.0). */
    fun exportSpreadsheets(uri: Uri) = viewModelScope.launch {
        try {
            val files = repo.spreadsheets(TimeZone.currentSystemDefault())
            withContext(Dispatchers.IO) {
                java.util.zip.ZipOutputStream(resolver.openOutputStream(uri, "wt")!!).use { zip ->
                    for ((name, text) in files) {
                        zip.putNextEntry(java.util.zip.ZipEntry(name))
                        // A byte-order mark, so spreadsheet apps read the accents right.
                        zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        zip.write(text.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
            message.value = "Saved ${files.size} spreadsheets"
        } catch (e: Exception) {
            message.value = "The file could not be written."
        }
    }

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
