package io.github.randomtools3377.fitness.data

import android.content.Context
import androidx.room.AutoMigration
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
import io.github.randomtools3377.fitness.core.CardioType
import io.github.randomtools3377.fitness.core.Exercise
import io.github.randomtools3377.fitness.core.ExerciseKind
import io.github.randomtools3377.fitness.core.Exercises
import io.github.randomtools3377.fitness.core.SavedWorkout
import io.github.randomtools3377.fitness.core.SavedWorkoutItem
import io.github.randomtools3377.fitness.core.Workout
import io.github.randomtools3377.fitness.core.WorkoutExercise
import io.github.randomtools3377.fitness.core.WorkoutSet
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

/** An exercise of the user's own. The built-in ones live in the shared code. */
@Entity(tableName = "exercises")
data class ExerciseRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val hidden: Boolean = false,
)

/** Rest between sets for an exercise whose rest was changed, by its key ("b:squat", "u:3"). */
@Entity(tableName = "rest_times")
data class RestRow(
    @PrimaryKey val exerciseKey: String,
    val seconds: Int,
)

@Entity(tableName = "workouts", indices = [Index("day")])
data class WorkoutRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val name: String,
    val startedAt: Long,
    /** Null while the workout is in progress. */
    val endedAt: Long?,
    val savedWorkoutId: Long?,
    val burnedKcal: Double,
)

/** One set of one exercise in a workout. The exercise is copied in, so the row stands alone. */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutRow::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId")],
)
data class WorkoutSetRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    /** The exercise's place in the workout. */
    val position: Int,
    val setIndex: Int,
    val exerciseKey: String,
    val exerciseName: String,
    val kind: String,
    val met: Double,
    val muscle: String,
    val cardio: String,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val distanceKm: Double?,
    val done: Boolean,
    val doneAt: Long?,
)

@Entity(tableName = "saved_workouts")
data class SavedWorkoutRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "saved_workout_items",
    foreignKeys = [ForeignKey(entity = SavedWorkoutRow::class, parentColumns = ["id"], childColumns = ["savedId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("savedId")],
)
data class SavedWorkoutItemRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedId: Long,
    val position: Int,
    val exerciseKey: String,
    val exerciseName: String,
    val kind: String,
    val met: Double,
    val muscle: String,
    val cardio: String,
    val sets: Int,
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

    // Exercises of the user's own, and rest times.
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun exercises(): Flow<List<ExerciseRow>>

    @Query("SELECT * FROM exercises ORDER BY id")
    suspend fun allExercises(): List<ExerciseRow>

    @Insert
    suspend fun insertExercise(row: ExerciseRow): Long

    @Insert
    suspend fun insertExercises(rows: List<ExerciseRow>)

    @Query("UPDATE exercises SET hidden = 1 WHERE id = :id")
    suspend fun hideExercise(id: Long)

    @Query("SELECT * FROM rest_times")
    fun restTimes(): Flow<List<RestRow>>

    @Query("SELECT * FROM rest_times")
    suspend fun allRestTimes(): List<RestRow>

    @Upsert
    suspend fun putRest(row: RestRow)

    // Workouts.
    @Query("SELECT * FROM workouts ORDER BY startedAt")
    fun workouts(): Flow<List<WorkoutRow>>

    @Query("SELECT * FROM workout_sets ORDER BY workoutId, position, setIndex")
    fun workoutSets(): Flow<List<WorkoutSetRow>>

    @Query("SELECT * FROM workouts ORDER BY startedAt")
    suspend fun allWorkouts(): List<WorkoutRow>

    @Query("SELECT * FROM workout_sets ORDER BY workoutId, position, setIndex")
    suspend fun allWorkoutSets(): List<WorkoutSetRow>

    @Insert
    suspend fun insertWorkout(row: WorkoutRow): Long

    @Update
    suspend fun updateWorkout(row: WorkoutRow)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("DELETE FROM workout_sets WHERE workoutId = :id")
    suspend fun deleteSetsOf(id: Long)

    @Insert
    suspend fun insertWorkoutSets(rows: List<WorkoutSetRow>)

    // Saved workouts.
    @Query("SELECT * FROM saved_workouts ORDER BY name COLLATE NOCASE")
    fun savedWorkouts(): Flow<List<SavedWorkoutRow>>

    @Query("SELECT * FROM saved_workout_items ORDER BY savedId, position")
    fun savedWorkoutItems(): Flow<List<SavedWorkoutItemRow>>

    @Query("SELECT * FROM saved_workouts ORDER BY id")
    suspend fun allSavedWorkouts(): List<SavedWorkoutRow>

    @Query("SELECT * FROM saved_workout_items ORDER BY savedId, position")
    suspend fun allSavedWorkoutItems(): List<SavedWorkoutItemRow>

    @Insert
    suspend fun insertSavedWorkout(row: SavedWorkoutRow): Long

    @Insert
    suspend fun insertSavedWorkoutItems(rows: List<SavedWorkoutItemRow>)

    @Query("DELETE FROM saved_workouts WHERE id = :id")
    suspend fun deleteSavedWorkout(id: Long)

    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM rest_times") suspend fun clearRestTimes()
    @Query("DELETE FROM workout_sets") suspend fun clearWorkoutSets()
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM saved_workout_items") suspend fun clearSavedWorkoutItems()
    @Query("DELETE FROM saved_workouts") suspend fun clearSavedWorkouts()

    @Insert
    suspend fun insertSavedMeals(rows: List<SavedMealRow>)
}

/**
 * The app's database. Every version's layout is committed in app/schemas, so a later version
 * can move the user's data forward with a migration instead of losing it.
 */
@Database(
    entities = [
        FoodRow::class, EntryRow::class, SavedMealRow::class, SavedMealItemRow::class, WaterRow::class, WeighInRow::class, SettingRow::class,
        // Version 2 (0.2.0): workouts.
        ExerciseRow::class, RestRow::class, WorkoutRow::class, WorkoutSetRow::class, SavedWorkoutRow::class, SavedWorkoutItemRow::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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

fun ExerciseRow.toCore() = Exercises.own(id, name, ExerciseKind.valueOf(kind))

private fun exerciseOf(key: String, name: String, kind: String, met: Double, muscle: String, cardio: String) =
    Exercise(key, name, ExerciseKind.valueOf(kind), met, muscle, CardioType.valueOf(cardio))

fun workoutsOf(rows: List<WorkoutRow>, sets: List<WorkoutSetRow>): List<Workout> {
    val byWorkout = sets.groupBy { it.workoutId }
    return rows.map { r ->
        val exercises = byWorkout[r.id].orEmpty().groupBy { it.position }.toSortedMap().values.map { list ->
            val f = list.first()
            WorkoutExercise(
                exerciseOf(f.exerciseKey, f.exerciseName, f.kind, f.met, f.muscle, f.cardio),
                list.sortedBy { it.setIndex }.map { WorkoutSet(it.weightKg, it.reps, it.seconds, it.distanceKm, it.done, it.doneAt) },
            )
        }
        Workout(r.id, r.name, dayOf(r.day), r.startedAt, r.endedAt, exercises, r.savedWorkoutId, r.burnedKcal)
    }
}

fun Workout.toRow() = WorkoutRow(id, day.toDay(), name, startedAt, endedAt, savedWorkoutId, burnedKcal)

fun Workout.setRows(workoutId: Long): List<WorkoutSetRow> = exercises.flatMapIndexed { position, we ->
    we.sets.mapIndexed { i, s ->
        WorkoutSetRow(
            workoutId = workoutId, position = position, setIndex = i,
            exerciseKey = we.exercise.key, exerciseName = we.exercise.name, kind = we.exercise.kind.name, met = we.exercise.met,
            muscle = we.exercise.muscle, cardio = we.exercise.cardio.name,
            weightKg = s.weightKg, reps = s.reps, seconds = s.seconds, distanceKm = s.distanceKm, done = s.done, doneAt = s.doneAt,
        )
    }
}

fun savedWorkoutsOf(rows: List<SavedWorkoutRow>, items: List<SavedWorkoutItemRow>): List<SavedWorkout> {
    val bySaved = items.groupBy { it.savedId }
    return rows.map { r ->
        SavedWorkout(
            r.id, r.name,
            bySaved[r.id].orEmpty().sortedBy { it.position }.map {
                SavedWorkoutItem(exerciseOf(it.exerciseKey, it.exerciseName, it.kind, it.met, it.muscle, it.cardio), it.sets)
            },
            r.createdAt,
        )
    }
}

fun SavedWorkout.itemRows(savedId: Long) = items.mapIndexed { i, it ->
    SavedWorkoutItemRow(
        savedId = savedId, position = i, exerciseKey = it.exercise.key, exerciseName = it.exercise.name, kind = it.exercise.kind.name,
        met = it.exercise.met, muscle = it.exercise.muscle, cardio = it.exercise.cardio.name, sets = it.sets,
    )
}
