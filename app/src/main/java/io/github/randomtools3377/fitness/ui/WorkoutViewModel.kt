package io.github.randomtools3377.fitness.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.core.Best
import io.github.randomtools3377.fitness.core.BestBeaten
import io.github.randomtools3377.fitness.core.Bests
import io.github.randomtools3377.fitness.core.Burned
import io.github.randomtools3377.fitness.core.Exercise
import io.github.randomtools3377.fitness.core.ExerciseKind
import io.github.randomtools3377.fitness.core.Exercises
import io.github.randomtools3377.fitness.core.SavedWorkout
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Weight
import io.github.randomtools3377.fitness.core.Workout
import io.github.randomtools3377.fitness.core.WorkoutExercise
import io.github.randomtools3377.fitness.core.WorkoutSet
import io.github.randomtools3377.fitness.core.Workouts
import io.github.randomtools3377.fitness.workout.RestTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale

/** Workouts: the one in progress, saved workouts, history, exercises and personal bests. */
class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitnessApplication).container.repository
    private val context = app

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    fun todayDate(): LocalDate = today()

    val message = MutableStateFlow<String?>(null)

    /** Bests just beaten by a set, shown on the spot. */
    val celebration = MutableStateFlow<List<BestBeaten>>(emptyList())

    val units: StateFlow<UnitSettings> = repo.settings
        .map { it.units ?: UnitSettings.forRegion(Locale.getDefault().country) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSettings.forRegion(Locale.getDefault().country))

    val workouts: StateFlow<List<Workout>> = repo.workouts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Finished workouts, newest first. */
    val history: StateFlow<List<Workout>> = workouts.map { list -> list.filter { it.finished }.sortedByDescending { it.startedAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val saved: StateFlow<List<SavedWorkout>> = repo.savedWorkouts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val exercises: StateFlow<List<Exercise>> = repo.ownExercises.map { Exercises.BUILT_IN + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Exercises.BUILT_IN)

    val restTimes: StateFlow<Map<String, Int>> = repo.restTimes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val bests: StateFlow<List<Best>> = workouts.map { Bests.all(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The workout in progress, kept here as it is edited and saved after every change, so it
     * survives the app being closed.
     */
    private val current = MutableStateFlow<Workout?>(null)
    val active: StateFlow<Workout?> = current
    private val saveLock = Mutex()
    val loaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            current.value = repo.workoutsOnce().lastOrNull { !it.finished }
            loaded.value = true
        }
    }

    fun restFor(key: String): Int = restTimes.value[key] ?: Exercises.DEFAULT_REST_SECONDS

    private fun persist() = viewModelScope.launch {
        saveLock.withLock { current.value?.let { repo.saveWorkout(it) } }
    }

    private fun change(f: (Workout) -> Workout) {
        val w = current.value ?: return
        current.value = f(w)
        persist()
    }

    private fun finishedBefore(): List<Workout> = workouts.value.filter { it.finished }

    // Starting and ending.

    fun startEmpty() = start(Workout(name = "Workout", day = today(), startedAt = now()))

    fun startSaved(sw: SavedWorkout) = start(Workouts.start(sw, finishedBefore(), today(), now()))

    private fun start(w: Workout) = viewModelScope.launch {
        if (current.value != null) { message.value = "Finish the workout in progress first."; return@launch }
        val id = saveLock.withLock { repo.saveWorkout(w) }
        current.value = w.copy(id = id)
    }

    private suspend fun weightKg(): Double? = Weight.latest(repo.weighInsOnce())?.kg ?: repo.settingsOnce().profile?.weightKg

    /** Ends the workout, works out the calories it burned, and saves it for next time if named. */
    fun finish(saveAs: String?) = viewModelScope.launch {
        val w = current.value ?: return@launch
        val now = now()
        val kept = w.copy(endedAt = now, exercises = w.exercises.filter { it.sets.isNotEmpty() })
        val kg = weightKg()
        val burned = if (kg != null) Burned.rounded(Burned.workout(kept, kg)) else 0.0
        current.value = null
        RestTimer.stop(context)
        saveLock.withLock { repo.saveWorkout(kept.copy(burnedKcal = burned)) }
        if (!saveAs.isNullOrBlank() && kept.exercises.isNotEmpty()) {
            repo.saveSavedWorkout(Workouts.saveAs(saveAs, kept, now))
        }
        message.value = when {
            kg == null -> "Workout saved. Add a weigh-in to count the calories it burned."
            else -> "Workout saved: about ${burned.toInt()} kcal burned"
        }
    }

    fun discard() = viewModelScope.launch {
        val w = current.value ?: return@launch
        current.value = null
        RestTimer.stop(context)
        saveLock.withLock { repo.deleteWorkout(w.id) }
        message.value = "Workout discarded"
    }

    fun rename(name: String) { if (name.isNotBlank()) change { it.copy(name = name.trim()) } }

    // Exercises and sets.

    fun addExercise(e: Exercise) = change { Workouts.addExercise(it, e, finishedBefore()) }

    fun removeExercise(index: Int) = change { w -> w.copy(exercises = w.exercises.filterIndexed { i, _ -> i != index }) }

    fun addSet(index: Int) = change { w ->
        w.copy(
            exercises = w.exercises.mapIndexed { i, we ->
                if (i != index) we else we.copy(sets = we.sets + (we.sets.lastOrNull()?.copy(done = false, doneAt = null) ?: WorkoutSet()))
            },
        )
    }

    fun removeSet(index: Int, setIndex: Int) = change { w ->
        w.copy(exercises = w.exercises.mapIndexed { i, we -> if (i != index) we else we.copy(sets = we.sets.filterIndexed { j, _ -> j != setIndex }) })
    }

    fun updateSet(index: Int, setIndex: Int, f: (WorkoutSet) -> WorkoutSet) = change { w ->
        w.copy(
            exercises = w.exercises.mapIndexed { i, we ->
                if (i != index) we else we.copy(sets = we.sets.mapIndexed { j, s -> if (j == setIndex) f(s) else s })
            },
        )
    }

    /** Ticks a set (or unticks it). Ticking starts the rest timer and checks for personal bests. */
    fun tick(index: Int, setIndex: Int) {
        val w = current.value ?: return
        val we = w.exercises.getOrNull(index) ?: return
        val s = we.sets.getOrNull(setIndex) ?: return
        if (s.done) {
            updateSet(index, setIndex) { it.copy(done = false, doneAt = null) }
            return
        }
        val doneSet = s.copy(done = true, doneAt = now())
        // Compared with every earlier workout and the sets already ticked in this one.
        val sofar = w.copy(
            endedAt = w.startedAt,
            exercises = w.exercises.mapIndexed { i, x -> if (i == index) x.copy(sets = x.sets.filterIndexed { j, _ -> j != setIndex }) else x },
        )
        val beaten = Bests.beaten(finishedBefore() + sofar, we.exercise, doneSet, w.day)
        updateSet(index, setIndex) { doneSet }
        if (beaten.isNotEmpty()) celebration.value = beaten
        // In a superset, the rest comes after the last exercise of the round (0.9.0).
        if (we.exercise.kind != ExerciseKind.CARDIO && io.github.randomtools3377.fitness.core.Supersets.restAfter(w, index)) {
            RestTimer.start(context, restFor(we.exercise.key), we.exercise.name)
        }
    }

    /** Marks a set as a warm-up, or back to a working set (0.9.0). */
    fun toggleWarmUp(index: Int, setIndex: Int) = updateSet(index, setIndex) { it.copy(warmUp = !it.warmUp) }

    fun joinSuperset(index: Int) = change { w -> io.github.randomtools3377.fitness.core.Supersets.joinWithNext(w, index) }

    fun leaveSuperset(index: Int) = change { w -> io.github.randomtools3377.fitness.core.Supersets.leave(w, index) }

    /** "Try a bit more", from the last time the exercise was done. */
    fun hint(e: io.github.randomtools3377.fitness.core.Exercise): io.github.randomtools3377.fitness.core.Hint? =
        io.github.randomtools3377.fitness.core.Progression.hint(e, io.github.randomtools3377.fitness.core.Workouts.lastTime(e.key, finishedBefore()), units.value.weight)

    fun applyHint(index: Int, h: io.github.randomtools3377.fitness.core.Hint) = change { w ->
        w.copy(exercises = w.exercises.mapIndexed { i, we -> if (i == index) io.github.randomtools3377.fitness.core.Progression.apply(we, h) else we })
    }

    /** Starts today's workout from the weekly programme. */
    fun startToday(): Boolean {
        val sw = io.github.randomtools3377.fitness.core.Programme.today(programme.value, saved.value, today()) ?: return false
        startSaved(sw)
        return true
    }

    val programme: StateFlow<Map<Int, Long>> = repo.settings.map { it.programme }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val plates: StateFlow<io.github.randomtools3377.fitness.core.PlateSettings> = repo.settings.map { it.plates }
        .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.randomtools3377.fitness.core.PlateSettings())

    fun setProgramme(p: Map<Int, Long>) = viewModelScope.launch { repo.updateSettings { it.copy(programme = p) } }


    fun setRest(key: String, seconds: Int) = viewModelScope.launch {
        repo.setRest(key, seconds.coerceIn(5, 3600))
    }

    suspend fun newExercise(name: String, kind: ExerciseKind): Exercise? {
        if (name.isBlank()) { message.value = "Give the exercise a name."; return null }
        return repo.addExercise(name, kind)
    }

    fun deleteExercise(e: Exercise) = viewModelScope.launch { repo.deleteExercise(e) }

    // Cardio typed in on its own, and history.

    /** A cardio session typed in: its time, and its distance if known. */
    fun logCardio(e: Exercise, minutes: Double, km: Double?, day: LocalDate) = viewModelScope.launch {
        if (minutes <= 0) { message.value = "Type how long it took."; return@launch }
        val now = now()
        val seconds = (minutes * 60).toInt()
        val w = Workout(
            name = e.name, day = day, startedAt = now - seconds * 1000L, endedAt = now,
            exercises = listOf(WorkoutExercise(e, listOf(WorkoutSet(seconds = seconds, distanceKm = km, done = true, doneAt = now)))),
        )
        val beaten = Bests.beaten(finishedBefore(), e, w.exercises[0].sets[0], day)
        val kg = weightKg()
        val burned = if (kg != null) Burned.rounded(Burned.workout(w, kg)) else 0.0
        saveLock.withLock { repo.saveWorkout(w.copy(burnedKcal = burned)) }
        if (beaten.isNotEmpty()) celebration.value = beaten
        message.value = if (kg == null) "Saved. Add a weigh-in to count the calories it burned." else "Saved: about ${burned.toInt()} kcal burned"
    }

    fun deleteWorkout(w: Workout) = viewModelScope.launch {
        repo.deleteWorkout(w.id)
        message.value = "Workout deleted"
    }

    fun deleteSaved(sw: SavedWorkout) = viewModelScope.launch { repo.deleteSavedWorkout(sw.id) }

    fun saveAsWorkout(name: String, w: Workout) = viewModelScope.launch {
        try {
            repo.saveSavedWorkout(Workouts.saveAs(name, w, now()))
            message.value = "Saved \"${name.trim()}\""
        } catch (e: IllegalArgumentException) {
            message.value = e.message
        }
    }
}
