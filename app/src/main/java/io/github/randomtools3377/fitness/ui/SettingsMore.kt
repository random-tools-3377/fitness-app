package io.github.randomtools3377.fitness.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.Privacy
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.ReminderSettings
import io.github.randomtools3377.fitness.core.ReminderTime

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "08:30" for minutes after midnight. */
fun clockText(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

/** Minutes after midnight from "8:30", "08:30" or "830"; null if it isn't a time. */
fun parseClock(text: String): Int? {
    val t = text.trim()
    val (h, m) = when {
        ':' in t -> t.substringBefore(':').toIntOrNull() to t.substringAfter(':').toIntOrNull()
        t.length in 3..4 -> t.dropLast(2).toIntOrNull() to t.takeLast(2).toIntOrNull()
        t.length in 1..2 -> t.toIntOrNull() to 0
        else -> null to null
    }
    if (h == null || m == null || h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@Composable
private fun TimeBox(minute: Int, tag: String, onChange: (Int) -> Unit) {
    var text by remember(minute) { mutableStateOf(clockText(minute)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t.filter { it.isDigit() || it == ':' }.take(5); parseClock(text)?.let(onChange) },
        singleLine = true,
        isError = parseClock(text) == null,
        modifier = Modifier.width(96.dp).testTag(tag),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(days: Set<Int>, tag: String, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in 1..7) {
            FilterChip(
                selected = d in days,
                onClick = { onChange(if (d in days) days - d else days + d) },
                label = { Text(DAY_NAMES[d - 1]) },
                modifier = Modifier.testTag("$tag:$d"),
            )
        }
    }
}

/** Optional reminders for meals, water, workouts and weigh-ins, all scheduled on the phone. */
@Composable
fun RemindersSection(vm: MainViewModel, settings: AppSettings) {
    val r = settings.reminders
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save(new: ReminderSettings) {
        if (new.anyOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.setReminders(new)
    }
    Section("Reminders") {
        Text("Optional, and set on the phone itself. Tap a time to change it.", style = MaterialTheme.typography.bodySmall)
        for (meal in Meal.entries) {
            val t = r.meals[meal]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.title, Modifier.weight(1f))
                if (t?.on == true) TimeBox(t.minute, "time:${meal.name}") { m -> save(r.copy(meals = r.meals + (meal to t.copy(minute = m)))) }
                Switch(
                    checked = t?.on == true,
                    onCheckedChange = { on ->
                        val base = t ?: ReminderTime(ReminderSettings.MEAL_TIMES.getValue(meal))
                        save(r.copy(meals = r.meals + (meal to base.copy(on = on))))
                    },
                    modifier = Modifier.testTag("remind:${meal.name}"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Water", Modifier.weight(1f))
            Switch(checked = r.waterOn, onCheckedChange = { save(r.copy(waterOn = it)) }, modifier = Modifier.testTag("remind:WATER"))
        }
        if (r.waterOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Every")
                Choices(listOf(1, 2, 3, 4), r.waterEveryHours, { "$it h" }, { "water-every:$it" }) { save(r.copy(waterEveryHours = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("From")
                TimeBox(r.waterFrom, "water-from") { save(r.copy(waterFrom = it)) }
                Text("to")
                TimeBox(r.waterTo, "water-to") { save(r.copy(waterTo = it)) }
            }
        }
        DaysReminder("Workouts", r.workout, 18 * 60, setOf(1, 3, 5), "WORKOUT") { save(r.copy(workout = it)) }
        DaysReminder("Weigh-ins", r.weighIn, 7 * 60, setOf(1), "WEIGH_IN") { save(r.copy(weighIn = it)) }
    }
}

@Composable
private fun DaysReminder(title: String, t: ReminderTime?, defaultMinute: Int, defaultDays: Set<Int>, tag: String, onChange: (ReminderTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            if (t?.on == true) TimeBox(t.minute, "time:$tag") { onChange(t.copy(minute = it)) }
            Switch(
                checked = t?.on == true,
                onCheckedChange = { on -> onChange((t ?: ReminderTime(defaultMinute, defaultDays)).copy(on = on)) },
                modifier = Modifier.testTag("remind:$tag"),
            )
        }
        if (t?.on == true) DayChips(t.days, "days:$tag") { onChange(t.copy(days = it)) }
    }
}

/** What sits behind the phone's own lock, and hiding the app in the recent-apps screen. */
@Composable
fun PrivacySection(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    var hide by remember { mutableStateOf(Privacy.hideInRecents(context)) }
    Section("Privacy") {
        Text(
            "Choose what opens only with your phone's own lock (fingerprint, face or PIN). It locks again each time you leave the app.",
            style = MaterialTheme.typography.bodySmall,
        )
        for (tab in Tab.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tab.name in settings.locked,
                    onCheckedChange = { on -> vm.setLocked(if (on) settings.locked + tab.name else settings.locked - tab.name) },
                    modifier = Modifier.testTag("lock:${tab.name}"),
                )
                Text(tab.title)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text("The app is hidden in the recent-apps screen.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide in recent apps")
                    Text("On this phone, that also blocks screenshots of the app.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = hide, onCheckedChange = {
                    hide = it
                    Privacy.setHideInRecents(context, it)
                    (context as? Activity)?.let { a -> Privacy.apply(a) }
                })
            }
        }
    }
}

/** The automatic daily backup to a folder the user picks, keeping the last 7. */
@Composable
fun AutoBackupSection(vm: MainViewModel) {
    val folder by vm.backupFolder.collectAsState()
    val last by vm.lastBackup.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.setBackupFolder(uri) }
    Section("Automatic backup") {
        Text(
            "Once a day, when you open the app, everything is saved to a folder you choose. The newest 7 are kept. " +
                "A folder on a memory card, or one a cloud app keeps in step, keeps it off the phone.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(if (folder == null) "Off" else "To the folder \"$folder\"", modifier = Modifier.testTag("backup-folder"))
        last?.let { Text("Last backup: ${dayName(dayOfMillis(it), vm.today())}", style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(null) }, modifier = Modifier.testTag("choose-backup-folder")) {
                Text(if (folder == null) "Choose a folder" else "Change folder")
            }
            if (folder != null) TextButton(onClick = { vm.setBackupFolder(null) }) { Text("Turn off") }
        }
    }
}

@Composable
fun WorkoutsPerWeekRow(vm: MainViewModel, settings: AppSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Workouts a week, for the weekly streak")
        Choices((1..7).toList(), settings.workoutsPerWeek, { "$it" }, { "per-week:$it" }) { vm.setWorkoutsPerWeek(it) }
    }
}
