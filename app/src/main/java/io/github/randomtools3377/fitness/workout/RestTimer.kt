package io.github.randomtools3377.fitness.workout

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.MainActivity
import io.github.randomtools3377.fitness.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A rest between sets: when it ends (epoch ms), how long it is, and for which exercise. */
data class Rest(val endsAt: Long, val seconds: Int, val exercise: String)

/**
 * The rest timer. It starts when a set is ticked. On screen it counts down; when it ends and
 * the app is in the background, the phone buzzes and shows a notification. The end time is
 * kept, so closing the app doesn't lose it, and the alarm is set on the phone itself.
 */
object RestTimer {
    private const val PREFS = "rest"
    private const val CHANNEL = "rest"
    private const val NOTIFICATION_ID = 1

    private val state = MutableStateFlow<Rest?>(null)
    val rest: StateFlow<Rest?> = state

    /** Reads a rest still running when the app was closed. */
    fun restore(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endsAt = p.getLong("endsAt", 0)
        state.value = if (endsAt > System.currentTimeMillis()) {
            Rest(endsAt, p.getInt("seconds", 90), p.getString("exercise", "") ?: "")
        } else {
            null
        }
    }

    fun start(context: Context, seconds: Int, exercise: String) {
        set(context, Rest(System.currentTimeMillis() + seconds * 1000L, seconds, exercise))
    }

    /** Adds (or takes off) time from the rest running now. */
    fun add(context: Context, seconds: Int) {
        val r = state.value ?: return
        val endsAt = maxOf(System.currentTimeMillis(), r.endsAt + seconds * 1000L)
        set(context, r.copy(endsAt = endsAt, seconds = (r.seconds + seconds).coerceAtLeast(1)))
    }

    fun stop(context: Context) {
        state.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        alarms(context).cancel(pending(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Called by the screen when the countdown reaches zero while the app is open. */
    fun finishedOnScreen(context: Context) {
        stop(context)
        buzz(context)
    }

    private fun set(context: Context, r: Rest) {
        state.value = r
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("endsAt", r.endsAt).putInt("seconds", r.seconds).putString("exercise", r.exercise).apply()
        val am = alarms(context)
        val pi = pending(context)
        am.cancel(pi)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.endsAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.endsAt, pi)
        }
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestDoneReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When the rest between sets is over"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buzz(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)) }
    }

    /** The alarm went off: if the app isn't on screen, say so with a notification. */
    internal fun alarmFired(context: Context) {
        val r = state.value
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val exercise = r?.exercise ?: p.getString("exercise", "") ?: ""
        p.edit().clear().apply()
        state.value = null
        val app = context.applicationContext as FitnessApplication
        if (app.inForeground) {
            buzz(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            buzz(context)
            return
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest is over")
            .setContentText(if (exercise.isBlank()) "Time for the next set." else "Time for the next set of $exercise.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }
}

class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimer.alarmFired(context)
    }
}
