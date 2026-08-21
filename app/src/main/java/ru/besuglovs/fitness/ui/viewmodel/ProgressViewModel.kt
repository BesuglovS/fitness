package ru.besuglovs.fitness.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.ProgressPoint

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(repository: FitnessRepository) : ViewModel() {

    val exercises = repository.exercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Exercise>())

    private val _selectedExerciseId = MutableStateFlow(-1L)
    val selectedExerciseId: StateFlow<Long> = _selectedExerciseId.asStateFlow()

    private val selectedExercise = combine(exercises, _selectedExerciseId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val progression = selectedExercise.flatMapLatest { ex ->
        if (ex == null) flowOf(emptyList()) else repository.progression(ex.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ProgressPoint>())

    val maxWeight = selectedExercise.flatMapLatest { ex ->
        if (ex == null) flowOf(null) else repository.maxWeight(ex.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val best1RM = selectedExercise.flatMapLatest { ex ->
        if (ex == null) flowOf(null) else repository.best1RM(ex.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalWorkouts = repository.workoutCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun selectExercise(id: Long) {
        _selectedExerciseId.value = id
    }
}
