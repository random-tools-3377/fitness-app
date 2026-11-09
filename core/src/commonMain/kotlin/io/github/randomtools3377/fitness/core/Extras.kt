package io.github.randomtools3377.fitness.core

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Light or dark (0.10.0). */
@Serializable
enum class ThemeMode(val title: String) { SYSTEM("Follow the phone"), LIGHT("Light"), DARK("Dark") }

/** The accent colour; WALLPAPER uses the phone's own colours where the phone offers them. */
@Serializable
enum class Accent(val title: String) { WALLPAPER("Phone's colours"), TERRACOTTA("Terracotta"), GREEN("Green"), BLUE("Blue"), PURPLE("Purple") }

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.WALLPAPER,
    /** Text size on top of the phone's own: 1, 1.15 or 1.3 (0.14.0). */
    val textScale: Float = 1f,
    /** Chart colours that don't rely on telling red from green (0.14.0). */
    val colourBlind: Boolean = false,
)

/** A stretch of steps the phone counted, from Health Connect (0.10.0). Times in epoch milliseconds. */
data class StepCount(val start: Long, val end: Long, val count: Long)

object Steps {
    /** About 0.0005 kcal per kg of body weight for each step: some 35 kcal per 1,000 steps at 70 kg. */
    const val KCAL_PER_KG_PER_STEP = 0.0005

    /**
     * The day's steps that count for calories: those not during a logged workout, so a walk or
     * run logged as cardio isn't counted twice. [workouts] are (start, end) in epoch ms.
     */
    fun countable(records: List<StepCount>, workouts: List<Pair<Long, Long>>): Long =
        records.filter { r -> workouts.none { (s, e) -> r.start < e && r.end > s } }.sumOf { it.count }

    fun kcal(steps: Long, weightKg: Double, half: Boolean = false): Double {
        val k = steps * weightKg * KCAL_PER_KG_PER_STEP
        return (if (half) k / 2 else k).roundToInt().toDouble()
    }
}

/** Spreadsheet (CSV) files of the diary, weigh-ins, measurements and workouts, for reading elsewhere (0.10.0). */
object CsvExport {
    private fun cell(x: String): String =
        if (x.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + x.replace("\"", "\"\"") + "\"" else x

    private fun num(x: Double?): String = x?.let { formatAmount(kotlin.math.round(it * 100) / 100.0) }.orEmpty()

    private fun row(vararg cells: String) = cells.joinToString(",") { cell(it) }

    private fun time(ms: Long?, tz: TimeZone): String = ms?.let {
        val t = Instant.fromEpochMilliseconds(it).toLocalDateTime(tz)
        "${t.date} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }.orEmpty()

    /** Eaten food only; planned items that weren't eaten are left out. */
    fun diary(entries: List<DiaryEntry>): String = buildString {
        appendLine(row("date", "meal", "food", "amount", "kcal", "protein g", "carbs g", "fat g", "fibre g", "sugar g", "saturated fat g", "salt g"))
        for (e in entries.filter { !it.planned }.sortedWith(compareBy<DiaryEntry> { it.day }.thenBy { it.meal.ordinal }.thenBy { it.createdAt })) {
            val n = e.nutrients
            appendLine(
                row(
                    e.day.toString(), e.meal.title, e.name, e.describeAmount(),
                    num(n.kcal), num(n.protein), num(n.carbs), num(n.fat), num(n.fibre), num(n.sugar), num(n.satFat), num(n.salt),
                ),
            )
        }
    }

    fun weighIns(weighIns: List<WeighIn>): String = buildString {
        appendLine(row("date", "kg"))
        for (w in weighIns.sortedWith(compareBy<WeighIn> { it.day }.thenBy { it.at })) appendLine(row(w.day.toString(), num(w.kg)))
    }

    fun measurements(list: List<Measurement>): String = buildString {
        appendLine(row("date", "measurement", "value", "unit"))
        for (m in list.sortedWith(compareBy<Measurement> { it.day }.thenBy { it.at })) {
            appendLine(row(m.day.toString(), m.kind.title, num(m.value), if (m.kind.percent) "%" else "cm"))
        }
    }

    /** One row per set: weights in kg, distances in km, times in seconds. */
    fun workouts(workouts: List<Workout>, tz: TimeZone): String = buildString {
        appendLine(row("date", "started", "workout", "exercise", "set", "warm-up", "done", "kg", "reps", "seconds", "km"))
        for (w in workouts.filter { it.finished }.sortedBy { it.startedAt }) {
            for (we in w.exercises) {
                we.sets.forEachIndexed { i, s ->
                    appendLine(
                        row(
                            w.day.toString(), time(w.startedAt, tz), w.name, we.exercise.name, "${i + 1}",
                            if (s.warmUp) "yes" else "", if (s.done) "yes" else "no",
                            num(s.weightKg), s.reps?.toString().orEmpty(), s.seconds?.toString().orEmpty(), num(s.distanceKm),
                        ),
                    )
                }
            }
        }
    }

    fun sleepAndMood(notes: List<DayNote>): String = buildString {
        appendLine(row("date", "hours slept", "mood (1-5)"))
        for (n in notes.sortedBy { it.day }) appendLine(row(n.day.toString(), num(n.sleepHours), n.mood?.toString().orEmpty()))
    }

    /** The file names and contents, for one zip file. */
    fun all(
        entries: List<DiaryEntry>,
        weighIns: List<WeighIn>,
        measurements: List<Measurement>,
        workouts: List<Workout>,
        notes: List<DayNote>,
        tz: TimeZone,
    ): Map<String, String> = linkedMapOf(
        "diary.csv" to diary(entries),
        "weigh-ins.csv" to weighIns(weighIns),
        "measurements.csv" to measurements(measurements),
        "workouts.csv" to workouts(workouts, tz),
        "sleep-and-mood.csv" to sleepAndMood(notes),
    )

    fun fileName(day: LocalDate): String = "fitness-spreadsheets-$day.zip"
}
