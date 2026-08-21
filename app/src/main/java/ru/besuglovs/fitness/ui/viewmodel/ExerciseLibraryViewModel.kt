package ru.besuglovs.fitness.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.data.FitnessRepository

class ExerciseLibraryViewModel(private val repository: FitnessRepository) : ViewModel() {

    val exercises = repository.exercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun clearDeleteError() {
        _deleteError.value = null
    }

    fun addExercise(name: String, muscleGroup: String, category: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.insertExercise(
                Exercise(
                    name = trimmed,
                    muscleGroup = muscleGroup.trim(),
                    category = category
                )
            )
        }
    }

    fun updateExercise(exercise: Exercise) {
        viewModelScope.launch { repository.updateExercise(exercise) }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            if (repository.exerciseUsageCount(exercise.id) > 0) {
                _deleteError.value =
                    "«${exercise.name}» используется в тренировках и не может быть удалено."
            } else {
                repository.deleteExercise(exercise)
            }
        }
    }
}
