package io.github.randomtools3377.fitness

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.fitness.data.AppDatabase
import io.github.randomtools3377.fitness.data.toCore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A database made by 0.1 moves to the current layout with nothing lost. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun aVersion1DatabaseKeepsItsDiaryAndGainsWorkouts() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO foods (id, name, brand, unit, kcal, protein, carbs, fat, portions, createdAt, hidden) VALUES (1, 'Oats', '', 'GRAM', 380, 13, 60, 7, '[]', 0, 0)")
            execSQL("INSERT INTO entries (id, day, meal, foodId, builtInId, name, quantity, portion, unit, kcal, protein, carbs, fat, createdAt) VALUES (1, 20401, 'BREAKFAST', 1, NULL, 'Oats', 50, NULL, 'GRAM', 190, 6.5, 30, 3.5, 5)")
            execSQL("INSERT INTO weigh_ins (id, day, kg, at) VALUES (1, 20401, 72.5, 6)")
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            runBlocking {
                val dao = db.dao()
                assertEquals("Oats", dao.food(1)!!.toCore().name)
                assertEquals(190.0, dao.entry(1)!!.toCore().nutrients.kcal, 1e-9)
                assertEquals(72.5, dao.allWeighIns().single().kg, 1e-9)
                assertTrue(dao.allWorkouts().isEmpty())
                assertTrue(dao.savedWorkouts().first().isEmpty())
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
