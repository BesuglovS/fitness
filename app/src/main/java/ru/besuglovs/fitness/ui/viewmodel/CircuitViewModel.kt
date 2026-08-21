package ru.besuglovs.fitness.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.ble.HeartRateSensor
import ru.besuglovs.fitness.ble.HeartRateStatus
import ru.besuglovs.fitness.ble.ScannedDevice
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.data.ExerciseWithSets
import ru.besuglovs.fitness.data.FitnessRepository
import ru.besuglovs.fitness.data.HeartRateSample
import ru.besuglovs.fitness.data.SetEntry
import ru.besuglovs.fitness.data.decodeDoubleMap
import ru.besuglovs.fitness.data.decodeIntListMap
import ru.besuglovs.fitness.data.decodeIntMap
import ru.besuglovs.fitness.data.decodeLongArray
import ru.besuglovs.fitness.data.decodeLongListMap
import ru.besuglovs.fitness.data.decodeStringMap
import ru.besuglovs.fitness.data.encodeDoubleMap
import ru.besuglovs.fitness.data.encodeIntListMap
import ru.besuglovs.fitness.data.encodeIntMap
import ru.besuglovs.fitness.data.encodeLongListMap
import ru.besuglovs.fitness.data.encodeStringMap
import ru.besuglovs.fitness.util.weightLabel

enum class CircuitPhase { SETUP, EXERCISE, REP_ENTRY }

data class RecordedSet(
    val weight: Double,
    val reps: Int,
    val durationSeconds: Int = 0,
    val restSeconds: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null
)

data class RoundHrExercise(
    val exerciseId: Long,
    val exerciseName: String,
    val samples: List<HeartRateSample>
)

data class RoundHrData(
    val circleNumber: Int,
    val circleSamples: List<HeartRateSample>,
    val exercises: List<RoundHrExercise>
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

    private val heartRateSensor = HeartRateSensor(getApplication())

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm.asStateFlow()

    private val _heartRateStatus = MutableStateFlow(HeartRateStatus.DISCONNECTED)
    val heartRateStatus: StateFlow<HeartRateStatus> = _heartRateStatus.asStateFlow()

    private val _heartRateDeviceName = MutableStateFlow<String?>(null)
    val heartRateDeviceName: StateFlow<String?> = _heartRateDeviceName.asStateFlow()

    private val _heartRateRecorded = MutableStateFlow(0)
    val heartRateRecorded: StateFlow<Int> = _heartRateRecorded.asStateFlow()

    private val _heartRateDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val heartRateDevices: StateFlow<List<ScannedDevice>> = _heartRateDevices.asStateFlow()

    private val heartRateSamples = mutableListOf<HeartRateSample>()
    private var heartRateSavedCount = 0
    private val hrLoaded = CompletableDeferred<Unit>()

    private val _currentRoundHr = MutableStateFlow<RoundHrData?>(null)
    val currentRoundHr: StateFlow<RoundHrData?> = _currentRoundHr.asStateFlow()

    private val _roundDurations = MutableStateFlow<List<Int>>(emptyList())
    val roundDurations: StateFlow<List<Int>> = _roundDurations.asStateFlow()

    private var setJob: Job? = null
    private var restJob: Job? = null
    private var setRunningSeconds = 0L

    private val _setDurations = MutableStateFlow<Map<Long, List<Int>>>(emptyMap())
    val setDurations: StateFlow<Map<Long, List<Int>>> = _setDurations.asStateFlow()
    private val _durationIndex = mutableMapOf<Long, Int>()
    private var _roundTimes = mutableListOf<Map<Long, Long>>()
    private var _currentRoundTimes = mutableMapOf<Long, Long>()
    private var _currentRoundStart = 0L
    private val _pendingSetRests = mutableMapOf<Long, Int>()
    private val _setPauseSeconds = mutableMapOf<Long, Int>()
    private val _setStartTimes = mutableMapOf<Long, MutableList<Long>>()
    private var pendingSetStart = 0L
    private var _lastCompletedExerciseId: Long? = null
    private var _lastSetEndTime = 0L

    init {
        viewModelScope.launch {
            repository.exercises().collect { _allExercises.value = it }
        }
        viewModelScope.launch {
            heartRateSensor.bpm.collect { _heartRateBpm.value = it }
        }
        viewModelScope.launch {
            heartRateSensor.status.collect { _heartRateStatus.value = it }
        }
        viewModelScope.launch {
            heartRateSensor.deviceName.collect { _heartRateDeviceName.value = it }
        }
        viewModelScope.launch {
            heartRateSensor.discoveredDevices.collect { _heartRateDevices.value = it }
        }
        viewModelScope.launch {
            heartRateSensor.readings.collect { bpm ->
                hrLoaded.await()
                heartRateSamples.add(
                    HeartRateSample(
                        workoutId = workoutId,
                        timestamp = System.currentTimeMillis(),
                        bpm = bpm
                    )
                )
                _heartRateRecorded.value = heartRateSamples.size
                refreshCurrentRoundHr()
            }
        }
        viewModelScope.launch {
            try {
                val existing = repository.heartRateSamplesOnce(workoutId)
                heartRateSamples.addAll(existing)
                heartRateSavedCount = heartRateSamples.size
                _heartRateRecorded.value = heartRateSamples.size
                refreshCurrentRoundHr()
            } finally {
                hrLoaded.complete(Unit)
            }
        }
        viewModelScope.launch {
            val workout = repository.getWorkoutOnce(workoutId)
            val sessionJson = workout?.sessionJson
            if (workout?.endTime == null && !sessionJson.isNullOrBlank()) {
                val wasRestore = restoreSession(sessionJson)
                refreshCurrentRoundHr()
                val gap = workout.pausedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                _resumeGapSeconds.value = gap.takeIf { it > 30 }
                if (wasRestore && _resumeGapSeconds.value == null) startResumedTimers()
            }
        }
    }

    fun connectHeartRate() {
        viewModelScope.launch { heartRateSensor.connect() }
    }

    fun disconnectHeartRate() {
        heartRateSensor.disconnect()
    }

    fun selectHeartRateDevice(device: ScannedDevice) {
        viewModelScope.launch { heartRateSensor.connect(device.address) }
    }

    fun scanHeartRateDevices() {
        heartRateSensor.disconnect()
        heartRateSensor.scanForDevices()
    }

    fun forgetHeartRateDevice() {
        heartRateSensor.forgetLastDevice()
    }

    override fun onCleared() {
        heartRateSensor.disconnect()
        super.onCleared()
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

    fun moveExercise(fromIndex: Int, toIndex: Int) {
        val list = _selectedExercises.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= list.size) return
        if (toIndex < 0 || toIndex >= list.size) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _selectedExercises.value = list
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
        _roundTimes.clear()
        _currentRoundTimes = mutableMapOf()
        _roundDurations.value = emptyList()
        _currentRoundStart = System.currentTimeMillis()
        _pendingSetRests.clear()
        _setPauseSeconds.clear()
        _setStartTimes.clear()
        pendingSetStart = 0L
        _lastCompletedExerciseId = null
        _lastSetEndTime = 0L
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
        val lastCompleted = _lastCompletedExerciseId
        if (lastCompleted != null && _lastSetEndTime > 0L) {
            val pause = ((System.currentTimeMillis() - _lastSetEndTime) / 1000).toInt().coerceAtLeast(0)
            _pendingSetRests[lastCompleted] = pause + (_setPauseSeconds.remove(lastCompleted) ?: 0)
        }
        _lastCompletedExerciseId = null
        _lastSetEndTime = 0L
        _activeExercise.value = exercise
        pendingSetStart = System.currentTimeMillis()
        startSetTimer()
    }

    fun completeSet() {
        val list = _selectedExercises.value
        if (list.isEmpty()) return
        val exerciseId = _activeExercise.value?.id ?: return
        stopTimers()
        _setDurations.value = _setDurations.value + (exerciseId to
            ((_setDurations.value[exerciseId] ?: emptyList()) + _setElapsed.value.toInt()))
        _setPauseSeconds[exerciseId] = _pauseElapsed.value.toInt()
        _setStartTimes.getOrPut(exerciseId) { mutableListOf() }.add(pendingSetStart)
        _currentRoundTimes[exerciseId] = System.currentTimeMillis()
        _roundCompletedIds.value = _roundCompletedIds.value + exerciseId
        _activeExercise.value = null
        _lastCompletedExerciseId = exerciseId
        if (_roundCompletedIds.value.size >= list.size) {
            _lastSetEndTime = 0L
            val lastCompletion = _currentRoundTimes.values.maxOrNull() ?: System.currentTimeMillis()
            val duration = ((lastCompletion - _currentRoundStart) / 1000).toInt().coerceAtLeast(0)
            _roundDurations.value = _roundDurations.value + duration
            _roundTimes.add(_currentRoundTimes)
            _currentRoundTimes = mutableMapOf()
            initEntryState()
            _phase.value = CircuitPhase.REP_ENTRY
            _restElapsed.value = 0L
            refreshCurrentRoundHr()
            startRestTimer()
        } else {
            _lastSetEndTime = System.currentTimeMillis()
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
        _circuitNumber.value += 1
        _roundCompletedIds.value = emptySet()
        _activeExercise.value = null
        _phase.value = CircuitPhase.EXERCISE
        _currentRoundStart = System.currentTimeMillis()
        stopTimers()
    }

    fun lastWeightOf(exerciseId: Long): Double? = _lastWeights.value[exerciseId]

    fun totalSetsCount(): Int {
        var count = _completedSets.value.values.sumOf { it.size }
        if (_phase.value == CircuitPhase.REP_ENTRY) {
            for (ex in _selectedExercises.value) {
                val w = _entryWeights.value[ex.id]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val r = _entryReps.value[ex.id]?.toIntOrNull() ?: 10
                if (w >= 0 && r > 0) count++
            }
        }
        return count
    }

    private fun initEntryState() {
        val weights = mutableMapOf<Long, String>()
        val reps = mutableMapOf<Long, String>()
        for (ex in _selectedExercises.value) {
            weights[ex.id] = _lastWeights.value[ex.id]?.let { weightLabel(it) } ?: "0"
            reps[ex.id] = _setupReps.value[ex.id] ?: "10"
        }
        _entryWeights.value = weights
        _entryReps.value = reps
    }

    private fun commitEntrySets() {
        val lastCompleted = _lastCompletedExerciseId
        if (lastCompleted != null && _phase.value == CircuitPhase.REP_ENTRY) {
            _pendingSetRests[lastCompleted] =
                _restElapsed.value.toInt() + (_setPauseSeconds.remove(lastCompleted) ?: 0)
        }
        val updated = _completedSets.value.toMutableMap()
        val weights = _lastWeights.value.toMutableMap()
        for (ex in _selectedExercises.value) {
            val w = _entryWeights.value[ex.id]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val r = _entryReps.value[ex.id]?.toIntOrNull() ?: 10
            if (w < 0 || r <= 0) continue
            val durationIdx = _durationIndex[ex.id] ?: 0
            val duration = _setDurations.value[ex.id]?.getOrNull(durationIdx) ?: 0
            _durationIndex[ex.id] = durationIdx + 1
            val rest = _pendingSetRests[ex.id] ?: 0
            val start = _setStartTimes[ex.id]?.getOrNull(durationIdx)
            val end = _roundTimes.getOrNull(durationIdx)?.get(ex.id) ?: _currentRoundTimes[ex.id]
            val (avg, max) = computeSetHrStats(start, end)
            updated[ex.id] = (updated[ex.id].orEmpty()) +
                RecordedSet(
                    w,
                    r,
                    durationSeconds = duration,
                    restSeconds = rest,
                    startTime = start,
                    endTime = end,
                    avgHeartRate = avg,
                    maxHeartRate = max
                )
            weights[ex.id] = w
        }
        _setPauseSeconds.clear()
        _pendingSetRests.clear()
        _lastCompletedExerciseId = null
        _lastSetEndTime = 0L
        _completedSets.value = updated
        _lastWeights.value = weights
    }

    private fun computeSetHrStats(start: Long?, end: Long?): Pair<Int?, Int?> {
        if (start == null || end == null || end <= start) return null to null
        val samples = heartRateSamples.filter { it.timestamp in start..end }
        if (samples.isEmpty()) return null to null
        return samples.map { it.bpm }.average().toInt() to samples.maxOf { it.bpm }
    }

    private fun refreshCurrentRoundHr() {
        if (_roundTimes.isEmpty()) {
            _currentRoundHr.value = null
            return
        }
        val roundTimes = _roundTimes.last()
        val circleEnd = roundTimes.values.maxOrNull() ?: return
        val circleStart = _currentRoundStart
        if (circleEnd <= circleStart) return
        val circleSamples = heartRateSamples.filter { it.timestamp in circleStart..circleEnd }
        val exercises = _selectedExercises.value.mapNotNull { ex ->
            val end = roundTimes[ex.id] ?: return@mapNotNull null
            val start = _setStartTimes[ex.id]?.lastOrNull() ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            RoundHrExercise(
                exerciseId = ex.id,
                exerciseName = ex.name,
                samples = heartRateSamples.filter { it.timestamp in start..end }
            )
        }
        _currentRoundHr.value = RoundHrData(
            circleNumber = _circuitNumber.value,
            circleSamples = circleSamples,
            exercises = exercises
        )
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
            var lastTick = System.currentTimeMillis()
            while (_phase.value == CircuitPhase.EXERCISE) {
                delay(500)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick) / 1000
                if (delta <= 0) continue
                lastTick += delta * 1000
                if (_setPaused.value) {
                    _pauseElapsed.value += delta
                } else {
                    setRunningSeconds += delta
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
            var lastTick = System.currentTimeMillis()
            while (_phase.value == CircuitPhase.REP_ENTRY) {
                delay(500)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick) / 1000
                if (delta <= 0) continue
                lastTick += delta * 1000
                if (!_restPaused.value) {
                    _restElapsed.value += delta
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
            // Записи круга коммитим только в фазе ввода результатов;
            // посреди круга подходы ещё не выполнены — иначе записались бы фантомные.
            if (_phase.value == CircuitPhase.REP_ENTRY) {
                commitEntrySets()
            }
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
                                doneAt = s.endTime
                                    ?: _roundTimes.getOrNull(i)?.get(ex.id)
                                    ?: _currentRoundTimes[ex.id]
                                    ?: System.currentTimeMillis(),
                                setStartTime = s.startTime,
                                avgHeartRate = s.avgHeartRate,
                                maxHeartRate = s.maxHeartRate
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
            val pending = heartRateSamples.drop(heartRateSavedCount)
            if (pending.isNotEmpty()) {
                repository.saveHeartRateSamples(pending)
                heartRateSavedCount = heartRateSamples.size
            }
            heartRateSensor.disconnect()
            _saved.value = true
        }
    }

    fun saveAndExit() {
        viewModelScope.launch {
            stopTimers()
            repository.saveSession(workoutId, buildSessionJson(), isCircuit = true)
            val pending = heartRateSamples.drop(heartRateSavedCount)
            if (pending.isNotEmpty()) {
                repository.saveHeartRateSamples(pending)
                heartRateSavedCount = heartRateSamples.size
            }
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
                } else if (_lastSetEndTime > 0L && !countGapAsRest) {
                    _lastSetEndTime += gap * 1000
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
        root.put("lastWeights", encodeDoubleMap(_lastWeights.value))
        root.put("setDurations", encodeIntListMap(_setDurations.value))
        root.put("durationIndex", encodeIntMap(_durationIndex))
        root.put("setStartTimes", encodeLongListMap(_setStartTimes))
        root.put("pendingSetStart", pendingSetStart)
        root.put("pendingSetRests", encodeIntMap(_pendingSetRests))
        root.put("setPauseSeconds", encodeIntMap(_setPauseSeconds))
        _lastCompletedExerciseId?.let { root.put("lastCompletedExerciseId", it) }
        root.put("lastSetEndTime", _lastSetEndTime)

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
                if (s.startTime != null) so.put("startTime", s.startTime)
                if (s.endTime != null) so.put("endTime", s.endTime)
                if (s.avgHeartRate != null) so.put("avgHeartRate", s.avgHeartRate)
                if (s.maxHeartRate != null) so.put("maxHeartRate", s.maxHeartRate)
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

        val roundDurations = JSONArray()
        _roundDurations.value.forEach { roundDurations.put(it) }
        root.put("roundDurations", roundDurations)
        root.put("currentRoundStart", _currentRoundStart)
        return root.toString()
    }

    private fun restoreSession(json: String): Boolean {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return false
        }
        return try {
            restoreSessionInner(root)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun restoreSessionInner(root: JSONObject) {
        val savedRestLimit = root.optInt("restLimitSeconds")
        if (savedRestLimit > 0) restLimitSeconds = savedRestLimit
        _setupWeights.value = decodeStringMap(root.optJSONObject("setupWeights"))
        _setupReps.value = decodeStringMap(root.optJSONObject("setupReps"))
        _entryWeights.value = decodeStringMap(root.optJSONObject("entryWeights"))
        _entryReps.value = decodeStringMap(root.optJSONObject("entryReps"))
        _setElapsed.value = root.optLong("setElapsed", 0L)
        _restElapsed.value = root.optLong("restElapsed", 0L)
        _pauseElapsed.value = root.optLong("pauseElapsed", 0L)
        _lastWeights.value = decodeDoubleMap(root.optJSONObject("lastWeights"))
        _setDurations.value = decodeIntListMap(root.optJSONObject("setDurations"))
        _durationIndex.clear()
        _durationIndex.putAll(decodeIntMap(root.optJSONObject("durationIndex")))
        _setStartTimes.clear()
        _setStartTimes.putAll(decodeLongListMap(root.optJSONObject("setStartTimes")))
        pendingSetStart = root.optLong("pendingSetStart", 0L)
        _pendingSetRests.clear()
        _pendingSetRests.putAll(decodeIntMap(root.optJSONObject("pendingSetRests")))
        _setPauseSeconds.clear()
        _setPauseSeconds.putAll(decodeIntMap(root.optJSONObject("setPauseSeconds")))
        _lastCompletedExerciseId = if (root.has("lastCompletedExerciseId")) {
            root.getLong("lastCompletedExerciseId")
        } else {
            null
        }
        _lastSetEndTime = root.optLong("lastSetEndTime", 0L)

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
                        restSeconds = if (so.has("restSeconds")) so.getInt("restSeconds") else null,
                        startTime = if (so.has("startTime")) so.getLong("startTime") else null,
                        endTime = if (so.has("endTime")) so.getLong("endTime") else null,
                        avgHeartRate = if (so.has("avgHeartRate")) so.getInt("avgHeartRate") else null,
                        maxHeartRate = if (so.has("maxHeartRate")) so.getInt("maxHeartRate") else null
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

        val roundDurations = mutableListOf<Int>()
        root.optJSONArray("roundDurations")?.let { arr ->
            for (i in 0 until arr.length()) roundDurations.add(arr.getInt(i))
        }
        _roundDurations.value = roundDurations
        _currentRoundStart = if (root.has("currentRoundStart")) {
            root.getLong("currentRoundStart")
        } else {
            System.currentTimeMillis()
        }

        val phaseName = root.optString("phase", CircuitPhase.SETUP.name)
        _phase.value = try {
            CircuitPhase.valueOf(phaseName)
        } catch (_: Exception) {
            CircuitPhase.SETUP
        }
    }
}
