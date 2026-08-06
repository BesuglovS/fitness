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

enum class WorkoutPhase { SETUP, EXERCISE, REST }

data class WorkoutRecordedSet(
    val weight: Double,
    val reps: Int,
    val durationSeconds: Int = 0,
    val restSeconds: Int? = null
)

class WorkoutViewModel(
    app: Application,
    private val workoutId: Long,
    val restLimitSeconds: Int
) : AndroidViewModel(app) {

    private val fitnessApp = getApplication<FitnessApp>()
    private val repository: FitnessRepository = fitnessApp.repository

    private val _allExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val allExercises: StateFlow<List<Exercise>> = _allExercises.asStateFlow()

    private val _currentExercise = MutableStateFlow<Exercise?>(null)
    val currentExercise: StateFlow<Exercise?> = _currentExercise.asStateFlow()

    private val _phase = MutableStateFlow(WorkoutPhase.SETUP)
    val phase: StateFlow<WorkoutPhase> = _phase.asStateFlow()

    private val _setupWeights = MutableStateFlow<Map<Long, String>>(emptyMap())
    val setupWeights: StateFlow<Map<Long, String>> = _setupWeights.asStateFlow()

    private val _setupReps = MutableStateFlow<Map<Long, String>>(emptyMap())
    val setupReps: StateFlow<Map<Long, String>> = _setupReps.asStateFlow()

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

    private val _completedSets = MutableStateFlow<Map<Long, List<WorkoutRecordedSet>>>(emptyMap())
    val completedSets: StateFlow<Map<Long, List<WorkoutRecordedSet>>> = _completedSets.asStateFlow()

    private val _entryExerciseId = MutableStateFlow<Long?>(null)
    val entryExerciseId: StateFlow<Long?> = _entryExerciseId.asStateFlow()

    private val _entryWeight = MutableStateFlow("")
    val entryWeight: StateFlow<String> = _entryWeight.asStateFlow()

    private val _entryReps = MutableStateFlow("")
    val entryReps: StateFlow<String> = _entryReps.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var setJob: Job? = null
    private var restJob: Job? = null
    private var setRunningSeconds = 0L
    private var lastCommittedSet: Pair<Long, Int>? = null

    private val _setDurations = mutableMapOf<Long, MutableList<Int>>()
    private val _durationIndex = mutableMapOf<Long, Int>()

    init {
        viewModelScope.launch {
            repository.exercises().collect { _allExercises.value = it }
        }
    }

    fun selectExercise(exercise: Exercise) {
        if (_currentExercise.value?.id == exercise.id) return
        _currentExercise.value = exercise
        val lastWeight = _lastWeights.value[exercise.id]?.let { weightLabel(it) }
        if (lastWeight != null) {
            _setupWeights.value = _setupWeights.value + (exercise.id to lastWeight)
        }
        if (!_setupReps.value.containsKey(exercise.id)) {
            _setupReps.value = _setupReps.value + (exercise.id to "")
        }
    }

    fun updateSetupWeight(exerciseId: Long, value: String) {
        _setupWeights.value = _setupWeights.value + (exerciseId to value)
    }

    fun updateSetupReps(exerciseId: Long, value: String) {
        _setupReps.value = _setupReps.value + (exerciseId to value)
    }

    fun updateEntryWeight(value: String) {
        _entryWeight.value = value
    }

    fun updateEntryReps(value: String) {
        _entryReps.value = value
    }

    fun startTraining() {
        val exercise = _currentExercise.value ?: return
        if (_setupWeights.value[exercise.id]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 } == null) return
        val weight = _setupWeights.value[exercise.id]!!.replace(',', '.').toDouble()
        _lastWeights.value = _lastWeights.value + (exercise.id to weight)
        _phase.value = WorkoutPhase.EXERCISE
        startSetTimer()
    }

    fun completeSet() {
        val exercise = _currentExercise.value ?: return
        stopTimers()
        val exId = exercise.id
        _setDurations.getOrPut(exId) { mutableListOf() }.add(_setElapsed.value.toInt())
        val pause = _pauseElapsed.value.toInt()
        if (pause > 0) {
            lastCommittedSet?.let { (prevExId, prevIndex) ->
                val list = _completedSets.value[prevExId]?.toMutableList()
                if (list != null && prevIndex in list.indices) {
                    val prev = list[prevIndex]
                    list[prevIndex] = prev.copy(restSeconds = (prev.restSeconds ?: 0) + pause)
                    _completedSets.value = _completedSets.value + (prevExId to list)
                }
            }
        }
        _entryExerciseId.value = exId
        _entryWeight.value = _setupWeights.value[exId]?.takeIf { it.isNotBlank() }
            ?: _lastWeights.value[exId]?.let { weightLabel(it) } ?: ""
        _entryReps.value = _setupReps.value[exId] ?: ""
        _phase.value = WorkoutPhase.REST
        _restElapsed.value = 0L
        _restPaused.value = false
        startRestTimer()
    }

    fun nextApproach() {
        if (_phase.value != WorkoutPhase.REST) return
        val exId = _entryExerciseId.value ?: return
        val rest = _restElapsed.value.toInt()
        restJob?.cancel()
        commitEntrySet(exId, rest)
        val exercise = _currentExercise.value ?: return
        _phase.value = WorkoutPhase.EXERCISE
        startSetTimer()
    }

    fun lastWeightOf(exerciseId: Long): Double? =
        _setupWeights.value[exerciseId]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 }
            ?: _lastWeights.value[exerciseId]

    fun totalSetsCount(): Int {
        var count = _completedSets.value.values.sumOf { it.size }
        if (_phase.value == WorkoutPhase.REST && _entryExerciseId.value != null) {
            val w = _entryWeight.value.replace(',', '.').toDoubleOrNull()
            val r = _entryReps.value.toIntOrNull()
            if (w != null && w >= 0 && r != null && r > 0) count++
        }
        return count
    }

    private fun commitEntrySet(exId: Long, restSeconds: Int? = null) {
        val w = _entryWeight.value.replace(',', '.').toDoubleOrNull() ?: return
        val r = _entryReps.value.toIntOrNull() ?: return
        if (w < 0 || r <= 0) return
        val durationIdx = _durationIndex[exId] ?: 0
        val duration = _setDurations[exId]?.getOrNull(durationIdx) ?: 0
        _durationIndex[exId] = durationIdx + 1
        val list = _completedSets.value[exId].orEmpty()
        _completedSets.value = _completedSets.value + (exId to (list +
            WorkoutRecordedSet(w, r, durationSeconds = duration, restSeconds = restSeconds)))
        lastCommittedSet = exId to list.size
        _lastWeights.value = _lastWeights.value + (exId to w)
    }

    fun toggleRestPause() {
        if (_phase.value != WorkoutPhase.REST) return
        _restPaused.value = !_restPaused.value
    }

    fun toggleSetPause() {
        if (_phase.value != WorkoutPhase.EXERCISE) return
        _setPaused.value = !_setPaused.value
    }

    private fun startSetTimer() {
        setJob?.cancel()
        setRunningSeconds = 0L
        _setElapsed.value = 0
        _pauseElapsed.value = 0
        _setPaused.value = false
        setJob = viewModelScope.launch {
            while (_phase.value == WorkoutPhase.EXERCISE) {
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
        restJob = viewModelScope.launch {
            while (_phase.value == WorkoutPhase.REST) {
                delay(1000)
                if (!_restPaused.value) {
                    _restElapsed.value += 1
                }
            }
        }
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
            _entryExerciseId.value?.let { commitEntrySet(it) }
            val exercisesWithSets = _completedSets.value
                .filter { it.value.isNotEmpty() }
                .map { (exId, sets) ->
                    ExerciseWithSets(
                        exerciseId = exId,
                        sets = sets.mapIndexed { i, s ->
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
                exercises = exercisesWithSets
            )
            _saved.value = true
        }
    }
}