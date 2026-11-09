package io.github.randomtools3377.fitness.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
}

@Composable
fun FitnessApp(vm: MainViewModel, wvm: WorkoutViewModel, svm: StatsViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.DIARY) }
    val stack = remember { mutableStateListOf<Route>() }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val workoutMessage by wvm.message.collectAsState()
    val offer by vm.targetOffer.collectAsState()
    val units by vm.units.collectAsState()
    val settings by vm.settings.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val fromReminder by vm.openFromReminder.collectAsState()
    var progressPart by rememberSaveable { mutableStateOf("stats") }

    // A reminder opens the screen it is about.
    LaunchedEffect(fromReminder) {
        when (fromReminder ?: return@LaunchedEffect) {
            "WORKOUT" -> tab = Tab.WORKOUTS
            "WEIGH_IN" -> { tab = Tab.PROGRESS; progressPart = "weight" }
            else -> tab = Tab.DIARY
        }
        vm.openFromReminder.value = null
    }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.message.value = null
        snackbar.showSnackbar(m)
    }

    LaunchedEffect(workoutMessage) {
        val m = workoutMessage ?: return@LaunchedEffect
        wvm.message.value = null
        snackbar.showSnackbar(m)
    }

    fun open(r: Route) { stack.add(r) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.isNotEmpty()) { back() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar {
                    for (t in Tab.entries) {
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
                    Tab.DIARY -> DiaryScreen(vm, modifier, open = ::open)
                    Tab.FOODS -> FoodsScreen(vm, modifier, open = ::open)
                    Tab.WORKOUTS -> WorkoutsScreen(wvm, vm, modifier, open = ::open)
                    Tab.PROGRESS -> Column(modifier) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Choices(listOf("stats", "weight"), progressPart, { if (it == "stats") "Stats and streaks" else "Weight" }, { "progress:$it" }) { progressPart = it }
                        }
                        if (progressPart == "stats") StatsScreen(svm, Modifier) else WeightScreen(vm, Modifier)
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
        }
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
