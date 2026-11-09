package io.github.randomtools3377.fitness.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.randomtools3377.fitness.FitnessApplication
import io.github.randomtools3377.fitness.MainActivity
import io.github.randomtools3377.fitness.R
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The home-screen widget (0.10.0): today's calories left and water, with buttons to add a glass
 * of water or open the app to add food. If the diary is behind the privacy lock, it shows no
 * numbers.
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                update(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ADD_WATER) {
            val pending = goAsync()
            scope.launch {
                try {
                    val repo = (context.applicationContext as FitnessApplication).container.repository
                    val s = repo.settingsOnce()
                    repo.addWater(today(), s.water.glassMl, Clock.System.now().toEpochMilliseconds())
                    update(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_ADD_WATER = "io.github.randomtools3377.fitness.widget.ADD_WATER"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

        /** Redraws every copy of the widget with today's numbers. Safe to call often. */
        suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val repo = (context.applicationContext as FitnessApplication).container.repository
            val s = repo.settingsOnce()
            val units = s.units ?: UnitSettings.forRegion(Locale.getDefault().country)
            val day = today()
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            if ("DIARY" in s.locked) {
                views.setTextViewText(R.id.widget_kcal, "Locked")
                views.setTextViewText(R.id.widget_detail, "Open the app to see today")
                views.setTextViewText(R.id.widget_water, "")
            } else {
                val eaten = repo.entriesOnce(day).filter { !it.planned }.sumOf { it.nutrients.kcal }
                val workouts = repo.workouts.first().filter { it.day == day && it.finished }
                val burned = workouts.sumOf { it.burnedKcal } * (if (s.burnedHalf) 0.5 else 1.0) + repo.stepsKcal(day, s)
                val target = io.github.randomtools3377.fitness.core.DayTarget.resolver(
                    s, io.github.randomtools3377.fitness.core.Weight.latest(repo.weighInsOnce())?.kg,
                    repo.workouts.first().filter { it.finished }.map { it.day }.toSet(), day.year,
                )(day)
                if (target == null) {
                    views.setTextViewText(R.id.widget_kcal, Units.energy(eaten, units.energy))
                    views.setTextViewText(R.id.widget_detail, "eaten today")
                } else {
                    val left = target + burned - eaten
                    views.setTextViewText(R.id.widget_kcal, Units.energy(kotlin.math.abs(left), units.energy))
                    views.setTextViewText(R.id.widget_detail, if (left >= 0) "left today" else "over today")
                }
                val water = repo.waterOn(day)
                views.setTextViewText(
                    R.id.widget_water,
                    "Water ${Units.volume(water.toDouble(), units.volume)} of ${Units.volume(s.water.targetMl.toDouble(), units.volume)}",
                )
            }
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            val addFood = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN, "ADD_FOOD")
            views.setOnClickPendingIntent(R.id.widget_add_food, PendingIntent.getActivity(context, 2, addFood, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            val water = Intent(context, TodayWidget::class.java).setAction(ACTION_ADD_WATER)
            views.setOnClickPendingIntent(R.id.widget_add_water, PendingIntent.getBroadcast(context, 3, water, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            views.setTextViewText(R.id.widget_add_water, "+ Glass")
            manager.updateAppWidget(ids, views)
        }

        /** Called when the day's data changes, so the widget doesn't lag behind the app. */
        fun refresh(context: Context) {
            scope.launch { runCatching { update(context) } }
        }
    }
}
