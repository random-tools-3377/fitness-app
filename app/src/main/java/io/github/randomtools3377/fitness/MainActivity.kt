package io.github.randomtools3377.fitness

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import io.github.randomtools3377.fitness.ui.FitnessApp
import io.github.randomtools3377.fitness.ui.FitnessTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.randomtools3377.fitness.ui.BodyViewModel
import io.github.randomtools3377.fitness.ui.MainViewModel
import io.github.randomtools3377.fitness.ui.StatsViewModel
import io.github.randomtools3377.fitness.ui.WorkoutViewModel

/** A FragmentActivity, so the privacy lock can ask for the phone's own lock. */
class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val wvm: WorkoutViewModel by viewModels()
    private val svm: StatsViewModel by viewModels()
    private val bvm: BodyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Privacy.apply(this)
        handle(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            FitnessTheme(settings.theme) {
                FitnessApp(vm, wvm, svm, bvm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        // After midnight, opening the app again shows the new day; and once a day, the automatic backup.
        vm.onAppOpened()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks what is behind the privacy lock again.
        vm.lockAgain()
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN)?.let { what ->
            vm.openFromReminder.value = what
            intent.removeExtra(EXTRA_OPEN)
        }
    }

    companion object {
        /** Opens the screen a reminder is about, like "WEIGH_IN". */
        const val EXTRA_OPEN = "open"
    }
}
