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
    val restSeconds: Int? = null,
    val doneAt: Long? = null,
    val startTime: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null
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

    private val hrSampleList = mutableListOf<HeartRateSample>()
    private var heartRateSavedCount = 0
    private val hrLoaded = CompletableDeferred<Unit>()

    private val _heartRateSamples = MutableStateFlow<List<HeartRateSample>>(emptyList())
    val heartRateSamples: StateFlow<List<HeartRateSample>> = _heartRateSamples.asStateFlow()

    private fun publishHeartRate() {
        _heartRateSamples.value = hrSampleList.toList()
    }

    private val _pendingEntrySet = MutableStateFlow<WorkoutRecordedSet?>(null)
    val pendingEntrySet: StateFlow<WorkoutRecordedSet?> = _pendingEntrySet.asStateFlow()

    private fun buildPendingEntrySet(restSec: Int?): WorkoutRecordedSet? {
        val exId = _entryExerciseId.value ?: return null
        val duration = _setDurations[exId]?.lastOrNull() ?: 0
        return WorkoutRecordedSet(
            weight = entryWeightValue(),
            reps = entryRepsValue(),
            durationSeconds = duration,
            restSeconds = restSec,
            doneAt = _pendingDoneAt.takeIf { it > 0 },
            startTime = pendingSetStart.takeIf { it > 0 },
            avgHeartRate = pendingSetAvgHr,
            maxHeartRate = pendingSetMaxHr
        )
    }

    private var setJob: Job? = null
    private var restJob: Job? = null
    private var setRunningSeconds = 0L
    private var lastCommittedSet: Pair<Long, Int>? = null

    private val _setDurations = mutableMapOf<Long, MutableList<Int>>()
    private val _durationIndex = mutableMapOf<Long, Int>()
    private var _pendingDoneAt = 0L
    private var pendingSetStart = 0L
    private var pendingSetAvgHr: Int? = null
    private var pendingSetMaxHr: Int? = null

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
                hrSampleList.add(
                    HeartRateSample(
                        workoutId = workoutId,
                        timestamp = System.currentTimeMillis(),
                        bpm = bpm
                    )
                )
                _heartRateRecorded.value = hrSampleList.size
                publishHeartRate()
            }
        }
        viewModelScope.launch {
            try {
                val existing = repository.heartRateSamplesOnce(workoutId)
                hrSampleList.addAll(existing)
                heartRateSavedCount = hrSampleList.size
                _heartRateRecorded.value = hrSampleList.size
                publishHeartRate()
            } finally {
                hrLoaded.complete(Unit)
            }
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
        val weight = _setupWeights.value[exercise.id]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        if (weight < 0) return
        _lastWeights.value = _lastWeights.value + (exercise.id to weight)
        beginNewSet()
        _phase.value = WorkoutPhase.EXERCISE
        startSetTimer()
    }

    private fun beginNewSet() {
        _pendingDoneAt = 0L
        pendingSetStart = System.currentTimeMillis()
        pendingSetAvgHr = null
        pendingSetMaxHr = null
    }

    fun completeSet() {
        val exercise = _currentExercise.value ?: return
        stopTimers()
        val exId = exercise.id
        _setDurations.getOrPut(exId) { mutableListOf() }.add(_setElapsed.value.toInt())
        _pendingDoneAt = System.currentTimeMillis()
        val (avg, max) = computeSetHrStats(pendingSetStart, _pendingDoneAt)
        pendingSetAvgHr = avg
        pendingSetMaxHr = max
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
        _pendingEntrySet.value = buildPendingEntrySet(0)
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
        beginNewSet()
        _phase.value = WorkoutPhase.EXERCISE
        startSetTimer()
    }

    fun isEntrySetValid(): Boolean {
        val w = entryWeightValue()
        val r = entryRepsValue()
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
        val w = entryWeightValue()
        val r = entryRepsValue()
        if (w < 0 || r <= 0) return
        val durationIdx = _durationIndex[exId] ?: 0
        val duration = _setDurations[exId]?.getOrNull(durationIdx) ?: 0
        _durationIndex[exId] = durationIdx + 1
        val doneAt = _pendingDoneAt.takeIf { it > 0 }
        val startTime = pendingSetStart.takeIf { it > 0 }
        _pendingDoneAt = 0L
        val list = _completedSets.value[exId].orEmpty()
        _completedSets.value = _completedSets.value + (exId to (list +
            WorkoutRecordedSet(
                w,
                r,
                durationSeconds = duration,
                restSeconds = restSeconds,
                doneAt = doneAt,
                startTime = startTime,
                avgHeartRate = pendingSetAvgHr,
                maxHeartRate = pendingSetMaxHr
            )))
        lastCommittedSet = exId to list.size
        _lastWeights.value = _lastWeights.value + (exId to w)
    }

    private fun computeSetHrStats(start: Long, end: Long): Pair<Int?, Int?> {
        if (start <= 0 || end <= start) return null to null
        val samples = hrSampleList.filter { it.timestamp in start..end }
        if (samples.isEmpty()) return null to null
        return samples.map { it.bpm }.average().toInt() to samples.maxOf { it.bpm }
    }

    private fun entryWeightValue(): Double =
        _entryWeight.value.replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun entryRepsValue(): Int =
        _entryReps.value.toIntOrNull() ?: 10

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
            var lastTick = System.currentTimeMillis()
            while (_phase.value == WorkoutPhase.EXERCISE) {
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
        _pendingEntrySet.value = null
    }

    private fun startRestTimer(initialElapsed: Long? = null) {
        restJob?.cancel()
        if (initialElapsed != null) _restElapsed.value = initialElapsed
        _restPaused.value = false
        restJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (_phase.value == WorkoutPhase.REST) {
                delay(500)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick) / 1000
                if (delta <= 0) continue
                lastTick += delta * 1000
                if (!_restPaused.value) {
                    _restElapsed.value += delta
                    _pendingEntrySet.value = buildPendingEntrySet(_restElapsed.value.toInt())
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
            // Коммитим отложенный подход только если он реально завершён (фаза REST);
            // во время выполнения подхода вес/повторы ещё не введены — иначе был бы дубль.
            if (_phase.value == WorkoutPhase.REST) {
                _entryExerciseId.value?.let { commitEntrySet(it) }
            }
            _pendingEntrySet.value = null
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
                                durationSeconds = s.durationSeconds.takeIf { it > 0 },
                                doneAt = s.doneAt ?: System.currentTimeMillis(),
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
                exercises = exercisesWithSets
            )
            val pending = hrSampleList.drop(heartRateSavedCount)
            if (pending.isNotEmpty()) {
                repository.saveHeartRateSamples(pending)
                heartRateSavedCount = hrSampleList.size
            }
            heartRateSensor.disconnect()
            _saved.value = true
        }
    }

    fun saveAndExit() {
        viewModelScope.launch {
            stopTimers()
            _pendingEntrySet.value = null
            repository.saveSession(workoutId, buildSessionJson(), isCircuit = false)
            val pending = hrSampleList.drop(heartRateSavedCount)
            if (pending.isNotEmpty()) {
                repository.saveHeartRateSamples(pending)
                heartRateSavedCount = hrSampleList.size
            }
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
        root.put("pendingDoneAt", _pendingDoneAt)
        root.put("pendingSetStart", pendingSetStart)
        pendingSetAvgHr?.let { root.put("pendingSetAvgHr", it) }
        pendingSetMaxHr?.let { root.put("pendingSetMaxHr", it) }

        val sets = JSONObject()
        _completedSets.value.forEach { (exId, list) ->
            val arr = JSONArray()
            list.forEach { s ->
                val so = JSONObject()
                so.put("weight", s.weight)
                so.put("reps", s.reps)
                so.put("durationSeconds", s.durationSeconds)
                if (s.restSeconds != null) so.put("restSeconds", s.restSeconds)
                if (s.doneAt != null) so.put("doneAt", s.doneAt)
                if (s.startTime != null) so.put("startTime", s.startTime)
                if (s.avgHeartRate != null) so.put("avgHeartRate", s.avgHeartRate)
                if (s.maxHeartRate != null) so.put("maxHeartRate", s.maxHeartRate)
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
        _pendingDoneAt = root.optLong("pendingDoneAt", 0L)
        pendingSetStart = root.optLong("pendingSetStart", 0L)
        pendingSetAvgHr = if (root.has("pendingSetAvgHr")) root.getInt("pendingSetAvgHr") else null
        pendingSetMaxHr = if (root.has("pendingSetMaxHr")) root.getInt("pendingSetMaxHr") else null

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
                        restSeconds = if (so.has("restSeconds")) so.getInt("restSeconds") else null,
                        doneAt = if (so.has("doneAt")) so.getLong("doneAt") else null,
                        startTime = if (so.has("startTime")) so.getLong("startTime") else null,
                        avgHeartRate = if (so.has("avgHeartRate")) so.getInt("avgHeartRate") else null,
                        maxHeartRate = if (so.has("maxHeartRate")) so.getInt("maxHeartRate") else null
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