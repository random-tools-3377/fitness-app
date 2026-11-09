package io.github.randomtools3377.fitness.data

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import io.github.randomtools3377.fitness.core.DayNote
import io.github.randomtools3377.fitness.core.Habit
import io.github.randomtools3377.fitness.core.HabitTick
import io.github.randomtools3377.fitness.core.MeasureKind
import io.github.randomtools3377.fitness.core.Measurement
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// Version 7 (0.8.0): body measurements, sleep and mood, the user's own habits, progress photos.

@Entity(tableName = "measurements", indices = [Index("day")])
data class MeasurementRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kind: String,
    val value: Double,
    val at: Long,
)

@Entity(tableName = "day_notes")
data class DayNoteRow(
    @PrimaryKey val day: Long,
    val sleepHours: Double?,
    val mood: Int?,
    val text: String,
)

@Entity(tableName = "habits")
data class HabitRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val archived: Boolean,
)

@Entity(tableName = "habit_ticks", primaryKeys = ["habitId", "day"])
data class HabitTickRow(val habitId: Long, val day: Long)

/** A progress photo: the picture is a file in the app's own storage, never in the backup. */
@Entity(tableName = "photos", indices = [Index("day")])
data class PhotoRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val file: String,
    val at: Long,
)

@Dao
interface WellbeingDao {
    @Query("SELECT * FROM measurements ORDER BY day, at, id") fun measurements(): Flow<List<MeasurementRow>>
    @Query("SELECT * FROM measurements ORDER BY day, at, id") suspend fun allMeasurements(): List<MeasurementRow>
    @Insert suspend fun insertMeasurements(rows: List<MeasurementRow>)
    @Query("DELETE FROM measurements WHERE id = :id") suspend fun deleteMeasurement(id: Long)
    @Query("DELETE FROM measurements") suspend fun clearMeasurements()

    @Query("SELECT * FROM day_notes ORDER BY day") fun dayNotes(): Flow<List<DayNoteRow>>
    @Query("SELECT * FROM day_notes ORDER BY day") suspend fun allDayNotes(): List<DayNoteRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDayNotes(rows: List<DayNoteRow>)
    @Query("DELETE FROM day_notes WHERE day = :day") suspend fun deleteDayNote(day: Long)
    @Query("DELETE FROM day_notes") suspend fun clearDayNotes()

    @Query("SELECT * FROM habits ORDER BY createdAt, id") fun habits(): Flow<List<HabitRow>>
    @Query("SELECT * FROM habits ORDER BY createdAt, id") suspend fun allHabits(): List<HabitRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabits(rows: List<HabitRow>)
    @Insert suspend fun insertHabit(row: HabitRow): Long
    @Query("DELETE FROM habits") suspend fun clearHabits()

    @Query("SELECT * FROM habit_ticks") fun habitTicks(): Flow<List<HabitTickRow>>
    @Query("SELECT * FROM habit_ticks") suspend fun allHabitTicks(): List<HabitTickRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHabitTicks(rows: List<HabitTickRow>)
    @Query("DELETE FROM habit_ticks WHERE habitId = :habitId AND day = :day") suspend fun deleteHabitTick(habitId: Long, day: Long)
    @Query("DELETE FROM habit_ticks") suspend fun clearHabitTicks()

    @Query("SELECT * FROM photos ORDER BY day DESC, at DESC") fun photos(): Flow<List<PhotoRow>>
    @Insert suspend fun insertPhoto(row: PhotoRow): Long
    @Query("SELECT * FROM photos WHERE id = :id") suspend fun photo(id: Long): PhotoRow?
    @Query("DELETE FROM photos WHERE id = :id") suspend fun deletePhoto(id: Long)
}

fun MeasurementRow.toCore() = Measurement(id, dayOf(day), MeasureKind.valueOf(kind), value, at)
fun Measurement.toRow() = MeasurementRow(id, day.toDay(), kind.name, value, at)
fun DayNoteRow.toCore() = DayNote(dayOf(day), sleepHours, mood, text)
fun DayNote.toRow() = DayNoteRow(day.toDay(), sleepHours, mood, text)
fun HabitRow.toCore() = Habit(id, name, createdAt, archived)
fun Habit.toRow() = HabitRow(id, name, createdAt, archived)
fun HabitTickRow.toCore() = HabitTick(habitId, dayOf(day))
fun HabitTick.toRow() = HabitTickRow(habitId, day.toDay())

/** Progress photos, as files in the app's private storage, which no other app can read. */
object Photos {
    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Copies the picture at [uri] into the app's storage and returns the new file's name. */
    suspend fun keep(context: Context, uri: Uri, now: Long): String = withContext(Dispatchers.IO) {
        val name = "photo-$now.jpg"
        val target = file(context, name)
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("The photo could not be read.")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        name
    }

    /** Writes a copy of a photo to a place the user picked through the system dialog. */
    suspend fun saveCopy(context: Context, name: String, to: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(to) ?: throw IllegalArgumentException("That place can't be written to.")
        out.use { o -> file(context, name).inputStream().use { it.copyTo(o) } }
    }

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }
}
