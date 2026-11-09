package io.github.randomtools3377.fitness.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import io.github.randomtools3377.fitness.core.Amount
import io.github.randomtools3377.fitness.core.BaseUnit
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.PortionText
import io.github.randomtools3377.fitness.core.SavedMeal
import io.github.randomtools3377.fitness.core.SavedMealItem
import io.github.randomtools3377.fitness.core.WaterEntry
import io.github.randomtools3377.fitness.core.WeighIn
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** A food the user typed in. Nutrients per 100 g or 100 ml. A deleted food is only hidden. */
@Entity(tableName = "foods")
data class FoodRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** The food's portions, as JSON. */
    val portions: String,
    val createdAt: Long,
    val hidden: Boolean = false,
)

@Entity(tableName = "entries", indices = [Index("day")])
data class EntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The day, as days since 1 January 1970. */
    val day: Long,
    val meal: String,
    val foodId: Long?,
    val builtInId: String?,
    val name: String,
    val quantity: Double,
    val portion: String?,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val createdAt: Long,
)

@Entity(tableName = "saved_meals")
data class SavedMealRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [ForeignKey(entity = SavedMealRow::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mealId")],
)
data class SavedMealItemRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val position: Int,
    val foodId: Long?,
    val builtInId: String?,
    val name: String,
    val quantity: Double,
    val portion: String?,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

@Entity(tableName = "water", indices = [Index("day")])
data class WaterRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val ml: Int,
    val at: Long,
)

@Entity(tableName = "weigh_ins", indices = [Index("day")])
data class WeighInRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kg: Double,
    val at: Long,
)

@Entity(tableName = "settings")
data class SettingRow(
    @PrimaryKey val key: String,
    val value: String,
)

/** How often and how lately each food was logged. */
data class UsageRow(val key: String, val count: Int, val lastUsed: Long)

@Dao
interface FitnessDao {
    // Foods the user typed in.
    @Query("SELECT * FROM foods WHERE hidden = 0 ORDER BY name COLLATE NOCASE")
    fun foods(): Flow<List<FoodRow>>

    @Query("SELECT * FROM foods ORDER BY id")
    suspend fun allFoods(): List<FoodRow>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun food(id: Long): FoodRow?

    @Insert
    suspend fun insertFood(food: FoodRow): Long

    @Update
    suspend fun updateFood(food: FoodRow)

    @Query("UPDATE foods SET hidden = 1 WHERE id = :id")
    suspend fun hideFood(id: Long)

    // The diary.
    @Query("SELECT * FROM entries WHERE day = :day ORDER BY createdAt, id")
    fun entriesOn(day: Long): Flow<List<EntryRow>>

    @Query("SELECT * FROM entries WHERE day = :day ORDER BY createdAt, id")
    suspend fun entriesOnce(day: Long): List<EntryRow>

    @Query("SELECT * FROM entries ORDER BY day, createdAt, id")
    suspend fun allEntries(): List<EntryRow>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): EntryRow?

    @Insert
    suspend fun insertEntries(entries: List<EntryRow>)

    @Update
    suspend fun updateEntry(entry: EntryRow)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query(
        "SELECT CASE WHEN builtInId IS NOT NULL THEN 'b:' || builtInId ELSE 'u:' || foodId END AS `key`, " +
            "COUNT(*) AS count, MAX(createdAt) AS lastUsed FROM entries " +
            "WHERE foodId IS NOT NULL OR builtInId IS NOT NULL GROUP BY `key`",
    )
    fun usage(): Flow<List<UsageRow>>

    // Saved meals.
    @Query("SELECT * FROM saved_meals ORDER BY name COLLATE NOCASE")
    fun savedMeals(): Flow<List<SavedMealRow>>

    @Query("SELECT * FROM saved_meal_items ORDER BY mealId, position")
    fun savedMealItems(): Flow<List<SavedMealItemRow>>

    @Query("SELECT * FROM saved_meals ORDER BY id")
    suspend fun allSavedMeals(): List<SavedMealRow>

    @Query("SELECT * FROM saved_meal_items ORDER BY mealId, position")
    suspend fun allSavedMealItems(): List<SavedMealItemRow>

    @Insert
    suspend fun insertSavedMeal(meal: SavedMealRow): Long

    @Insert
    suspend fun insertSavedMealItems(items: List<SavedMealItemRow>)

    @Query("UPDATE saved_meals SET name = :name WHERE id = :id")
    suspend fun renameSavedMeal(id: Long, name: String)

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteSavedMeal(id: Long)

    // Water.
    @Query("SELECT * FROM water WHERE day = :day ORDER BY at, id")
    fun waterOn(day: Long): Flow<List<WaterRow>>

    @Query("SELECT * FROM water ORDER BY day, at, id")
    suspend fun allWater(): List<WaterRow>

    @Insert
    suspend fun insertWater(rows: List<WaterRow>)

    @Query("DELETE FROM water WHERE id = (SELECT id FROM water WHERE day = :day ORDER BY at DESC, id DESC LIMIT 1)")
    suspend fun deleteLastWater(day: Long)

    // Weigh-ins.
    @Query("SELECT * FROM weigh_ins ORDER BY day, at, id")
    fun weighIns(): Flow<List<WeighInRow>>

    @Query("SELECT * FROM weigh_ins ORDER BY day, at, id")
    suspend fun allWeighIns(): List<WeighInRow>

    @Insert
    suspend fun insertWeighIns(rows: List<WeighInRow>)

    @Query("DELETE FROM weigh_ins WHERE id = :id")
    suspend fun deleteWeighIn(id: Long)

    // Settings.
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun setting(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun settingOnce(key: String): String?

    @Upsert
    suspend fun putSetting(row: SettingRow)

    // Import replaces everything.
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM foods") suspend fun clearFoods()
    @Query("DELETE FROM saved_meal_items") suspend fun clearSavedMealItems()
    @Query("DELETE FROM saved_meals") suspend fun clearSavedMeals()
    @Query("DELETE FROM water") suspend fun clearWater()
    @Query("DELETE FROM weigh_ins") suspend fun clearWeighIns()
    @Query("DELETE FROM settings") suspend fun clearSettings()

    @Insert
    suspend fun insertFoods(rows: List<FoodRow>)

    @Insert
    suspend fun insertSavedMeals(rows: List<SavedMealRow>)
}

/**
 * The app's database. Every version's layout is committed in app/schemas, so a later version
 * can move the user's data forward with a migration instead of losing it.
 */
@Database(
    entities = [FoodRow::class, EntryRow::class, SavedMealRow::class, SavedMealItemRow::class, WaterRow::class, WeighInRow::class, SettingRow::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FitnessDao

    companion object {
        const val NAME = "fitness.db"

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}

// Between rows and the shared models.

fun LocalDate.toDay(): Long = toEpochDays().toLong()
fun dayOf(n: Long): LocalDate = LocalDate.fromEpochDays(n.toInt())

fun FoodRow.toCore() = Food(
    id = id, name = name, brand = brand, unit = BaseUnit.valueOf(unit),
    per100 = Nutrients(kcal, protein, carbs, fat), portions = PortionText.decode(portions), hidden = hidden,
)

fun Food.toRow(createdAt: Long) = FoodRow(
    id = id, name = name, brand = brand, unit = unit.name,
    kcal = per100.kcal, protein = per100.protein, carbs = per100.carbs, fat = per100.fat,
    portions = PortionText.encode(portions), createdAt = createdAt, hidden = hidden,
)

fun EntryRow.toCore() = DiaryEntry(
    id = id, day = dayOf(day), meal = Meal.valueOf(meal), foodId = foodId, builtInId = builtInId, name = name,
    amount = Amount(quantity, portion), unit = BaseUnit.valueOf(unit), nutrients = Nutrients(kcal, protein, carbs, fat),
    createdAt = createdAt,
)

fun DiaryEntry.toRow() = EntryRow(
    id = id, day = day.toDay(), meal = meal.name, foodId = foodId, builtInId = builtInId, name = name,
    quantity = amount.quantity, portion = amount.portion, unit = unit.name,
    kcal = nutrients.kcal, protein = nutrients.protein, carbs = nutrients.carbs, fat = nutrients.fat, createdAt = createdAt,
)

fun SavedMealItemRow.toCore() = SavedMealItem(
    foodId = foodId, builtInId = builtInId, name = name, amount = Amount(quantity, portion), unit = BaseUnit.valueOf(unit),
    nutrients = Nutrients(kcal, protein, carbs, fat),
)

fun SavedMealItem.toRow(mealId: Long, position: Int) = SavedMealItemRow(
    mealId = mealId, position = position, foodId = foodId, builtInId = builtInId, name = name,
    quantity = amount.quantity, portion = amount.portion, unit = unit.name,
    kcal = nutrients.kcal, protein = nutrients.protein, carbs = nutrients.carbs, fat = nutrients.fat,
)

fun savedMealsOf(meals: List<SavedMealRow>, items: List<SavedMealItemRow>): List<SavedMeal> {
    val byMeal = items.groupBy { it.mealId }
    return meals.map { m -> SavedMeal(m.id, m.name, byMeal[m.id].orEmpty().sortedBy { it.position }.map { it.toCore() }, m.createdAt) }
}

fun WaterRow.toCore() = WaterEntry(id, dayOf(day), ml, at)
fun WaterEntry.toRow() = WaterRow(id, day.toDay(), ml, at)
fun WeighInRow.toCore() = WeighIn(id, dayOf(day), kg, at)
fun WeighIn.toRow() = WeighInRow(id, day.toDay(), kg, at)
