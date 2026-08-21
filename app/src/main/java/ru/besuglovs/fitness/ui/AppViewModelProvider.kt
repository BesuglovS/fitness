package ru.besuglovs.fitness.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.ui.viewmodel.CircuitViewModel
import ru.besuglovs.fitness.ui.viewmodel.ExerciseLibraryViewModel
import ru.besuglovs.fitness.ui.viewmodel.HistoryViewModel
import ru.besuglovs.fitness.ui.viewmodel.HomeViewModel
import ru.besuglovs.fitness.ui.viewmodel.ProgressViewModel
import ru.besuglovs.fitness.ui.viewmodel.SettingsViewModel
import ru.besuglovs.fitness.ui.viewmodel.WorkoutDetailViewModel
import ru.besuglovs.fitness.ui.viewmodel.WorkoutViewModel

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp)
        }
        initializer {
            ExerciseLibraryViewModel(
                (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp).repository
            )
        }
        initializer {
            HistoryViewModel(
                (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp).repository
            )
        }
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp
            val handle = createSavedStateHandle()
            val id = handle.get<Long>("workoutId") ?: 0L
            val restSeconds = handle.get<Int>("restSeconds") ?: 90
            WorkoutViewModel(app, id, restSeconds)
        }
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp
            val handle = createSavedStateHandle()
            val id = handle.get<Long>("workoutId") ?: 0L
            val restSeconds = handle.get<Int>("restSeconds") ?: 90
            CircuitViewModel(app, id, restSeconds)
        }
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp
            val id = createSavedStateHandle().get<Long>("workoutId") ?: 0L
            WorkoutDetailViewModel(app, app.repository, id)
        }
        initializer {
            ProgressViewModel(
                (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp).repository
            )
        }
        initializer {
            SettingsViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FitnessApp)
        }
    }
}
