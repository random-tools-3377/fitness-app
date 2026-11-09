package io.github.randomtools3377.fitness.core

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Light or dark (0.10.0), or pure black (0.17.0). */
@Serializable
enum class ThemeMode(val title: String) { SYSTEM("Phone"), LIGHT("Light"), DARK("Dark"), BLACK("Black") }

/**
 * The app's colour; WALLPAPER uses the phone's own colours where the phone offers them. Each other
 * one is a [seed] the whole set of colours is built from (0.17.0 adds Ocean, Forest and Sunset).
 */
@Serializable
enum class Accent(val title: String, val seed: Int) {
    WALLPAPER("Wallpaper", 0xFFB5462F.toInt()),
    TERRACOTTA("Terracotta", 0xFFB5462F.toInt()),
    GREEN("Green", 0xFF2E6B30.toInt()),
    BLUE("Blue", 0xFF2B5EA7.toInt()),
    PURPLE("Purple", 0xFF6C4EA2.toInt()),
    OCEAN("Ocean", 0xFF2F80C0.toInt()),
    FOREST("Forest", 0xFF3E7B3A.toInt()),
    SUNSET("Sunset", 0xFFD9822B.toInt()),
}

/** How roomy the cards are (0.17.0). */
@Serializable
enum class CardSize(val title: String) { COMPACT("Compact"), COMFORTABLE("Comfortable"), LARGE("Large") }

/** The app's font: the phone's own, or one of three that come with the app (0.17.0). */
@Serializable
enum class FontChoice(val title: String) { PHONE("Phone's"), ROUNDED("Rounded"), CLEAR("Easy to read"), SERIF("Serif") }

/** What is behind the diary: nothing, a gradient of the app's colour, or the user's own photo, dimmed (0.17.0). */
@Serializable
enum class Backdrop(val title: String) { NONE("None"), GRADIENT("Gradient"), PHOTO("My photo") }

/** The home-screen widget in the app's colours, or white text over the wallpaper (0.17.0). */
@Serializable
enum class WidgetStyle(val title: String) { SOLID("Solid"), SEE_THROUGH("See-through") }

/** The home-screen icon (0.17.0): each is a launcher entry, and only the chosen one is on. */
@Serializable
enum class AppIconChoice(val title: String, val color: Int, val alias: String) {
    TERRACOTTA("Terracotta", 0xFFB5462F.toInt(), "IconTerracotta"),
    OCEAN("Ocean", 0xFF2F80C0.toInt(), "IconOcean"),
    FOREST("Forest", 0xFF3E7B3A.toInt(), "IconForest"),
    NIGHT("Night", 0xFF1D1B1A.toInt(), "IconNight"),
}

/** The tabs that can be hidden or opened first; the diary always stays (0.17.0). */
object AppTabs {
    /** In the order shown, by name. */
    val ALL = listOf("DIARY", "FOODS", "WORKOUTS", "PLAN", "PROGRESS")
    const val DIARY = "DIARY"
}

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.WALLPAPER,
    /** Text size on top of the phone's own: 0.9, 1, 1.15 or 1.3 (0.14.0; smaller in 0.17.0). */
    val textScale: Float = 1f,
    /** Chart colours that don't rely on telling red from green (0.14.0). */
    val colourBlind: Boolean = false,
    /** Any colour from the colour picker, as 0xFFRRGGBB; when set, it is used instead of [accent] (0.17.0). */
    val color: Int? = null,
    /** Colours picked lately, newest first, offered again in the picker. */
    val recent: List<Int> = emptyList(),
    val cardSize: CardSize = CardSize.COMFORTABLE,
    val font: FontChoice = FontChoice.PHONE,
    /** Tabs the user hid (never the diary). */
    val hiddenTabs: Set<String> = emptySet(),
    /** The tab the app opens on. */
    val firstTab: String = AppTabs.DIARY,
    val backdrop: Backdrop = Backdrop.NONE,
    /** How much the photo is faded toward the background colour, in percent. */
    val dim: Int = DEFAULT_DIM,
    /** Changes when a new photo is picked, so it is read again. */
    val photoStamp: String = "",
    val icon: AppIconChoice = AppIconChoice.TERRACOTTA,
    val widgetStyle: WidgetStyle = WidgetStyle.SOLID,
) {
    /** The colour the app is built from, or null to match the wallpaper. */
    val seed: Int? get() = color ?: accent.takeIf { it != Accent.WALLPAPER }?.seed

    /** The first tab, or the diary if that one is hidden. */
    fun opensOn(available: List<String>): String = firstTab.takeIf { it in available && it !in hiddenTabs } ?: AppTabs.DIARY

    /** A colour picked from the picker: used, and put first among the recent ones (at most 6). */
    fun picked(c: Int): ThemeSettings = copy(color = c, recent = (listOf(c) + recent.filter { it != c }).take(6))

    companion object {
        const val DEFAULT_DIM = 70
        const val MIN_DIM = 40
        const val MAX_DIM = 90
    }
}

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
