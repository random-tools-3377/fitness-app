package io.github.randomtools3377.fitness.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.MainActivity
import io.github.randomtools3377.fitness.R
import io.github.randomtools3377.fitness.core.DueReminder
import io.github.randomtools3377.fitness.core.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Reminders at set times, scheduled on the phone. One alarm is kept, at the next reminder. When
 * it goes off, the reminders due are shown and the next alarm is set. Nothing goes online.
 */
object ReminderScheduler {
    const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val LAST = "last_fired"

    private fun LocalDateTime.millis(): Long = toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    private fun Long.local(): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Reminders")
                .setDescription("Meals, water, workouts and weigh-ins, at the times you chose")
                .build(),
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets the alarm for the next reminder, or cancels it when none are on. */
    fun schedule(context: Context, settings: ReminderSettings) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        am.cancel(pi)
        val now = Clock.System.now().toEpochMilliseconds().local()
        val next = settings.next(now) ?: return
        val at = next.at.millis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Shows what came due since the last time, then sets the next alarm. */
    internal suspend fun fire(context: Context) {
        val repo = (context.applicationContext as FitnessApplication).container.repository
        val settings = repo.settingsOnce().reminders
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nowMs = Clock.System.now().toEpochMilliseconds()
        // Anything due in the last 15 minutes, so a late alarm still shows its reminder.
        val from = prefs.getLong(LAST, nowMs - 15 * 60_000).coerceAtLeast(nowMs - 15 * 60_000)
        val due = settings.due(from.local(), nowMs.local())
        prefs.edit().putLong(LAST, nowMs).apply()
        for ((i, r) in due.withIndex()) show(context, r, i)
        schedule(context, settings)
    }

    private fun show(context: Context, r: DueReminder, i: Int) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 10 + r.kind.ordinal, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, r.kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(100 + r.kind.ordinal * 10 + (r.meal?.ordinal ?: 0) + i, n)
    }
}

/** The reminder alarm, and setting it again after the phone restarts or its clock changes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == null) {
                    ReminderScheduler.fire(context)
                } else {
                    val repo = (context.applicationContext as FitnessApplication).container.repository
                    ReminderScheduler.schedule(context, repo.settingsOnce().reminders)
                }
            } finally {
                result.finish()
            }
        }
    }
}
