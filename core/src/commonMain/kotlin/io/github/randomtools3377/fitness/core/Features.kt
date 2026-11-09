package io.github.randomtools3377.fitness.core

/**
 * The parts of the app that can be switched off, so it only shows what the user uses (0.15.0).
 * The food diary and Settings always stay. [startsOn] is for a new install.
 */
enum class Feature(val title: String, val description: String, val startsOn: Boolean = false) {
    WORKOUTS("Workouts", "Log weights, cardio and bodyweight exercises, with a rest timer and personal bests.", startsOn = true),
    WATER("Water", "A daily water target with buttons for a glass or a bottle.", startsOn = true),
    WEIGHT("Weight", "Weigh-ins with a chart and a 7-day average.", startsOn = true),
    STATS("Stats and streaks", "Averages, charts and streaks for the week, month and year.", startsOn = true),
    BARCODES("Barcode scanning", "Scan packaged foods, using the country files you download."),
    LABEL_PHOTO("Reading labels", "Fill in a food from a photo of its nutrition label."),
    PLANNING("Recipes and planning", "Recipes with servings, meals planned ahead, and a shopping list from them."),
    FASTING("Fasting", "A fasting timer with 16:8 and other plans, and a streak."),
    MEAL_TARGETS("A target for each meal", "Split the day's calories between breakfast, lunch, dinner and snacks."),
    DAY_TARGETS("Targets by day", "Different calories on some weekdays or training days, and diet breaks."),
    EXTRAS("Fibre, sugar, saturates and salt", "Shown on foods and the day, with optional limits."),
    ALCOHOL_CAFFEINE("Alcohol and caffeine", "Standard drinks and mg of caffeine each day, with optional limits."),
    VITAMINS("Vitamins and minerals", "Iron, calcium, vitamin D and more from built-in foods."),
    BODY("Measurements and photos", "Waist, hips and more, a body-fat estimate, and progress photos kept on the phone."),
    SLEEP_MOOD("Sleep and mood", "Hours slept and how you feel, next to what you ate."),
    HABITS("Your own habits", "Things to do each day, like stretching, each with a streak."),
    SUPPLEMENTS("Supplements and medication", "A daily tick list, with reminders if you like."),
    CYCLE("Cycle tracking", "Period days, marked on the weight chart, and the next one estimated."),
    STEPS("Steps", "Your step count from Health Connect, adding to the day's calories."),
    CHECK_IN("Weekly check-in", "On Mondays, how your weight moved against the plan, with a suggested target."),
    TRAINING_EXTRAS("Training extras", "A weekly programme, plate calculator, hints, supersets, warm-ups, notes and an interval timer."),
    OVERVIEW("Calendar and year in review", "A month at a glance, and each year's totals and bests."),
}

/** What the user's data shows they already use, for turning those parts on when they update. */
data class FeatureUse(
    val hasWorkouts: Boolean = false,
    val hasWater: Boolean = false,
    val hasWeighIns: Boolean = false,
    val hasCountries: Boolean = false,
    val hasBarcodeFoods: Boolean = false,
    val hasRecipesOrPlans: Boolean = false,
    val hasFasts: Boolean = false,
    val hasMeasurementsOrPhotos: Boolean = false,
    val hasSleepOrMood: Boolean = false,
    val hasHabits: Boolean = false,
    val hasSupplements: Boolean = false,
    val hasPeriods: Boolean = false,
)

object Features {
    val STARTS_ON: Set<Feature> = Feature.entries.filter { it.startsOn }.toSet()

    /** The parts that are on: as chosen, or for someone who hasn't chosen, the simple start plus what they already use. */
    fun enabled(s: AppSettings): Set<Feature> =
        s.features?.mapNotNull { k -> Feature.entries.firstOrNull { it.name == k } }?.toSet() ?: STARTS_ON

    /** The first choice for someone updating: the simple start, plus every part their data or settings show they use. */
    fun firstChoice(s: AppSettings, u: FeatureUse): Set<Feature> = buildSet {
        addAll(STARTS_ON)
        if (u.hasCountries || u.hasBarcodeFoods) add(Feature.BARCODES)
        if (u.hasRecipesOrPlans) add(Feature.PLANNING)
        if (u.hasFasts) add(Feature.FASTING)
        if (s.mealSplit.enabled) add(Feature.MEAL_TARGETS)
        if (s.dayTargets.weekdayKcal.isNotEmpty() || s.dayTargets.trainingExtraKcal != null || s.dietBreak != null) add(Feature.DAY_TARGETS)
        val t = s.extraTargets
        if (t.fibre != null || t.sugar != null || t.satFat != null || t.salt != null) add(Feature.EXTRAS)
        if (t.alcoholDrinks != null || t.caffeineMg != null) add(Feature.ALCOHOL_CAFFEINE)
        if (u.hasMeasurementsOrPhotos) add(Feature.BODY)
        if (u.hasSleepOrMood) add(Feature.SLEEP_MOOD)
        if (u.hasHabits) add(Feature.HABITS)
        if (u.hasSupplements) add(Feature.SUPPLEMENTS)
        if (s.cycleOn || u.hasPeriods) add(Feature.CYCLE)
        if (s.stepsOn) add(Feature.STEPS)
        if (s.programme.isNotEmpty() || s.exerciseNotes.isNotEmpty()) add(Feature.TRAINING_EXTRAS)
    }

    fun encode(on: Set<Feature>): Set<String> = on.map { it.name }.toSet()
}
