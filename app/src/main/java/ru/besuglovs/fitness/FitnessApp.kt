package ru.besuglovs.fitness

import android.app.Application
import ru.besuglovs.fitness.data.AppDatabase
import ru.besuglovs.fitness.data.FitnessRepository

class FitnessApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: FitnessRepository by lazy { FitnessRepository(database) }
    val settings: SettingsStorage by lazy { SettingsStorage(this) }
}
