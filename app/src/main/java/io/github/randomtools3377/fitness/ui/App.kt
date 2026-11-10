package io.github.randomtools3377.fitness.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import io.github.randomtools3377.fitness.core.Meal

enum class Tab(val title: String, val icon: ImageVector) {
    DIARY("Diary", Icons.Filled.DateRange),
    FOODS("Foods", Icons.AutoMirrored.Filled.List),
    WORKOUTS("Workouts", Icons.Filled.PlayArrow),
    PLAN("Plan", Icons.Filled.Edit),
    PROGRESS("Progress", Icons.Filled.Favorite),
}

/** Screens opened on top of the tabs. */
sealed interface Route {
    /** Finding a food to add to [meal]. [openFoodKey] opens its amount straight away. */
    data class AddFood(
        val meal: Meal,
        val openFoodKey: String? = null,
        val mode: AddMode = AddMode.LOG,
        val planDay: kotlinx.datetime.LocalDate? = null,
    ) : Route

    /** Writing or changing a recipe (the draft is kept in the view model). */
    data object Recipe : Route

    data object Settings : Route

    /** Typing in a food from its label: a new one, or changing [editId]. Adds it to [meal] after. */
    data class FoodForm(val editId: Long? = null, val meal: Meal? = null, val barcode: String? = null) : Route

    /** Copying a meal ([meal]), or a whole day, from another day to the day shown. */
    data class Copy(val meal: Meal?) : Route

    data object Target : Route

    /** The workout in progress. */
    data object ActiveWorkout : Route

    data object Bests : Route

    /** The interval timer (0.13.0). */
    data object Intervals : Route

    /** Switching parts of the app on and off (0.15.0). */
    data object Features : Route

    /** Any colour for the app, from the colour picker (0.17.0). */
    data object AppColour : Route

    /** Adding foods with the optional on-phone AI (0.18.0 onwards). */
    data class AiFoods(val kind: AiKind, val meal: Meal = Meal.LUNCH, val mode: AddMode = AddMode.LOG, val planDay: kotlinx.datetime.LocalDate? = null) : Route
}

@Composable
fun FitnessApp(vm: MainViewModel, wvm: WorkoutViewModel, svm: StatsViewModel, bvm: BodyViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.DIARY) }
    val stack = remember { mutableStateListOf<Route>() }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val workoutMessage by wvm.message.collectAsState()
    val bodyMessage by bvm.message.collectAsState()
    val features by vm.features.collectAsState()
    val checkIn by bvm.checkIn.collectAsState()
    val offer by vm.targetOffer.collectAsState()
    val units by vm.units.collectAsState()
    val settings by vm.settings.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val fromReminder by vm.openFromReminder.collectAsState()
    var progressPart by rememberSaveable { mutableStateOf("stats") }
    val loaded by vm.settingsLoaded.collectAsState()
    var opened by rememberSaveable { mutableStateOf(false) }
    val goTo by vm.goToTab.collectAsState()

    // A reminder opens the screen it is about.
    LaunchedEffect(fromReminder) {
        when (fromReminder ?: return@LaunchedEffect) {
            "WORKOUT" -> tab = Tab.WORKOUTS
            "WEIGH_IN" -> { tab = Tab.PROGRESS; progressPart = "weight" }
            "FAST" -> { tab = Tab.PLAN; vm.planPart.value = "fasting" }
            // The widget's "Add food" (0.10.0): the meal that fits the time of day.
            "ADD_FOOD" -> {
                tab = Tab.DIARY
                vm.day.value = vm.today()
                val hour = java.time.LocalTime.now().hour
                val meal = when {
                    hour < 11 -> io.github.randomtools3377.fitness.core.Meal.BREAKFAST
                    hour < 15 -> io.github.randomtools3377.fitness.core.Meal.LUNCH
                    hour < 17 -> io.github.randomtools3377.fitness.core.Meal.SNACKS
                    hour < 21 -> io.github.randomtools3377.fitness.core.Meal.DINNER
                    else -> io.github.randomtools3377.fitness.core.Meal.SNACKS
                }
                stack.clear()
                stack.add(Route.AddFood(meal))
            }
            else -> tab = Tab.DIARY
        }
        vm.openFromReminder.value = null
    }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.message.value = null
        snackbar.showSnackbar(m)
    }

    LaunchedEffect(bodyMessage) {
        val m = bodyMessage ?: return@LaunchedEffect
        bvm.message.value = null
        snackbar.showSnackbar(m)
    }

    LaunchedEffect(workoutMessage) {
        val m = workoutMessage ?: return@LaunchedEffect
        wvm.message.value = null
        snackbar.showSnackbar(m)
    }

    // Only the parts that are on (0.15.0).
    val progressParts = buildList {
        if (io.github.randomtools3377.fitness.core.Feature.STATS in features) add("stats")
        if (io.github.randomtools3377.fitness.core.Feature.WEIGHT in features) add("weight")
        if (io.github.randomtools3377.fitness.core.Feature.BODY in features || io.github.randomtools3377.fitness.core.Feature.CYCLE in features) add("body")
        if (io.github.randomtools3377.fitness.core.Feature.HABITS in features || io.github.randomtools3377.fitness.core.Feature.SUPPLEMENTS in features || io.github.randomtools3377.fitness.core.Feature.SLEEP_MOOD in features) add("habits")
        if (io.github.randomtools3377.fitness.core.Feature.OVERVIEW in features) { add("calendar"); add("year") }
    }
    val tabs = Tab.entries.filter { t ->
        when (t) {
            Tab.WORKOUTS -> io.github.randomtools3377.fitness.core.Feature.WORKOUTS in features
            Tab.PLAN -> io.github.randomtools3377.fitness.core.Feature.PLANNING in features || io.github.randomtools3377.fitness.core.Feature.FASTING in features
            Tab.PROGRESS -> progressParts.isNotEmpty() || io.github.randomtools3377.fitness.core.Feature.CHECK_IN in features
            else -> true
        }
    }
    LaunchedEffect(tabs) { if (loaded && tab !in tabs) tab = Tab.DIARY }
    // The tab chosen to open on, once the settings are read (0.17.0).
    LaunchedEffect(loaded) {
        if (loaded && !opened) {
            opened = true
            val first = settings.theme.opensOn(tabs.map { it.name })
            tab = Tab.entries.firstOrNull { it.name == first } ?: Tab.DIARY
        }
    }
    // A hidden tab opened from Settings.
    LaunchedEffect(goTo) {
        val name = goTo ?: return@LaunchedEffect
        vm.goToTab.value = null
        Tab.entries.firstOrNull { it.name == name && it in tabs }?.let { tab = it; stack.clear() }
    }
    val shownTabs = tabs.filter { it == Tab.DIARY || it.name !in settings.theme.hiddenTabs }
    LaunchedEffect(progressParts) { if (progressPart !in progressParts && progressParts.isNotEmpty()) progressPart = progressParts.first() }

    fun open(r: Route) { stack.add(r) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.isNotEmpty()) { back() }
    // Back from a hidden tab goes to the diary.
    BackHandler(enabled = stack.isEmpty() && tab !in shownTabs) { tab = Tab.DIARY }

    androidx.compose.runtime.CompositionLocalProvider(LocalFeatures provides features) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar {
                    for (t in shownTabs) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.title) },
                            modifier = Modifier.testTag("nav:${t.name.lowercase()}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val top = stack.lastOrNull()) {
            null -> if (tab.name in settings.locked && tab.name !in unlocked) {
                LockedScreen(modifier, onUnlocked = { vm.markUnlocked(tab.name) })
            } else {
                when (tab) {
                    Tab.DIARY -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
                        DiaryBackdrop(settings.theme)
                        DiaryScreen(vm, bvm, Modifier, open = ::open)
                    }
                    Tab.FOODS -> FoodsScreen(vm, modifier, open = ::open)
                    Tab.WORKOUTS -> WorkoutsScreen(wvm, vm, modifier, open = ::open)
                    Tab.PROGRESS -> Column(modifier) {
                        // The weekly check-in (0.8.0), from Monday until closed.
                        checkIn?.takeIf { on(io.github.randomtools3377.fitness.core.Feature.CHECK_IN) }?.let { c ->
                            CheckInCard(c, units, onTake = { bvm.takeCheckInTarget(it) }, onClose = { bvm.closeCheckIn() })
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Choices(
                                progressParts, progressPart,
                                {
                                    when (it) {
                                        "stats" -> "Stats"; "weight" -> "Weight"; "body" -> "Body"; "habits" -> "Habits"; "calendar" -> "Calendar"; else -> "Year"
                                    }
                                },
                                { "progress:$it" },
                            ) { progressPart = it }
                        }
                        when (progressPart) {
                            "stats" -> StatsScreen(svm, Modifier)
                            "weight" -> WeightScreen(vm, Modifier)
                            "body" -> MeasurementsScreen(bvm, Modifier)
                            "calendar" -> CalendarScreen(svm, Modifier)
                            "year" -> YearReviewScreen(svm, Modifier)
                            else -> {
                                val entries by svm.entries.collectAsState()
                                val kcalByDay = remember(entries) {
                                    entries.filter { !it.planned }.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.nutrients.kcal } }
                                }
                                HabitsScreen(bvm, kcalByDay, Modifier)
                            }
                        }
                    }
                    Tab.PLAN -> PlanScreen(vm, modifier, open = ::open)
                }
            }
            is Route.AddFood -> AddFoodScreen(
                vm, top.meal, top.openFoodKey, modifier,
                back = ::back,
                newFood = { code -> open(Route.FoodForm(meal = top.meal, barcode = code)) },
                mode = top.mode,
                planDay = top.planDay,
                ai = { kind -> back(); open(Route.AiFoods(kind, top.meal, top.mode, top.planDay)) },
            )
            is Route.AiFoods -> AiFoodsScreen(
                vm, top.kind, top.meal, top.mode, top.planDay, modifier,
                back = ::back,
                openRecipe = { back(); open(Route.Recipe) },
            )
            Route.Recipe -> RecipeScreen(vm, modifier, back = ::back, addFood = { open(Route.AddFood(Meal.LUNCH, mode = AddMode.RECIPE)) })
            Route.Settings -> Column(modifier) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Settings", style = MaterialTheme.typography.titleLarge)
                }
                SettingsScreen(vm, Modifier, open = ::open)
            }
            is Route.FoodForm -> FoodFormScreen(
                vm, top.editId, modifier, barcode = top.barcode,
                back = ::back,
                saved = { food ->
                    back()
                    // A food typed in while adding to a meal goes straight on to its amount.
                    if (top.meal != null) {
                        val previous = stack.lastOrNull() as? Route.AddFood
                        back()
                        open(Route.AddFood(top.meal, food.key, previous?.mode ?: AddMode.LOG, previous?.planDay))
                    }
                },
            )
            is Route.Copy -> CopyScreen(vm, top.meal, modifier, back = ::back)
            Route.Target -> TargetScreen(vm, modifier, back = ::back)
            Route.ActiveWorkout -> ActiveWorkoutScreen(wvm, modifier, back = ::back)
            Route.Bests -> BestsScreen(wvm, modifier, back = ::back)
            Route.Intervals -> IntervalScreen(wvm, modifier, back = ::back)
            Route.Features -> FeaturesScreen(vm, modifier, back = ::back)
            Route.AppColour -> AppColourScreen(vm, settings.theme, modifier, back = ::back)
        }
    }
    }

    val askEndFast by vm.askEndFast.collectAsState()
    if (askEndFast) {
        AlertDialog(
            onDismissRequest = { vm.askEndFast.value = false },
            title = { Text("You're fasting") },
            text = { Text("You've just logged food. End the fast now?") },
            confirmButton = { TextButton(onClick = { vm.endFast() }, modifier = Modifier.testTag("confirm-end-fast")) { Text("End it") } },
            dismissButton = { TextButton(onClick = { vm.askEndFast.value = false }) { Text("Keep fasting") } },
        )
    }

    offer?.let { o ->
        AlertDialog(
            onDismissRequest = { vm.answerOffer(false) },
            title = { Text("A new target") },
            text = {
                Text(
                    "With your new weight, the worked-out target is ${energy(o.newKcal.toDouble(), units)} a day" +
                        (o.currentKcal?.let { " instead of ${energy(it.toDouble(), units)}" } ?: "") + ". Use the new number?",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.answerOffer(true) }, modifier = Modifier.testTag("accept-offer")) { Text("Use it") }
            },
            dismissButton = {
                TextButton(onClick = { vm.answerOffer(false) }, modifier = Modifier.testTag("decline-offer")) { Text("Keep mine") }
            },
        )
    }
}

/** A tab behind the privacy lock: opens with the phone's own fingerprint, face or PIN. */
@Composable
private fun LockedScreen(modifier: Modifier, onUnlocked: () -> Unit) {
    val unlock = rememberUnlock(onUnlocked)
    LaunchedEffect(Unit) { unlock() }
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
        Text("Locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text("This part of the app is behind your phone's lock.", modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = unlock, modifier = Modifier.testTag("unlock")) { Text("Unlock") }
    }
}

/** What is behind the diary (0.17.0, copied from the checklist app): nothing, a gradient, or the user's photo, faded toward the background. */
@Composable
private fun DiaryBackdrop(theme: io.github.randomtools3377.fitness.core.ThemeSettings) {
    when (theme.backdrop) {
        io.github.randomtools3377.fitness.core.Backdrop.NONE -> Unit
        io.github.randomtools3377.fitness.core.Backdrop.GRADIENT -> androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
                .testTag("backdrop:gradient"),
        )
        io.github.randomtools3377.fitness.core.Backdrop.PHOTO -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, theme.photoStamp) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(io.github.randomtools3377.fitness.data.BackgroundPhoto.file(context).path)
                            ?.asImageBitmap()
                    }.getOrNull()
                }
            }
            image?.let {
                androidx.compose.foundation.Image(
                    it,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = theme.dim / 100f)).testTag("backdrop:photo"),
                )
            }
        }
    }
}
