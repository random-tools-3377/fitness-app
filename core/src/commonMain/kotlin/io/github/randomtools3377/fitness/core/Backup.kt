package io.github.randomtools3377.fitness.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The user's settings: body details, targets and units. */
@Serializable
data class AppSettings(
    /** The details the target is worked out from; null until the user fills them in. */
    val profile: Profile? = null,
    /** The daily calorie target in use, in kcal. Null until one is set. */
    val targetKcal: Int? = null,
    /** The last worked-out target, to notice when a weigh-in moves it by 50 kcal or more. */
    val lastWorkedOutKcal: Int? = null,
    val macros: MacroTargets = MacroTargets(),
    val water: WaterSettings = WaterSettings(),
    /** Null means the units follow the phone's region. */
    val units: UnitSettings? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(s: AppSettings): String = json.encodeToString(serializer(), s)
        fun decode(text: String?): AppSettings =
            if (text.isNullOrBlank()) AppSettings() else try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                AppSettings()
            } catch (e: IllegalArgumentException) {
                AppSettings()
            }
    }
}

/**
 * Everything the app holds, as one file. The same format on Android and iPhone, so data moves
 * between phones by exporting on one and importing on the other. Built-in foods aren't in it:
 * every copy of the app has them, and diary entries keep their own numbers.
 */
@Serializable
data class Backup(
    val app: String = APP,
    val format: Int = FORMAT,
    /** When it was exported, in epoch milliseconds. */
    val exportedAt: Long,
    /** The foods the user typed in, with their portions. */
    val foods: List<Food>,
    val entries: List<DiaryEntry>,
    val savedMeals: List<SavedMeal>,
    val water: List<WaterEntry>,
    val weighIns: List<WeighIn>,
    val settings: AppSettings,
) {
    companion object {
        const val APP = "fitness"

        /** Raised whenever the file's layout changes in a way older versions can't read. */
        const val FORMAT = 1

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun encode(backup: Backup): String = json.encodeToString(serializer(), backup)

        /** Reads a backup file, or explains in plain words why it can't be used. */
        fun decode(text: String): Backup {
            val backup = try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw BackupError("This file isn't a Fitness App backup, or it is damaged.")
            } catch (e: IllegalArgumentException) {
                throw BackupError("This file isn't a Fitness App backup, or it is damaged.")
            }
            if (backup.app != APP) throw BackupError("This file isn't a Fitness App backup.")
            if (backup.format > FORMAT) {
                throw BackupError("This backup was made by a newer version of the app. Update the app first.")
            }
            val foodIds = backup.foods.map { it.id }.toSet()
            if (backup.entries.any { it.foodId != null && it.foodId !in foodIds }) {
                throw BackupError("This backup is damaged: some diary entries point to foods that aren't in it.")
            }
            return backup
        }
    }
}

class BackupError(message: String) : Exception(message)
