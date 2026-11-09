package io.github.randomtools3377.fitness.data

import android.content.Context
import androidx.room.withTransaction
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.Backup
import io.github.randomtools3377.fitness.core.BuiltInFoods
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.SavedMeal
import io.github.randomtools3377.fitness.core.Usage
import io.github.randomtools3377.fitness.core.WaterEntry
import io.github.randomtools3377.fitness.core.WeighIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Everything the screens read and change, in terms of the shared models. */
class Repository(private val context: Context, val db: AppDatabase) {
    private val dao = db.dao()

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

    /** Takes a food off the lists. What was already logged keeps its numbers. */
    suspend fun deleteFood(id: Long) = dao.hideFood(id)

    /** The food an entry came from, built-in or typed in, if it still exists. */
    suspend fun foodOf(entry: DiaryEntry): Food? = when {
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

    // Weigh-ins.
    val weighIns: Flow<List<WeighIn>> = dao.weighIns().map { r -> r.map { it.toCore() } }
    suspend fun weighInsOnce(): List<WeighIn> = dao.allWeighIns().map { it.toCore() }
    suspend fun addWeighIn(w: WeighIn) = dao.insertWeighIns(listOf(w.toRow()))
    suspend fun deleteWeighIn(id: Long) = dao.deleteWeighIn(id)

    // Settings, kept as one JSON value in the same form as the backup file.
    val settings: Flow<AppSettings> = dao.setting(SETTINGS_KEY).map { AppSettings.decode(it) }
    suspend fun settingsOnce(): AppSettings = AppSettings.decode(dao.settingOnce(SETTINGS_KEY))

    private val settingsLock = Mutex()

    /** Changes the settings in one step, so two quick changes can't undo each other. */
    suspend fun updateSettings(change: (AppSettings) -> AppSettings) = settingsLock.withLock {
        dao.putSetting(SettingRow(SETTINGS_KEY, AppSettings.encode(change(settingsOnce()))))
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
            dao.insertFoods(backup.foods.map { it.toRow(now) })
            dao.insertEntries(backup.entries.map { it.toRow() })
            for (meal in backup.savedMeals) {
                val id = dao.insertSavedMeal(SavedMealRow(name = meal.name, createdAt = meal.createdAt))
                dao.insertSavedMealItems(meal.items.mapIndexed { i, item -> item.toRow(id, i) })
            }
            dao.insertWater(backup.water.map { it.toRow() })
            dao.insertWeighIns(backup.weighIns.map { it.toRow() })
            dao.putSetting(SettingRow(SETTINGS_KEY, AppSettings.encode(backup.settings)))
        }
    }

    companion object {
        const val FOODS_FILE = "foods.tsv"
        const val SETTINGS_KEY = "settings"
    }
}
