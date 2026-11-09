package io.github.randomtools3377.fitness.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.CheckIn
import io.github.randomtools3377.fitness.core.CheckIns
import io.github.randomtools3377.fitness.core.DayNote
import io.github.randomtools3377.fitness.core.Habit
import io.github.randomtools3377.fitness.core.HabitTick
import io.github.randomtools3377.fitness.core.Habits
import io.github.randomtools3377.fitness.core.MeasureKind
import io.github.randomtools3377.fitness.core.Measurement
import io.github.randomtools3377.fitness.core.Measurements
import io.github.randomtools3377.fitness.core.Streak
import io.github.randomtools3377.fitness.core.Streaks
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.data.PhotoRow
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Measurements, progress photos, sleep and mood, the user's own habits and the weekly check-in (0.8.0). */
class BodyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitnessApplication).container.repository

    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    val message = MutableStateFlow<String?>(null)

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val units: StateFlow<UnitSettings> = settings.map { it.units ?: UnitSettings.forRegion(Locale.getDefault().country) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSettings.forRegion(Locale.getDefault().country))

    val measurements: StateFlow<List<Measurement>> = repo.measurements.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<DayNote>> = repo.dayNotes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val habits: StateFlow<List<Habit>> = repo.habits.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ticks: StateFlow<List<HabitTick>> = repo.habitTicks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val photos: StateFlow<List<PhotoRow>> = repo.photos.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Each habit's streak. */
    val habitStreaks: StateFlow<Map<Long, Streak>> = combine(habits, ticks) { h, t -> Habits.streaks(h, t, today()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * This week's check-in, from Monday until the user closes it or takes the new target. Null
     * when there's nothing to show.
     */
    val checkIn: StateFlow<CheckIn?> = combine(repo.weighIns, settings) { w, s ->
        val monday = Streaks.weekOf(today())
        if (s.checkInSeen == monday) null else CheckIns.of(w, s.profile, s.targetKcal, monday)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            message.value = e.message
        } catch (e: IllegalStateException) {
            message.value = e.message
        }
    }

    // Measurements: lengths are typed in cm or inches, as the height unit is set.

    fun addMeasurements(values: Map<MeasureKind, Double>, day: LocalDate) = launchSafely {
        val cmPerShown = if (units.value.height == io.github.randomtools3377.fitness.core.HeightUnit.CM) 1.0 else 2.54
        val list = values.map { (kind, shown) ->
            Measurements.problem(kind, shown)?.let { throw IllegalArgumentException("${kind.title}: $it") }
            Measurement(day = day, kind = kind, value = if (kind.percent) shown else shown * cmPerShown, at = now())
        }
        require(list.isNotEmpty()) { "Type at least one measurement." }
        repo.addMeasurements(list)
        message.value = "Saved"
    }

    fun deleteMeasurement(id: Long) = launchSafely { repo.deleteMeasurement(id) }

    // Sleep and mood.

    fun saveNote(note: DayNote) = launchSafely {
        DayNote.problem(note.sleepHours, note.mood)?.let { throw IllegalArgumentException(it) }
        repo.putDayNote(note)
    }

    // Habits.

    fun addHabit(name: String) = addItem(name, io.github.randomtools3377.fitness.core.HabitKind.HABIT, null)

    /** A habit, or a supplement or medicine with an optional reminder time (0.12.0). */
    fun addItem(name: String, kind: io.github.randomtools3377.fitness.core.HabitKind, reminderMinute: Int?) = launchSafely {
        Habits.problem(name, habits.value.filter { it.kind == kind })?.let { throw IllegalArgumentException(it) }
        repo.addHabit(Habit(name = name.trim(), createdAt = now(), kind = kind, reminderMinute = reminderMinute))
        syncReminders()
    }

    fun archiveHabit(h: Habit, archived: Boolean) = launchSafely {
        repo.putHabit(h.copy(archived = archived))
        syncReminders()
    }

    fun setReminder(h: Habit, minute: Int?) = launchSafely {
        repo.putHabit(h.copy(reminderMinute = minute))
        syncReminders()
    }

    /** Supplement reminders live with the other reminders, so one alarm serves them all. */
    private suspend fun syncReminders() {
        val all = repo.habits.first()
        val times = io.github.randomtools3377.fitness.core.ReminderSettings.supplementTimes(all)
        repo.updateSettings { it.copy(reminders = it.reminders.copy(supplements = times)) }
        io.github.randomtools3377.fitness.reminders.ReminderScheduler.schedule(getApplication(), repo.settingsOnce().reminders)
    }

    // Cycle tracking (0.12.0).

    val periods: StateFlow<List<io.github.randomtools3377.fitness.core.Period>> = repo.periods.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setCycleOn(on: Boolean) = launchSafely { repo.updateSettings { it.copy(cycleOn = on) } }

    fun startPeriod(day: LocalDate) = launchSafely {
        io.github.randomtools3377.fitness.core.Cycle.problem(day, null, today())?.let { throw IllegalArgumentException(it) }
        require(periods.value.none { it.end == null }) { "A period is already going. Mark its last day first." }
        repo.putPeriod(io.github.randomtools3377.fitness.core.Period(start = day))
    }

    fun endPeriod(p: io.github.randomtools3377.fitness.core.Period, day: LocalDate) = launchSafely {
        io.github.randomtools3377.fitness.core.Cycle.problem(p.start, day, today())?.let { throw IllegalArgumentException(it) }
        repo.putPeriod(p.copy(end = day))
    }

    fun deletePeriod(id: Long) = launchSafely { repo.deletePeriod(id) }

    fun tick(h: Habit, day: LocalDate, done: Boolean) = launchSafely { repo.tickHabit(h.id, day, done) }

    // Progress photos: kept in the app's own storage, never in the backup.

    fun addPhoto(uri: Uri, day: LocalDate) = launchSafely {
        runCatching { repo.addPhoto(uri, day, now()) }.onFailure { throw IllegalArgumentException("The photo could not be kept.") }
        message.value = "Photo kept in the app"
    }

    fun deletePhoto(id: Long) = launchSafely { repo.deletePhoto(id) }

    fun savePhotoCopy(id: Long, to: Uri) = launchSafely {
        runCatching { repo.savePhotoCopy(id, to) }.onFailure { throw IllegalArgumentException("The copy could not be saved there.") }
        message.value = "Copy saved"
    }

    // The weekly check-in.

    fun closeCheckIn() = launchSafely {
        val monday = Streaks.weekOf(today())
        repo.updateSettings { it.copy(checkInSeen = monday) }
    }

    fun takeCheckInTarget(kcal: Int) = launchSafely {
        val monday = Streaks.weekOf(today())
        repo.updateSettings { it.copy(targetKcal = kcal, checkInSeen = monday) }
        message.value = "Daily target is now $kcal kcal"
    }
}
