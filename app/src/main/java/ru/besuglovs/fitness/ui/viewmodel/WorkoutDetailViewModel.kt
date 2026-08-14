package ru.besuglovs.fitness.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.HeartRateSample

class WorkoutDetailViewModel(
    app: FitnessApp,
    repository: FitnessRepository,
    workoutId: Long
) : ViewModel() {

    val details = repository.workoutDetails(workoutId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val heartRateSamples: kotlinx.coroutines.flow.StateFlow<List<HeartRateSample>> =
        repository.heartRateSamples(workoutId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
