package ru.besuglovs.fitness.ui.viewmodel

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
import ru.besuglovs.fitness.util.weightLabel

enum class CircuitPhase { SETUP, EXERCISE, REP_ENTRY }

data class RecordedSet(
    val weight: Double,
    val reps: Int,
    val durationSeconds: Int = 0,
    val restSeconds: Int? = null
)

class CircuitViewModel(
    app: Application,
    private val workoutId: Long,
    val restLimitSeconds: Int
) : AndroidViewModel(app) {

    private val fitnessApp = getApplication<FitnessApp>()
    private val repository: FitnessRepository = fitnessApp.repository

    private val _allExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val allExercises: StateFlow<List<Exercise>> = _allExercises.asStateFlow()

    private val _selectedExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val selectedExercises: StateFlow<List<Exercise>> = _selectedExercises.asStateFlow()

    private val _setupWeights = MutableStateFlow<Map<Long, String>>(emptyMap())
    val setupWeights: StateFlow<Map<Long, String>> = _setupWeights.asStateFlow()

    private val _setupReps = MutableStateFlow<Map<Long, String>>(emptyMap())
    val setupReps: StateFlow<Map<Long, String>> = _setupReps.asStateFlow()

    private val _phase = MutableStateFlow(CircuitPhase.SETUP)
    val phase: StateFlow<CircuitPhase> = _phase.asStateFlow()

    private val _circuitNumber = MutableStateFlow(1)
    val circuitNumber: StateFlow<Int> = _circuitNumber.asStateFlow()

    private val _exerciseIndex = MutableStateFlow(0)
    val exerciseIndex: StateFlow<Int> = _exerciseIndex.asStateFlow()

    private val _activeExercise = MutableStateFlow<Exercise?>(null)
    val activeExercise: StateFlow<Exercise?> = _activeExercise.asStateFlow()

    private val _setElapsed = MutableStateFlow(0L)
    val setElapsed: StateFlow<Long> = _setElapsed.asStateFlow()

    private val _restElapsed = MutableStateFlow(0L)
    val restElapsed: StateFlow<Long> = _restElapsed.asStateFlow()

    private val _restPaused = MutableStateFlow(false)
    val restPaused: StateFlow<Boolean> = _restPaused.asStateFlow()

    private val _setPaused = MutableStateFlow(false)
    val setPaused: StateFlow<Boolean> = _setPaused.asStateFlow()

    private val _pauseElapsed = MutableStateFlow(0L)
    val pauseElapsed: StateFlow<Long> = _pauseElapsed.asStateFlow()

    private val _lastWeights = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val lastWeights: StateFlow<Map<Long, Double>> = _lastWeights.asStateFlow()

    private val _completedSets = MutableStateFlow<Map<Long, List<RecordedSet>>>(emptyMap())
    val completedSets: StateFlow<Map<Long, List<RecordedSet>>> = _completedSets.asStateFlow()

    private val _entryWeights = MutableStateFlow<Map<Long, String>>(emptyMap())
    val entryWeights: StateFlow<Map<Long, String>> = _entryWeights.asStateFlow()

    private val _entryReps = MutableStateFlow<Map<Long, String>>(emptyMap())
    val entryReps: StateFlow<Map<Long, String>> = _entryReps.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var setJob: Job? = null
    private var restJob: Job? = null
    private var setRunningSeconds = 0L
    private var _accumulatedPause = 0L

    private val _setDurations = mutableMapOf<Long, MutableList<Int>>()
    private val _durationIndex = mutableMapOf<Long, Int>()
    private var _pendingRestSeconds: Int? = null

    init {
        viewModelScope.launch {
            repository.exercises().collect { _allExercises.value = it }
        }
    }

    fun addExercise(exercise: Exercise) {
        if (_selectedExercises.value.any { it.id == exercise.id }) return
        _selectedExercises.value = _selectedExercises.value + exercise
    }

    fun updateSetupWeight(exerciseId: Long, value: String) {
        _setupWeights.value = _setupWeights.value + (exerciseId to value)
    }

    fun updateSetupReps(exerciseId: Long, value: String) {
        _setupReps.value = _setupReps.value + (exerciseId to value)
    }

    fun removeExercise(exerciseId: Long) {
        _selectedExercises.value = _selectedExercises.value.filterNot { it.id == exerciseId }
        _setupWeights.value = _setupWeights.value - exerciseId
        _setupReps.value = _setupReps.value - exerciseId
    }

    fun startTraining() {
        if (_selectedExercises.value.isEmpty()) return
        val initialWeights = _setupWeights.value.mapNotNull { (id, value) ->
            value.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { id to it }
        }.toMap()
        _lastWeights.value = initialWeights
        _circuitNumber.value = 1
        _exerciseIndex.value = 0
        _activeExercise.value = _selectedExercises.value.first()
        _phase.value = CircuitPhase.EXERCISE
        _accumulatedPause = 0L
        startSetTimer()
    }

    fun completeSet() {
        val list = _selectedExercises.value
        if (list.isEmpty()) return
        stopTimers()
        val exerciseId = _activeExercise.value?.id ?: list[_exerciseIndex.value].id
        _setDurations.getOrPut(exerciseId) { mutableListOf() }.add(_setElapsed.value.toInt())
        _accumulatedPause += _pauseElapsed.value
        if (_exerciseIndex.value < list.size - 1) {
            _exerciseIndex.value += 1
            _activeExercise.value = list[_exerciseIndex.value]
            _phase.value = CircuitPhase.EXERCISE
            startSetTimer()
        } else {
            initEntryState()
            _phase.value = CircuitPhase.REP_ENTRY
            startRestTimer()
        }
    }

    fun updateEntryWeight(exerciseId: Long, value: String) {
        _entryWeights.value = _entryWeights.value + (exerciseId to value)
    }

    fun updateEntryReps(exerciseId: Long, value: String) {
        _entryReps.value = _entryReps.value + (exerciseId to value)
    }

    fun startNextCircuit() {
        if (_selectedExercises.value.isEmpty()) return
        commitEntrySets()
        _pendingRestSeconds = _restElapsed.value.toInt()
        _circuitNumber.value += 1
        _exerciseIndex.value = 0
        _activeExercise.value = _selectedExercises.value.first()
        _phase.value = CircuitPhase.EXERCISE
        startSetTimer()
    }

    fun lastWeightOf(exerciseId: Long): Double? = _lastWeights.value[exerciseId]

    fun totalSetsCount(): Int {
        var count = _completedSets.value.values.sumOf { it.size }
        if (_phase.value == CircuitPhase.REP_ENTRY) {
            for (ex in _selectedExercises.value) {
                val w = _entryWeights.value[ex.id]?.replace(',', '.')?.toDoubleOrNull()
                val r = _entryReps.value[ex.id]?.toIntOrNull()
                if (w != null && w >= 0 && r != null && r > 0) count++
            }
        }
        return count
    }

    private fun initEntryState() {
        val weights = mutableMapOf<Long, String>()
        val reps = mutableMapOf<Long, String>()
        for (ex in _selectedExercises.value) {
            weights[ex.id] = _lastWeights.value[ex.id]?.let { weightLabel(it) } ?: ""
            reps[ex.id] = _setupReps.value[ex.id] ?: ""
        }
        _entryWeights.value = weights
        _entryReps.value = reps
    }

    private fun commitEntrySets() {
        val updated = _completedSets.value.toMutableMap()
        val weights = _lastWeights.value.toMutableMap()
        val rest = (_pendingRestSeconds ?: 0) + _accumulatedPause.toInt()
        for (ex in _selectedExercises.value) {
            val w = _entryWeights.value[ex.id]?.replace(',', '.')?.toDoubleOrNull() ?: continue
            val r = _entryReps.value[ex.id]?.toIntOrNull() ?: continue
            if (w < 0 || r <= 0) continue
            val durationIdx = _durationIndex[ex.id] ?: 0
            val duration = _setDurations[ex.id]?.getOrNull(durationIdx) ?: 0
            _durationIndex[ex.id] = durationIdx + 1
            updated[ex.id] = (updated[ex.id].orEmpty()) +
                RecordedSet(w, r, durationSeconds = duration, restSeconds = rest)
            weights[ex.id] = w
        }
        _pendingRestSeconds = null
        _accumulatedPause = 0L
        _completedSets.value = updated
        _lastWeights.value = weights
    }

    private fun startSetTimer() {
        setJob?.cancel()
        setRunningSeconds = 0L
        _setElapsed.value = 0
        _pauseElapsed.value = 0
        _setPaused.value = false
        setJob = viewModelScope.launch {
            while (_phase.value == CircuitPhase.EXERCISE) {
                delay(1000)
                if (_setPaused.value) {
                    _pauseElapsed.value += 1
                } else {
                    setRunningSeconds += 1
                    _setElapsed.value = setRunningSeconds
                }
            }
        }
    }

    private fun startRestTimer() {
        restJob?.cancel()
        _restElapsed.value = 0L
        _restPaused.value = false
        restJob = viewModelScope.launch {
            while (_phase.value == CircuitPhase.REP_ENTRY) {
                delay(1000)
                if (!_restPaused.value) {
                    _restElapsed.value += 1
                }
            }
        }
    }

    fun toggleRestPause() {
        if (_phase.value != CircuitPhase.REP_ENTRY) return
        _restPaused.value = !_restPaused.value
    }

    fun toggleSetPause() {
        if (_phase.value != CircuitPhase.EXERCISE) return
        _setPaused.value = !_setPaused.value
    }

    private fun stopTimers() {
        setJob?.cancel()
        restJob?.cancel()
        setJob = null
        restJob = null
    }

    fun finishWorkout() {
        viewModelScope.launch {
            stopTimers()
            commitEntrySets()
            val exercisesWithSets = _selectedExercises.value
                .filter { _completedSets.value[it.id]?.isNotEmpty() == true }
                .map { ex ->
                    ExerciseWithSets(
                        exerciseId = ex.id,
                        sets = _completedSets.value[ex.id].orEmpty().mapIndexed { i, s ->
                            SetEntry(
                                setNumber = i + 1,
                                weightKg = s.weight,
                                reps = s.reps,
                                restSeconds = s.restSeconds,
                                durationSeconds = s.durationSeconds.takeIf { it > 0 }
                            )
                        }
                    )
                }
            repository.saveWorkoutWithSets(
                workoutId = workoutId,
                endTime = System.currentTimeMillis(),
                notes = "",
                exercises = exercisesWithSets,
                isCircuit = true
            )
            _saved.value = true
        }
    }
}
