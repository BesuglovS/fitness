package ru.besuglovs.fitness.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.WorkoutSummary

class HistoryViewModel(private val repository: FitnessRepository) : ViewModel() {

    val summaries = repository.summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<WorkoutSummary>())

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { repository.deleteWorkout(id) }
    }
}
