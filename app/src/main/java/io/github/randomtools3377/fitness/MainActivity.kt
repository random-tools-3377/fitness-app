package io.github.randomtools3377.fitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.randomtools3377.fitness.ui.FitnessApp
import io.github.randomtools3377.fitness.ui.FitnessTheme
import io.github.randomtools3377.fitness.ui.MainViewModel
import io.github.randomtools3377.fitness.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val wvm: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FitnessTheme {
                FitnessApp(vm, wvm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // After midnight, opening the app again shows the new day.
        vm.onAppOpened()
    }
}
