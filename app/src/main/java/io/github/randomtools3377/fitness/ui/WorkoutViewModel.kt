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
import io.github.randomtools3377.fitness.core.SavedWorkoutItem
import io.github.randomtools3377.fitness.core.Supersets
import io.github.randomtools3377.fitness.core.Track
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
import kotlinx.coroutines.flow.combine
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

    /** What the user chose to log for each exercise (0.20.0). */
    val tracks: StateFlow<Map<String, List<Track>>> = repo.settings.map { it.exerciseTracks }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val exercises: StateFlow<List<Exercise>> = combine(repo.ownExercises, tracks) { own, t -> (Exercises.BUILT_IN + own).map { Exercises.withTrack(it, t) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Exercises.BUILT_IN)

    /** Keep the screen on while a workout is open (0.20.0). */
    val keepScreenOn: StateFlow<Boolean> = repo.settings.map { it.keepScreenOn }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(keepScreenOn = on) } }

    /**
     * Changes what is logged for an exercise, from now on: in the workout in progress, in saved
     * workouts when they next start, and wherever it is picked.
     */
    fun setTrack(e: Exercise, chosen: List<Track>) = viewModelScope.launch {
        val t = Track.ordered(chosen)
        if (t.isEmpty()) return@launch
        val usual = t == Track.usual(e.kind)
        repo.updateSettings { s -> s.copy(exerciseTracks = if (usual) s.exerciseTracks - e.key else s.exerciseTracks + (e.key to t)) }
        change { w -> w.copy(exercises = w.exercises.map { we -> if (we.exercise.key == e.key) we.copy(exercise = we.exercise.copy(track = if (usual) null else t)) else we }) }
    }

    /** [e] as it is logged now. */
    fun current(e: Exercise): Exercise = Exercises.withTrack(e.copy(track = null), tracks.value)

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

    /** The sets of an exercise the last time it was done, for planning ahead. */
    fun lastSets(key: String): List<WorkoutSet>? = Workouts.lastTime(key, finishedBefore())

    // Starting and ending.

    fun startEmpty() = start(Workout(name = "Workout", day = today(), startedAt = now()))

    fun startSaved(sw: SavedWorkout) =
        start(Workouts.start(sw.copy(items = sw.items.map { it.copy(exercise = current(it.exercise)) }), finishedBefore(), today(), now()))

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

    /**
     * Leaving the workout screen (0.20.0): a workout left within its first minute with nothing
     * ticked was most likely started by mistake, so it isn't kept.
     */
    fun leave() {
        val w = current.value ?: return
        if (now() - w.startedAt < 60_000 && w.exercises.none { we -> we.sets.any { it.done } }) {
            discard("Not kept: it was under a minute, with nothing ticked")
        }
    }

    fun discard(note: String = "Workout discarded") = viewModelScope.launch {
        val w = current.value ?: return@launch
        current.value = null
        RestTimer.stop(context)
        saveLock.withLock { repo.deleteWorkout(w.id) }
        message.value = note
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
        // Each set can have its own rest (0.20.0); long ones, logged in minutes, have none.
        if (!we.exercise.long && Supersets.restAfter(w, index)) {
            RestTimer.start(context, s.restSeconds ?: restFor(we.exercise.key), we.exercise.name)
        }
    }

    /** Marks a set as a warm-up, or back to a working set (0.9.0). */
    fun toggleWarmUp(index: Int, setIndex: Int) = updateSet(index, setIndex) { it.copy(warmUp = !it.warmUp) }

    /** The rest after one set (0.20.0); null goes back to the exercise's rest. */
    fun setSetRest(index: Int, setIndex: Int, seconds: Int?) = updateSet(index, setIndex) { it.copy(restSeconds = seconds?.coerceIn(0, 3600)) }

    fun moveExercise(index: Int, by: Int) = change { w -> w.copy(exercises = Supersets.move(w.exercises, index, by)) }

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

    /** The user's own cues for each exercise (0.13.0). */
    val notes: StateFlow<Map<String, String>> = repo.settings.map { it.exerciseNotes }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setNote(key: String, text: String) = viewModelScope.launch {
        repo.updateSettings { s -> s.copy(exerciseNotes = if (text.isBlank()) s.exerciseNotes - key else s.exerciseNotes + (key to text.trim())) }
    }

    /** The interval timer's plan (0.13.0). */
    val intervalPlan: StateFlow<io.github.randomtools3377.fitness.core.IntervalPlan> = repo.settings.map { it.intervals }
        .stateIn(viewModelScope, SharingStarted.Eagerly, io.github.randomtools3377.fitness.core.IntervalPlan.TABATA)

    fun setIntervalPlan(p: io.github.randomtools3377.fitness.core.IntervalPlan) = viewModelScope.launch { repo.updateSettings { it.copy(intervals = p) } }

    fun setProgramme(p: Map<Int, Long>) = viewModelScope.launch { repo.updateSettings { it.copy(programme = p) } }


    fun setRest(key: String, seconds: Int) = viewModelScope.launch {
        repo.setRest(key, seconds.coerceIn(5, 3600))
    }

    suspend fun newExercise(name: String, kind: ExerciseKind, track: List<Track> = Track.usual(kind)): Exercise? {
        if (name.isBlank()) { message.value = "Give the exercise a name."; return null }
        val t = Track.ordered(track).ifEmpty { Track.usual(kind) }
        val e = repo.addExercise(name, kind)
        if (t == Track.usual(kind)) return e
        repo.updateSettings { s -> s.copy(exerciseTracks = s.exerciseTracks + (e.key to t)) }
        return e.copy(track = t)
    }

    /** Saves a workout planned ahead, new or changed (0.20.0). */
    fun savePlanned(sw: SavedWorkout, done: () -> Unit) = viewModelScope.launch {
        when {
            sw.name.isBlank() -> message.value = "Give the workout a name."
            sw.items.isEmpty() -> message.value = "Add an exercise first."
            else -> {
                val items = sw.items.map { it.copy(sets = it.plan.size.coerceAtLeast(1), exercise = it.exercise.copy(track = null)) }
                repo.saveSavedWorkout(sw.copy(name = sw.name.trim(), items = Supersets.tidyItems(items), createdAt = if (sw.id == 0L) now() else sw.createdAt))
                message.value = "Saved \"${sw.name.trim()}\""
                done()
            }
        }
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
