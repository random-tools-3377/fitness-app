package io.github.randomtools3377.fitness.data

import android.content.Context
import androidx.room.withTransaction
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.Backup
import io.github.randomtools3377.fitness.core.BuiltInFoods
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Exercise
import io.github.randomtools3377.fitness.core.ExerciseKind
import io.github.randomtools3377.fitness.core.Exercises
import io.github.randomtools3377.fitness.core.SavedWorkout
import io.github.randomtools3377.fitness.core.Workout
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Fast
import io.github.randomtools3377.fitness.core.Plan
import io.github.randomtools3377.fitness.core.Product
import io.github.randomtools3377.fitness.core.Recipe
import io.github.randomtools3377.fitness.core.ShoppingItem
import io.github.randomtools3377.fitness.core.SavedMeal
import io.github.randomtools3377.fitness.core.Usage
import io.github.randomtools3377.fitness.core.WaterEntry
import io.github.randomtools3377.fitness.core.WeighIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Everything the screens read and change, in terms of the shared models. */
class Repository(private val context: Context, val db: AppDatabase) {
    private val dao = db.dao()
    private val well = db.wellbeingDao()

    // Built-in foods: read from the app's own file the first time they are needed.
    private val builtInLock = Mutex()
    @Volatile private var builtIns: List<Food>? = null
    @Volatile private var builtInById: Map<String, Food> = emptyMap()

    suspend fun builtInFoods(): List<Food> = builtIns ?: builtInLock.withLock {
        builtIns ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(FOODS_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            BuiltInFoods.parse(text).also { list ->
                builtInById = list.associateBy { it.builtInId!! }
                builtIns = list
            }
        }
    }

    suspend fun builtInFood(id: String): Food? {
        builtInFoods()
        return builtInById[id]
    }

    // Foods the user typed in.
    val foods: Flow<List<Food>> = dao.foods().map { rows -> rows.map { it.toCore() } }

    suspend fun food(id: Long): Food? = dao.food(id)?.toCore()

    /** Saves a new food (id 0) or changes one, and gives back its id. */
    suspend fun saveFood(food: Food, now: Long): Long =
        if (food.id == 0L) {
            dao.insertFood(food.toRow(now))
        } else {
            val old = dao.food(food.id)
            dao.updateFood(food.copy(hidden = old?.hidden ?: false).toRow(old?.createdAt ?: now))
            food.id
        }

    /** Adds several new foods at once, as one step. */
    suspend fun addFoods(foods: List<Food>, now: Long) {
        db.withTransaction { for (f in foods) dao.insertFood(f.copy(id = 0).toRow(now)) }
    }

    // Barcodes and country files (0.3.0).
    private val products = db.productDao()
    val countries = products.countries()

    /** The food a barcode belongs to: one of the user's own first, then the country files. */
    suspend fun foodByBarcode(code: String): Food? =
        dao.foodByBarcode(code)?.toCore() ?: products.product(code)?.toCore()?.toFood()

    /** Products already used, so they show among recent foods. */
    suspend fun productFoods(barcodes: List<String>): List<Food> =
        if (barcodes.isEmpty()) emptyList() else barcodes.chunked(500).flatMap { products.products(it) }.distinctBy { it.barcode }.map { it.toCore().toFood() }

    suspend fun importCountry(resolver: android.content.ContentResolver, uri: android.net.Uri, now: Long, progress: (Int) -> Unit) =
        CountryFiles.import(db, resolver, uri, now, progress)

    suspend fun removeCountry(name: String) = db.withTransaction {
        products.deleteCountryProducts(name)
        products.deleteCountry(name)
    }

    /** Takes a food off the lists. What was already logged keeps its numbers. */
    suspend fun deleteFood(id: Long) = dao.hideFood(id)

    /** The food an entry came from, built-in or typed in, if it still exists. */
    suspend fun foodOf(entry: DiaryEntry): Food? = when {
        entry.builtInId?.startsWith(Recipe.PREFIX) == true -> entry.builtInId!!.drop(1).toLongOrNull()?.let { dao.recipe(it) }?.toCore()?.toFood()
        entry.builtInId?.startsWith(Product.PREFIX) == true -> products.product(entry.builtInId!!.drop(1))?.toCore()?.toFood()
        entry.builtInId != null -> builtInFood(entry.builtInId!!)
        entry.foodId != null -> dao.food(entry.foodId!!)?.toCore()
        else -> null
    }

    val usage: Flow<Map<String, Usage>> =
        dao.usage().map { rows -> rows.associate { it.key to Usage(it.key, it.count, it.lastUsed) } }

    // The diary.
    fun entries(day: LocalDate): Flow<List<DiaryEntry>> = dao.entriesOn(day.toDay()).map { r -> r.map { it.toCore() } }
    suspend fun entriesOnce(day: LocalDate): List<DiaryEntry> = dao.entriesOnce(day.toDay()).map { it.toCore() }
    suspend fun addEntries(entries: List<DiaryEntry>) = dao.insertEntries(entries.map { it.toRow() })
    suspend fun updateEntry(entry: DiaryEntry) = dao.updateEntry(entry.toRow())
    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    /** The whole diary and all water, for the stats and streaks (0.4.0). */
    val allEntries: Flow<List<DiaryEntry>> = dao.entriesFlow().map { r -> r.map { it.toCore() } }
    val allWater: Flow<List<WaterEntry>> = dao.waterFlow().map { r -> r.map { it.toCore() } }

    // Planning, recipes and the shopping list (0.5.0).
    fun entriesBetween(from: LocalDate, to: LocalDate): Flow<List<DiaryEntry>> =
        dao.entriesBetween(from.toDay(), to.toDay()).map { r -> r.map { it.toCore() } }

    suspend fun entriesBetweenOnce(from: LocalDate, to: LocalDate): List<DiaryEntry> =
        (from.toEpochDays()..to.toEpochDays()).flatMap { dao.entriesOnce(it.toLong()) }.map { it.toCore() }

    val recipes: Flow<List<Recipe>> = dao.recipes().map { r -> r.map { it.toCore() } }
    suspend fun recipe(id: Long): Recipe? = dao.recipe(id)?.toCore()
    suspend fun saveRecipe(r: Recipe): Long = if (r.id == 0L) dao.putRecipe(r.toRow()) else { dao.putRecipe(r.toRow()); r.id }
    suspend fun deleteRecipe(id: Long) = dao.hideRecipe(id)

    val shopping: Flow<List<ShoppingItem>> = dao.shopping().map { r -> r.map { it.toCore() } }
    suspend fun tickShopping(id: Long, ticked: Boolean) = dao.tickShopping(id, ticked)
    suspend fun addOwnShopping(name: String) = dao.insertShopping(listOf(ShoppingItem(name = name.trim(), own = true).toRow()))
    suspend fun deleteShopping(id: Long) = dao.deleteShopping(id)

    /**
     * Makes a new shopping list from the planned items between two days: ticked items and the
     * old list's foods go; the user's own items that aren't ticked stay.
     */
    suspend fun makeShoppingList(from: LocalDate, to: LocalDate) {
        val planned = dao.plannedBetween(from.toDay(), to.toDay()).map { it.toCore() }
        val recipes = dao.allRecipes().associate { it.id to it.toCore() }
        builtInFoods()
        val foods = planned.associateWith { e -> runCatching { foodOf(e) }.getOrNull() }
        val list = Plan.shopping(planned, recipes) { e ->
            runCatching { foods[e]?.let { f -> e.amount.baseAmount(f) } }.getOrNull() ?: e.amount.takeIf { it.portion == null }?.quantity
        }
        db.withTransaction {
            dao.clearMadeAndTicked()
            dao.insertShopping(list.map { it.toRow() })
        }
    }

    // Fasting (0.6.0).
    val fasts: Flow<List<Fast>> = dao.fasts().map { r -> r.map { it.toCore() } }
    suspend fun fastsOnce(): List<Fast> = dao.allFasts().map { it.toCore() }
    suspend fun saveFast(f: Fast): Long = if (f.id == 0L) dao.putFast(f.toRow()) else { dao.putFast(f.toRow()); f.id }
    suspend fun deleteFast(id: Long) = dao.deleteFast(id)

    // Saved meals.
    val savedMeals: Flow<List<SavedMeal>> = combine(dao.savedMeals(), dao.savedMealItems()) { m, i -> savedMealsOf(m, i) }

    suspend fun saveMeal(meal: SavedMeal) {
        db.withTransaction {
            val id = dao.insertSavedMeal(SavedMealRow(name = meal.name, createdAt = meal.createdAt))
            dao.insertSavedMealItems(meal.items.mapIndexed { i, item -> item.toRow(id, i) })
        }
    }

    suspend fun renameSavedMeal(id: Long, name: String) = dao.renameSavedMeal(id, name)
    suspend fun deleteSavedMeal(id: Long) = dao.deleteSavedMeal(id)

    // Water.
    fun water(day: LocalDate): Flow<List<WaterEntry>> = dao.waterOn(day.toDay()).map { r -> r.map { it.toCore() } }
    suspend fun addWater(day: LocalDate, ml: Int, now: Long) = dao.insertWater(listOf(WaterRow(day = day.toDay(), ml = ml, at = now)))
    suspend fun undoWater(day: LocalDate) = dao.deleteLastWater(day.toDay())
    suspend fun waterOn(day: LocalDate): Int = dao.waterOn(day.toDay()).first().sumOf { it.ml }

    // Steps from Health Connect (0.10.0).

    /** The day's steps that count for calories: none during a logged workout. 0 when steps are off. */
    suspend fun stepsOn(day: LocalDate, s: AppSettings): Long {
        if (!s.stepsOn) return 0
        val records = io.github.randomtools3377.fitness.steps.HealthSteps.day(context, day)
        val during = workouts.first().filter { it.day == day && it.finished }.map { it.startedAt to (it.endedAt ?: it.startedAt) }
        return io.github.randomtools3377.fitness.core.Steps.countable(records, during)
    }

    /** All the day's steps, for showing. */
    suspend fun allStepsOn(day: LocalDate, s: AppSettings): Long =
        if (!s.stepsOn) 0 else io.github.randomtools3377.fitness.steps.HealthSteps.day(context, day).sumOf { it.count }

    suspend fun stepsKcal(day: LocalDate, s: AppSettings): Double {
        val steps = stepsOn(day, s)
        if (steps <= 0) return 0.0
        val kg = io.github.randomtools3377.fitness.core.Weight.latest(weighInsOnce())?.kg ?: s.profile?.weightKg ?: return 0.0
        return io.github.randomtools3377.fitness.core.Steps.kcal(steps, kg, s.burnedHalf)
    }

    // Measurements, sleep and mood, habits and progress photos (0.8.0).
    val measurements: Flow<List<io.github.randomtools3377.fitness.core.Measurement>> = well.measurements().map { r -> r.map { it.toCore() } }
    suspend fun addMeasurements(list: List<io.github.randomtools3377.fitness.core.Measurement>) = well.insertMeasurements(list.map { it.toRow() })
    suspend fun deleteMeasurement(id: Long) = well.deleteMeasurement(id)

    val dayNotes: Flow<List<io.github.randomtools3377.fitness.core.DayNote>> = well.dayNotes().map { r -> r.map { it.toCore() } }
    suspend fun putDayNote(n: io.github.randomtools3377.fitness.core.DayNote) =
        if (n.isEmpty) well.deleteDayNote(n.day.toDay()) else well.putDayNotes(listOf(n.toRow()))

    val habits: Flow<List<io.github.randomtools3377.fitness.core.Habit>> = well.habits().map { r -> r.map { it.toCore() } }
    val habitTicks: Flow<List<io.github.randomtools3377.fitness.core.HabitTick>> = well.habitTicks().map { r -> r.map { it.toCore() } }
    suspend fun addHabit(h: io.github.randomtools3377.fitness.core.Habit) = well.insertHabit(h.toRow())
    suspend fun putHabit(h: io.github.randomtools3377.fitness.core.Habit) = well.putHabits(listOf(h.toRow()))
    suspend fun tickHabit(habitId: Long, day: kotlinx.datetime.LocalDate, done: Boolean) =
        if (done) well.insertHabitTicks(listOf(HabitTickRow(habitId, day.toDay()))) else well.deleteHabitTick(habitId, day.toDay())

    // Cycle tracking (0.12.0).
    val periods: Flow<List<io.github.randomtools3377.fitness.core.Period>> = well.periods().map { r -> r.map { it.toCore() } }
    suspend fun putPeriod(p: io.github.randomtools3377.fitness.core.Period) = well.putPeriods(listOf(p.toRow()))
    suspend fun deletePeriod(id: Long) = well.deletePeriod(id)

    val photos: Flow<List<PhotoRow>> = well.photos()
    suspend fun addPhoto(uri: android.net.Uri, day: kotlinx.datetime.LocalDate, now: Long): Long {
        val name = Photos.keep(context, uri, now)
        return well.insertPhoto(PhotoRow(day = day.toDay(), file = name, at = now))
    }
    suspend fun deletePhoto(id: Long) {
        well.photo(id)?.let { Photos.delete(context, it.file) }
        well.deletePhoto(id)
    }
    suspend fun savePhotoCopy(id: Long, to: android.net.Uri) {
        val p = well.photo(id) ?: return
        Photos.saveCopy(context, p.file, to)
    }

    // Weigh-ins.
    val weighIns: Flow<List<WeighIn>> = dao.weighIns().map { r -> r.map { it.toCore() } }
    suspend fun weighInsOnce(): List<WeighIn> = dao.allWeighIns().map { it.toCore() }
    suspend fun addWeighIn(w: WeighIn) = dao.insertWeighIns(listOf(w.toRow()))
    suspend fun deleteWeighIn(id: Long) = dao.deleteWeighIn(id)

    // Workouts.
    val workouts: Flow<List<Workout>> = combine(dao.workouts(), dao.workoutSets()) { w, s -> workoutsOf(w, s) }
    suspend fun workoutsOnce(): List<Workout> = workoutsOf(dao.allWorkouts(), dao.allWorkoutSets())

    /** Saves a workout as it is now, sets and all, and gives back its id. */
    suspend fun saveWorkout(w: Workout): Long = db.withTransaction {
        val id = if (w.id == 0L) dao.insertWorkout(w.toRow()) else { dao.updateWorkout(w.toRow()); w.id }
        dao.deleteSetsOf(id)
        dao.insertWorkoutSets(w.setRows(id))
        id
    }

    suspend fun deleteWorkout(id: Long) = dao.deleteWorkout(id)

    val savedWorkouts: Flow<List<SavedWorkout>> =
        combine(dao.savedWorkouts(), dao.savedWorkoutItems()) { r, i -> savedWorkoutsOf(r, i) }

    suspend fun saveSavedWorkout(sw: SavedWorkout): Long = db.withTransaction {
        val id = dao.insertSavedWorkout(SavedWorkoutRow(name = sw.name, createdAt = sw.createdAt))
        dao.insertSavedWorkoutItems(sw.itemRows(id))
        id
    }

    suspend fun deleteSavedWorkout(id: Long) = dao.deleteSavedWorkout(id)

    /** The user's own exercises (not deleted). */
    val ownExercises: Flow<List<Exercise>> = dao.exercises().map { rows -> rows.filter { !it.hidden }.map { it.toCore() } }

    suspend fun addExercise(name: String, kind: ExerciseKind): Exercise {
        val id = dao.insertExercise(ExerciseRow(name = name.trim(), kind = kind.name))
        return Exercises.own(id, name, kind)
    }

    suspend fun deleteExercise(e: Exercise) {
        e.key.removePrefix("u:").toLongOrNull()?.let { dao.hideExercise(it) }
    }

    val restTimes: Flow<Map<String, Int>> = dao.restTimes().map { rows -> rows.associate { it.exerciseKey to it.seconds } }
    suspend fun setRest(key: String, seconds: Int) = dao.putRest(RestRow(key, seconds))

    // Settings, kept as one JSON value in the same form as the backup file.
    val settings: Flow<AppSettings> = dao.setting(SETTINGS_KEY).map { AppSettings.decode(it) }
    suspend fun settingsOnce(): AppSettings = AppSettings.decode(dao.settingOnce(SETTINGS_KEY))

    private val settingsLock = Mutex()

    /** Changes the settings in one step, so two quick changes can't undo each other. */
    suspend fun updateSettings(change: (AppSettings) -> AppSettings) = settingsLock.withLock {
        dao.putSetting(SettingRow(SETTINGS_KEY, AppSettings.encode(change(settingsOnce()))))
    }

    /** Spreadsheet files of everything, for reading elsewhere (0.10.0). */
    suspend fun spreadsheets(tz: kotlinx.datetime.TimeZone): Map<String, String> = db.withTransaction {
        io.github.randomtools3377.fitness.core.CsvExport.all(
            entries = dao.allEntries().map { it.toCore() },
            weighIns = dao.allWeighIns().map { it.toCore() },
            measurements = well.allMeasurements().map { it.toCore() },
            workouts = workoutsOf(dao.allWorkouts(), dao.allWorkoutSets()),
            notes = well.allDayNotes().map { it.toCore() },
            tz = tz,
        )
    }

    /** What the data shows is in use, for the first choice of parts to show (0.15.0). */
    suspend fun featureUse(): io.github.randomtools3377.fitness.core.FeatureUse {
        val habits = well.allHabits()
        return io.github.randomtools3377.fitness.core.FeatureUse(
            hasCountries = products.countries().first().isNotEmpty(),
            hasBarcodeFoods = dao.allFoods().any { it.barcode != null },
            hasRecipesOrPlans = dao.allRecipes().isNotEmpty() || dao.allEntries().any { it.planned } || dao.allShopping().isNotEmpty(),
            hasFasts = dao.allFasts().isNotEmpty(),
            hasMeasurementsOrPhotos = well.allMeasurements().isNotEmpty() || well.photos().first().isNotEmpty(),
            hasSleepOrMood = well.allDayNotes().isNotEmpty(),
            hasHabits = habits.any { it.kind == "HABIT" },
            hasSupplements = habits.any { it.kind == "SUPPLEMENT" },
            hasPeriods = well.allPeriods().isNotEmpty(),
        )
    }

    // Export and import.
    suspend fun export(now: Long): String {
        val backup = db.withTransaction {
            Backup(
                exportedAt = now,
                foods = dao.allFoods().map { it.toCore() },
                entries = dao.allEntries().map { it.toCore() },
                savedMeals = savedMealsOf(dao.allSavedMeals(), dao.allSavedMealItems()),
                water = dao.allWater().map { it.toCore() },
                weighIns = dao.allWeighIns().map { it.toCore() },
                settings = settingsOnce(),
                exercises = dao.allExercises().filter { !it.hidden }.map { it.toCore() },
                workouts = workoutsOf(dao.allWorkouts(), dao.allWorkoutSets()),
                savedWorkouts = savedWorkoutsOf(dao.allSavedWorkouts(), dao.allSavedWorkoutItems()),
                restSeconds = dao.allRestTimes().associate { it.exerciseKey to it.seconds },
                recipes = dao.allRecipes().filter { !it.hidden }.map { it.toCore() },
                shopping = dao.allShopping().map { it.toCore() },
                fasts = dao.allFasts().map { it.toCore() },
                measurements = well.allMeasurements().map { it.toCore() },
                dayNotes = well.allDayNotes().map { it.toCore() },
                habits = well.allHabits().map { it.toCore() },
                habitTicks = well.allHabitTicks().map { it.toCore() },
                periods = well.allPeriods().map { it.toCore() },
            )
        }
        return Backup.encode(backup)
    }

    /**
     * Replaces everything with the backup's contents. Foods keep their ids, so diary entries and
     * saved meals still point to the right foods. Hidden (deleted) foods come back hidden.
     */
    suspend fun import(backup: Backup, now: Long) {
        db.withTransaction {
            dao.clearEntries()
            dao.clearSavedMealItems()
            dao.clearSavedMeals()
            dao.clearFoods()
            dao.clearWater()
            dao.clearWeighIns()
            dao.clearSettings()
            dao.clearWorkoutSets()
            dao.clearWorkouts()
            dao.clearSavedWorkoutItems()
            dao.clearSavedWorkouts()
            dao.clearExercises()
            dao.clearRestTimes()
            dao.clearRecipes()
            dao.clearShopping()
            dao.clearFasts()
            well.clearMeasurements()
            well.clearDayNotes()
            well.clearHabitTicks()
            well.clearHabits()
            well.insertMeasurements(backup.measurements.map { it.toRow() })
            well.putDayNotes(backup.dayNotes.map { it.toRow() })
            well.putHabits(backup.habits.map { it.toRow() })
            well.insertHabitTicks(backup.habitTicks.map { it.toRow() })
            well.clearPeriods()
            well.putPeriods(backup.periods.map { it.toRow() })
            dao.insertFoods(backup.foods.map { it.toRow(now) })
            dao.insertEntries(backup.entries.map { it.toRow() })
            for (meal in backup.savedMeals) {
                val id = dao.insertSavedMeal(SavedMealRow(name = meal.name, createdAt = meal.createdAt))
                dao.insertSavedMealItems(meal.items.mapIndexed { i, item -> item.toRow(id, i) })
            }
            dao.insertWater(backup.water.map { it.toRow() })
            dao.insertWeighIns(backup.weighIns.map { it.toRow() })
            dao.putSetting(SettingRow(SETTINGS_KEY, AppSettings.encode(backup.settings)))
            // The user's own exercises keep their ids, so workouts still point to them.
            dao.insertExercises(
                backup.exercises.mapNotNull { e ->
                    e.key.removePrefix("u:").toLongOrNull()?.let { ExerciseRow(id = it, name = e.name, kind = e.kind.name) }
                },
            )
            for (w in backup.workouts) {
                val id = dao.insertWorkout(w.toRow())
                dao.insertWorkoutSets(w.setRows(id))
            }
            for (sw in backup.savedWorkouts) {
                val id = dao.insertSavedWorkout(SavedWorkoutRow(id = sw.id, name = sw.name, createdAt = sw.createdAt))
                dao.insertSavedWorkoutItems(sw.itemRows(id))
            }
            for ((key, sec) in backup.restSeconds) dao.putRest(RestRow(key, sec))
            // Recipes keep their ids, so planned and logged items still point to them.
            for (r in backup.recipes) dao.putRecipe(r.toRow())
            dao.insertShopping(backup.shopping.map { it.copy(id = 0).toRow() })
            for (f in backup.fasts) dao.putFast(f.toRow())
        }
    }

    companion object {
        const val FOODS_FILE = "foods.tsv"
        const val SETTINGS_KEY = "settings"
    }
}
