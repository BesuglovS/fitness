с тоpackage ru.besuglovs.fitness.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.data.ExerciseWithSets
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.SetEntry

data class SetUi(
    val weight: String = "",
    val reps: String = "",
    val restSeconds: Int? = null
)

data class ExerciseUi(
    val exercise: Exercise,
    val sets: List<SetUi> = emptyList()
)

data class ActiveSetUi(
    val exercise: Exercise,
    val weight: String,
    val restTakenSeconds: Int?
)

class WorkoutViewModel(
    app: Application,
    private val workoutId: Long,
    val restLimitSeconds: Int
) : AndroidViewModel(app) {

    private val fitnessApp = getApplication<FitnessApp>()
    private val repository: FitnessRepository = fitnessApp.repository
    private val settings get() = fitnessApp.settings

    private val _exercises = MutableStateFlow<List<ExerciseUi>>(emptyList())
    val exercises: StateFlow<List<ExerciseUi>> = _exercises.asStateFlow()

    private val _allExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val allExercises: StateFlow<List<Exercise>> = _allExercises.asStateFlow()

    private val _restElapsed = MutableStateFlow<Int?>(null)
    val restElapsed: StateFlow<Int?> = _restElapsed.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _weightInput = MutableStateFlow("")
    val weightInput: StateFlow<String> = _weightInput.asStateFlow()

    private val _activeSet = MutableStateFlow<ActiveSetUi?>(null)
    val activeSet: StateFlow<ActiveSetUi?> = _activeSet.asStateFlow()

    private val _setElapsed = MutableStateFlow(0L)
    val setElapsed: StateFlow<Long> = _setElapsed.asStateFlow()

    private var restJob: Job? = null
    private var setJob: Job? = null
    private var setStartedAt: Long = 0L

    init {
        viewModelScope.launch {
            repository.exercises().collect { _allExercises.value = it }
        }
    }

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
    }

    fun updateWeight(value: String) {
        _weightInput.value = value
    }

    fun startSet() {
        val exerciseId = _selectedExerciseId.value ?: return
        val weight = _weightInput.value.trim()
        if (weight.isEmpty()) return
        val exercise = _allExercises.value.firstOrNull { it.id == exerciseId } ?: return
        if (_exercises.value.none { it.exercise.id == exerciseId }) {
            _exercises.value = _exercises.value + ExerciseUi(exercise = exercise)
        }
        val restTaken = _restElapsed.value
        cancelRest()
        setStartedAt = System.currentTimeMillis()
        _setElapsed.value = 0L
        _activeSet.value = ActiveSetUi(
            exercise = exercise,
            weight = weight,
            restTakenSeconds = restTaken
        )
        startSetTimer()
    }

    fun completeSet(reps: Int) {
        val active = _activeSet.value ?: return
        val list = _exercises.value.toMutableList()
        val idx = list.indexOfFirst { it.exercise.id == active.exercise.id }
        if (idx >= 0) {
            val ex = list[idx]
            val newSet = SetUi(
                weight = active.weight,
                reps = if (reps > 0) reps.toString() else "",
                restSeconds = active.restTakenSeconds
            )
            list[idx] = ex.copy(sets = ex.sets + newSet)
            _exercises.value = list
        }
        stopSetTimer()
        _activeSet.value = null
        startRestTimer()
    }

    private fun startSetTimer() {
        setJob?.cancel()
        setJob = viewModelScope.launch {
            while (_activeSet.value != null) {
                _setElapsed.value = (System.currentTimeMillis() - setStartedAt) / 1000
                delay(1000)
            }
        }
    }

    private fun stopSetTimer() {
        setJob?.cancel()
        setJob = null
    }

    fun startRestTimer() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            _restElapsed.value = 0
            while (true) {
                delay(1000)
                _restElapsed.value = (_restElapsed.value ?: 0) + 1
            }
        }
    }

    fun cancelRest() {
        restJob?.cancel()
        restJob = null
        _restElapsed.value = null
    }

    fun finishWorkout() {
        viewModelScope.launch {
            stopSetTimer()
            cancelRest()
            val exercisesWithSets = _exercises.value.map { ex ->
                ExerciseWithSets(
                    exerciseId = ex.exercise.id,
                    sets = ex.sets.mapNotNull { s ->
                        val weight = s.weight.toDoubleOrNull()
                        val reps = s.reps.toIntOrNull()
                        if (weight == null && reps == null) null
                        else if (weight != null && weight <= 0) null
                        else SetEntry(
                            setNumber = 0,
                            weightKg = weight,
                            reps = reps,
                            restSeconds = s.restSeconds
                        )
                    }
                )
            }
            repository.saveWorkoutWithSets(
                workoutId = workoutId,
                endTime = System.currentTimeMillis(),
                notes = "",
                exercises = exercisesWithSets
            )
            _saved.value = true
        }
    }
}
