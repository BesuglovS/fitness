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
import org.json.JSONArray
import org.json.JSONObject
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.data.ExerciseWithSets
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.SetEntry
import ru.besuglovs.fitness.data.decodeDoubleMap
import ru.besuglovs.fitness.data.decodeIntListMap
import ru.besuglovs.fitness.data.decodeIntMap
import ru.besuglovs.fitness.data.decodeStringMap
import ru.besuglovs.fitness.data.encodeDoubleMap
import ru.besuglovs.fitness.data.encodeIntListMap
import ru.besuglovs.fitness.data.encodeIntMap
import ru.besuglovs.fitness.data.encodeStringMap
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
    restLimitSeconds: Int
) : AndroidViewModel(app) {

    private val fitnessApp = getApplication<FitnessApp>()
    private val repository: FitnessRepository = fitnessApp.repository

    var restLimitSeconds = restLimitSeconds
        private set

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

    private val _exited = MutableStateFlow(false)
    val exited: StateFlow<Boolean> = _exited.asStateFlow()

    private val _resumeGapSeconds = MutableStateFlow<Long?>(null)
    val resumeGapSeconds: StateFlow<Long?> = _resumeGapSeconds.asStateFlow()

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
        viewModelScope.launch {
            val workout = repository.getWorkoutOnce(workoutId)
            val sessionJson = workout?.sessionJson
            if (workout?.endTime == null && !sessionJson.isNullOrBlank()) {
                val wasRestore = restoreSession(sessionJson)
                val gap = workout.pausedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                _resumeGapSeconds.value = gap.takeIf { it > 30 }
                if (wasRestore && _resumeGapSeconds.value == null) {
                    startResumedTimers()
                }
            }
        }
    }

    private fun startResumedTimers() {
        when (_phase.value) {
            WorkoutPhase.REST -> startRestTimer()
            WorkoutPhase.EXERCISE -> startSetTimer(initialElapsed = _setElapsed.value)
            WorkoutPhase.SETUP -> Unit
        }
    }

    fun addExercise(name: String, muscleGroup: String, category: String, onCreated: (Exercise) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.insertExercise(
                Exercise(
                    name = trimmed,
                    muscleGroup = muscleGroup.trim(),
                    category = category
                )
            )
            val created = Exercise(
                id = id,
                name = trimmed,
                muscleGroup = muscleGroup.trim(),
                category = category
            )
            onCreated(created)
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
        if (!isEntrySetValid()) return
        val rest = _restElapsed.value.toInt()
        restJob?.cancel()
        commitEntrySet(exId, rest)
        val exercise = _currentExercise.value ?: return
        _phase.value = WorkoutPhase.EXERCISE
        startSetTimer()
    }

    fun isEntrySetValid(): Boolean {
        val w = _entryWeight.value.replace(',', '.').toDoubleOrNull() ?: return false
        val r = _entryReps.value.toIntOrNull() ?: return false
        return w >= 0 && r > 0
    }

    fun nextSetNumber(exerciseId: Long?): Int =
        if (exerciseId == null) 1 else (_completedSets.value[exerciseId]?.size ?: 0) + 1

    fun lastWeightOf(exerciseId: Long): Double? =
        _setupWeights.value[exerciseId]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 }
            ?: _lastWeights.value[exerciseId]

    fun totalSetsCount(): Int {
        var count = _completedSets.value.values.sumOf { it.size }
        if (_phase.value == WorkoutPhase.REST && _entryExerciseId.value != null && isEntrySetValid()) count++
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

    private fun startSetTimer(
        initialElapsed: Long = 0L,
        initialPaused: Boolean = false,
        initialPauseElapsed: Long = 0L
    ) {
        setJob?.cancel()
        setRunningSeconds = initialElapsed
        _setElapsed.value = initialElapsed
        _setPaused.value = initialPaused
        _pauseElapsed.value = initialPauseElapsed
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

    private fun startRestTimer(initialElapsed: Long? = null) {
        restJob?.cancel()
        if (initialElapsed != null) _restElapsed.value = initialElapsed
        _restPaused.value = false
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

    fun saveAndExit() {
        viewModelScope.launch {
            stopTimers()
            repository.saveSession(workoutId, buildSessionJson(), isCircuit = false)
            _exited.value = true
        }
    }

    fun onResumeGapDecided(countGapAsRest: Boolean) {
        val gap = _resumeGapSeconds.value ?: return
        _resumeGapSeconds.value = null
        when (_phase.value) {
            WorkoutPhase.REST -> {
                if (countGapAsRest) _restElapsed.value += gap
                startRestTimer()
            }
            WorkoutPhase.EXERCISE -> {
                if (countGapAsRest) {
                    startSetTimer(initialPaused = true)
                    _pauseElapsed.value += gap
                } else {
                    startSetTimer(initialElapsed = _setElapsed.value)
                }
            }
            WorkoutPhase.SETUP -> Unit
        }
    }

    private fun buildSessionJson(): String {
        val root = JSONObject()
        root.put("phase", _phase.value.name)
        root.put("restLimitSeconds", restLimitSeconds)
        _currentExercise.value?.let { ex ->
            root.put("currentExerciseId", ex.id)
            root.put("currentExerciseName", ex.name)
            root.put("currentExerciseMuscle", ex.muscleGroup)
            root.put("currentExerciseCategory", ex.category)
        }
        root.put("setupWeights", encodeStringMap(_setupWeights.value))
        root.put("setupReps", encodeStringMap(_setupReps.value))
        root.put("setElapsed", _setElapsed.value)
        root.put("restElapsed", _restElapsed.value)
        root.put("pauseElapsed", _pauseElapsed.value)
        root.put("lastWeights", encodeDoubleMap(_lastWeights.value))
        root.put("setDurations", encodeIntListMap(_setDurations))
        root.put("durationIndex", encodeIntMap(_durationIndex))

        val sets = JSONObject()
        _completedSets.value.forEach { (exId, list) ->
            val arr = JSONArray()
            list.forEach { s ->
                val so = JSONObject()
                so.put("weight", s.weight)
                so.put("reps", s.reps)
                so.put("durationSeconds", s.durationSeconds)
                if (s.restSeconds != null) so.put("restSeconds", s.restSeconds)
                arr.put(so)
            }
            sets.put(exId.toString(), arr)
        }
        root.put("completedSets", sets)

        _entryExerciseId.value?.let { root.put("entryExerciseId", it) }
        root.put("entryWeight", _entryWeight.value)
        root.put("entryReps", _entryReps.value)
        return root.toString()
    }

    private fun restoreSession(json: String): Boolean {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return false
        }
        val savedRestLimit = root.optInt("restLimitSeconds")
        if (savedRestLimit > 0) restLimitSeconds = savedRestLimit
        _setupWeights.value = decodeStringMap(root.optJSONObject("setupWeights"))
        _setupReps.value = decodeStringMap(root.optJSONObject("setupReps"))
        _setElapsed.value = root.optLong("setElapsed", 0L)
        _restElapsed.value = root.optLong("restElapsed", 0L)
        _pauseElapsed.value = root.optLong("pauseElapsed", 0L)
        _lastWeights.value = decodeDoubleMap(root.optJSONObject("lastWeights"))
        _setDurations.clear()
        _setDurations.putAll(decodeIntListMap(root.optJSONObject("setDurations")))
        _durationIndex.clear()
        _durationIndex.putAll(decodeIntMap(root.optJSONObject("durationIndex")))

        val completed = mutableMapOf<Long, List<WorkoutRecordedSet>>()
        root.optJSONObject("completedSets")?.let { setsObj ->
            for (key in setsObj.keys()) {
                val exId = key.toLong()
                val arr = setsObj.optJSONArray(key) ?: continue
                val list = (0 until arr.length()).map { i ->
                    val so = arr.getJSONObject(i)
                    WorkoutRecordedSet(
                        weight = so.getDouble("weight"),
                        reps = so.getInt("reps"),
                        durationSeconds = so.optInt("durationSeconds", 0),
                        restSeconds = if (so.has("restSeconds")) so.getInt("restSeconds") else null
                    )
                }
                completed[exId] = list
            }
        }
        _completedSets.value = completed
        lastCommittedSet = completed.entries
            .filter { it.value.isNotEmpty() }
            .maxByOrNull { it.value.size }?.let { it.key to it.value.size - 1 }

        val phaseName = root.optString("phase", WorkoutPhase.SETUP.name)
        _phase.value = try {
            WorkoutPhase.valueOf(phaseName)
        } catch (_: Exception) {
            WorkoutPhase.SETUP
        }

        val currentId = root.optLong("currentExerciseId", -1L)
        if (currentId > 0) {
            _currentExercise.value = Exercise(
                id = currentId,
                name = root.optString("currentExerciseName", "Упражнение"),
                muscleGroup = root.optString("currentExerciseMuscle", ""),
                category = root.optString("currentExerciseCategory", "")
            )
        }

        val entryId = root.optLong("entryExerciseId", -1L)
        if (entryId > 0) {
            _entryExerciseId.value = entryId
        } else {
            _entryExerciseId.value = null
        }
        _entryWeight.value = root.optString("entryWeight", "")
        _entryReps.value = root.optString("entryReps", "")
        return true
    }
}