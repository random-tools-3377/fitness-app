package io.github.randomtools3377.fitness

import android.app.Application
import io.github.randomtools3377.fitness.data.AppDatabase
import io.github.randomtools3377.fitness.data.Repository

class FitnessApplication : Application() {
    /** Opened on first use, not at start-up. */
    val container: Container by lazy { Container(this) }
}

class Container(app: Application) {
    val db: AppDatabase = AppDatabase.open(app)
    val repository = Repository(app, db)
}
