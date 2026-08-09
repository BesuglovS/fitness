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
import ru.besuglovs.fitness.data.decodeLongArray
import ru.besuglovs.fitness.data.decodeStringMap
import ru.besuglovs.fitness.data.encodeDoubleMap
import ru.besuglovs.fitness.data.encodeIntListMap
import ru.besuglovs.fitness.data.encodeIntMap
import ru.besuglovs.fitness.data.encodeStringMap
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
    restLimitSeconds: Int
) : AndroidViewModel(app) {

    private val fitnessApp = getApplication<FitnessApp>()
    private val repository: FitnessRepository = fitnessApp.repository

    var restLimitSeconds = restLimitSeconds
        private set

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

    private val _roundCompletedIds = MutableStateFlow<Set<Long>>(emptySet())
    val roundCompletedIds: StateFlow<Set<Long>> = _roundCompletedIds.asStateFlow()

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

    private val _exited = MutableStateFlow(false)
    val exited: StateFlow<Boolean> = _exited.asStateFlow()

    private val _resumeGapSeconds = MutableStateFlow<Long?>(null)
    val resumeGapSeconds: StateFlow<Long?> = _resumeGapSeconds.asStateFlow()

    private var setJob: Job? = null
    private var restJob: Job? = null
    private var setRunningSeconds = 0L
    private var _accumulatedPause = 0L

    private val _setDurations = mutableMapOf<Long, MutableList<Int>>()
    private val _durationIndex = mutableMapOf<Long, Int>()
    private var _pendingRestSeconds: Int? = null
    private var _roundTimes = mutableListOf<Map<Long, Long>>()
    private var _currentRoundTimes = mutableMapOf<Long, Long>()

    init {
        viewModelScope.launch {
            repository.exercises().collect { _allExercises.value = it }
        }
        viewModelScope.launch {
            val workout = repository.getWorkoutOnce(workoutId)
            val sessionJson = workout?.sessionJson
            if (workout?.endTime == null && !sessionJson.isNullOrBlank()) {
                restoreSession(sessionJson)
                val gap = workout.pausedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                _resumeGapSeconds.value = gap.takeIf { it > 30 }
                if (_resumeGapSeconds.value == null) startResumedTimers()
            }
        }
    }

    private fun startResumedTimers() {
        when (_phase.value) {
            CircuitPhase.REP_ENTRY -> startRestTimer()
            CircuitPhase.EXERCISE -> {
                if (_activeExercise.value != null) startSetTimer(initialElapsed = _setElapsed.value)
            }
            CircuitPhase.SETUP -> Unit
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
        _roundCompletedIds.value = emptySet()
        _activeExercise.value = null
        _phase.value = CircuitPhase.EXERCISE
        _accumulatedPause = 0L
        _roundTimes.clear()
        _currentRoundTimes = mutableMapOf()
        stopTimers()
    }

    fun selectExercise(exerciseId: Long) {
        if (_phase.value != CircuitPhase.EXERCISE) return
        if (_roundCompletedIds.value.contains(exerciseId)) return
        val exercise = _selectedExercises.value.firstOrNull { it.id == exerciseId } ?: return
        setActiveExercise(exercise)
    }

    fun selectNextExercise() {
        if (_phase.value != CircuitPhase.EXERCISE) return
        val next = _selectedExercises.value.firstOrNull {
            !_roundCompletedIds.value.contains(it.id)
        } ?: return
        setActiveExercise(next)
    }

    private fun setActiveExercise(exercise: Exercise) {
        _activeExercise.value = exercise
        startSetTimer()
    }

    fun completeSet() {
        val list = _selectedExercises.value
        if (list.isEmpty()) return
        val exerciseId = _activeExercise.value?.id ?: return
        stopTimers()
        _setDurations.getOrPut(exerciseId) { mutableListOf() }.add(_setElapsed.value.toInt())
        _accumulatedPause += _pauseElapsed.value
        _currentRoundTimes[exerciseId] = System.currentTimeMillis()
        _roundCompletedIds.value = _roundCompletedIds.value + exerciseId
        _activeExercise.value = null
        if (_roundCompletedIds.value.size >= list.size) {
            _roundTimes.add(_currentRoundTimes)
            _currentRoundTimes = mutableMapOf()
            initEntryState()
            _phase.value = CircuitPhase.REP_ENTRY
            _restElapsed.value = 0L
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
        _roundCompletedIds.value = emptySet()
        _activeExercise.value = null
        _phase.value = CircuitPhase.EXERCISE
        stopTimers()
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

    private fun startRestTimer(initialElapsed: Long? = null) {
        restJob?.cancel()
        if (initialElapsed != null) _restElapsed.value = initialElapsed
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
                                durationSeconds = s.durationSeconds.takeIf { it > 0 },
                                doneAt = _roundTimes.getOrNull(i)?.get(ex.id)
                                    ?: System.currentTimeMillis()
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

    fun saveAndExit() {
        viewModelScope.launch {
            stopTimers()
            repository.saveSession(workoutId, buildSessionJson(), isCircuit = true)
            _exited.value = true
        }
    }

    fun onResumeGapDecided(countGapAsRest: Boolean) {
        val gap = _resumeGapSeconds.value ?: return
        _resumeGapSeconds.value = null
        when (_phase.value) {
            CircuitPhase.REP_ENTRY -> {
                if (countGapAsRest) _restElapsed.value += gap
                startRestTimer()
            }
            CircuitPhase.EXERCISE -> {
                val activeId = _activeExercise.value?.id
                if (activeId != null) {
                    if (countGapAsRest) {
                        startSetTimer(initialPaused = true)
                        _pauseElapsed.value += gap
                    } else {
                        startSetTimer(initialElapsed = _setElapsed.value)
                    }
                }
            }
            CircuitPhase.SETUP -> Unit
        }
    }

    private fun buildSessionJson(): String {
        val root = JSONObject()
        root.put("phase", _phase.value.name)
        root.put("restLimitSeconds", restLimitSeconds)
        root.put("circuitNumber", _circuitNumber.value)
        root.put("setupWeights", encodeStringMap(_setupWeights.value))
        root.put("setupReps", encodeStringMap(_setupReps.value))
        root.put("entryWeights", encodeStringMap(_entryWeights.value))
        root.put("entryReps", encodeStringMap(_entryReps.value))
        root.put("setElapsed", _setElapsed.value)
        root.put("restElapsed", _restElapsed.value)
        root.put("pauseElapsed", _pauseElapsed.value)
        root.put("accumulatedPause", _accumulatedPause)
        root.put("lastWeights", encodeDoubleMap(_lastWeights.value))
        root.put("setDurations", encodeIntListMap(_setDurations))
        root.put("durationIndex", encodeIntMap(_durationIndex))
        _pendingRestSeconds?.let { root.put("pendingRestSeconds", it) }

        val selected = JSONArray()
        _selectedExercises.value.forEach { ex ->
            val eo = JSONObject()
            eo.put("id", ex.id)
            eo.put("name", ex.name)
            eo.put("muscleGroup", ex.muscleGroup)
            eo.put("category", ex.category)
            selected.put(eo)
        }
        root.put("selectedExercises", selected)

        val completedIds = JSONArray()
        _roundCompletedIds.value.forEach { completedIds.put(it) }
        root.put("roundCompletedIds", completedIds)

        _activeExercise.value?.let { root.put("activeExerciseId", it.id) }

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

        val roundTimes = JSONArray()
        _roundTimes.forEach { round ->
            val ro = JSONObject()
            round.forEach { (exId, ts) -> ro.put(exId.toString(), ts) }
            roundTimes.put(ro)
        }
        root.put("roundTimes", roundTimes)
        return root.toString()
    }

    private fun restoreSession(json: String) {
        val root = JSONObject(json)
        val savedRestLimit = root.optInt("restLimitSeconds")
        if (savedRestLimit > 0) restLimitSeconds = savedRestLimit
        _setupWeights.value = decodeStringMap(root.optJSONObject("setupWeights"))
        _setupReps.value = decodeStringMap(root.optJSONObject("setupReps"))
        _entryWeights.value = decodeStringMap(root.optJSONObject("entryWeights"))
        _entryReps.value = decodeStringMap(root.optJSONObject("entryReps"))
        _setElapsed.value = root.optLong("setElapsed", 0L)
        _restElapsed.value = root.optLong("restElapsed", 0L)
        _pauseElapsed.value = root.optLong("pauseElapsed", 0L)
        _accumulatedPause = root.optLong("accumulatedPause", 0L)
        _lastWeights.value = decodeDoubleMap(root.optJSONObject("lastWeights"))
        _setDurations.clear()
        _setDurations.putAll(decodeIntListMap(root.optJSONObject("setDurations")))
        _durationIndex.clear()
        _durationIndex.putAll(decodeIntMap(root.optJSONObject("durationIndex")))
        _pendingRestSeconds = if (root.has("pendingRestSeconds")) root.getInt("pendingRestSeconds") else null

        val selected = mutableListOf<Exercise>()
        root.optJSONArray("selectedExercises")?.let { arr ->
            for (i in 0 until arr.length()) {
                val eo = arr.getJSONObject(i)
                selected.add(
                    Exercise(
                        id = eo.getLong("id"),
                        name = eo.optString("name", "Упражнение"),
                        muscleGroup = eo.optString("muscleGroup", ""),
                        category = eo.optString("category", "")
                    )
                )
            }
        }
        _selectedExercises.value = selected

        _roundCompletedIds.value = decodeLongArray(root.optJSONArray("roundCompletedIds")).toSet()
        val activeId = root.optLong("activeExerciseId", -1L)
        _activeExercise.value = if (activeId > 0) selected.firstOrNull { it.id == activeId } else null

        val completed = mutableMapOf<Long, List<RecordedSet>>()
        root.optJSONObject("completedSets")?.let { setsObj ->
            for (key in setsObj.keys()) {
                val exId = key.toLong()
                val arr = setsObj.optJSONArray(key) ?: continue
                val list = (0 until arr.length()).map { i ->
                    val so = arr.getJSONObject(i)
                    RecordedSet(
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

        val roundTimes = mutableListOf<Map<Long, Long>>()
        root.optJSONArray("roundTimes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val ro = arr.getJSONObject(i)
                val m = mutableMapOf<Long, Long>()
                for (key in ro.keys()) m[key.toLong()] = ro.getLong(key)
                roundTimes.add(m)
            }
        }
        _roundTimes.clear()
        _roundTimes.addAll(roundTimes)

        val phaseName = root.optString("phase", CircuitPhase.SETUP.name)
        _phase.value = try {
            CircuitPhase.valueOf(phaseName)
        } catch (_: Exception) {
            CircuitPhase.SETUP
        }
    }
}
