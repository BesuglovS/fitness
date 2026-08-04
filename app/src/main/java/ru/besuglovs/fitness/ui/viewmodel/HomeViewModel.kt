package ru.besuglovs.fitness.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val fitnessApp = getApplication<FitnessApp>()
    private val repository: FitnessRepository = fitnessApp.repository
    private val settings get() = fitnessApp.settings

    val defaultRestSeconds = MutableStateFlow(settings.defaultRestSeconds)

    val recentWorkouts = repository.workouts()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Workout>())

    val workoutCount = repository.workoutCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val exerciseCount = repository.exerciseCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unfinished = repository.unfinishedWorkout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _createdWorkoutId = MutableStateFlow<Long?>(null)
    val createdWorkoutId: StateFlow<Long?> = _createdWorkoutId.asStateFlow()

    fun startWorkout() {
        viewModelScope.launch {
            val id = repository.createWorkout()
            _createdWorkoutId.value = id
        }
    }

    fun onWorkoutOpened() {
        _createdWorkoutId.value = null
    }

    fun discardUnfinished() {
        viewModelScope.launch {
            repository.discardUnfinished()
        }
    }
}
