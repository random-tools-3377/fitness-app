package io.github.randomtools3377.fitness.fasting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.randomtools3377.fitness.MainActivity
import io.github.randomtools3377.fitness.R
import io.github.randomtools3377.fitness.reminders.ReminderScheduler

/** The notification when a fast is done, from an alarm set on the phone. */
object FastAlarm {
    private const val CHANNEL = "fasting"
    private const val ID = 7

    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Fasting")
                .setDescription("When a fast is done")
                .build(),
        )
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 3, Intent(context, FastDoneReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun set(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        am.cancel(pi)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        NotificationManagerCompat.from(context).cancel(ID)
    }

    internal fun fired(context: Context) {
        if (!ReminderScheduler.canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "FAST"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your fast is done")
            .setContentText("Well done. End it in the app when you eat.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, n)
    }
}

class FastDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FastAlarm.fired(context)
    }
}
