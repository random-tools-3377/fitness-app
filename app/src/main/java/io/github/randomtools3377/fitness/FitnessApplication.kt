package io.github.randomtools3377.fitness

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.randomtools3377.fitness.data.AppDatabase
import io.github.randomtools3377.fitness.data.Repository
import io.github.randomtools3377.fitness.reminders.ReminderScheduler
import io.github.randomtools3377.fitness.workout.RestTimer

class FitnessApplication : Application() {
    /** Opened on first use, not at start-up. */
    val container: Container by lazy { Container(this) }

    private var started = 0

    /** Whether the app is on screen, so the rest timer knows whether to show a notification. */
    val inForeground: Boolean get() = started > 0

    override fun onCreate() {
        super.onCreate()
        RestTimer.createChannel(this)
        RestTimer.restore(this)
        ReminderScheduler.createChannel(this)
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) { started++ }
                override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0) }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }
}

class Container(app: Application) {
    val db: AppDatabase = AppDatabase.open(app)
    val repository = Repository(app, db)
}
