package ru.besuglovs.fitness.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.besuglovs.fitness.data.SetEntry
import ru.besuglovs.fitness.data.HeartRateSample
import ru.besuglovs.fitness.data.WorkoutWithDetails
import ru.besuglovs.fitness.data.WorkoutExerciseWithExercise
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.components.ChartZone
import ru.besuglovs.fitness.ui.components.LineChart
import ru.besuglovs.fitness.ui.viewmodel.WorkoutDetailViewModel
import ru.besuglovs.fitness.util.formatDateTime
import ru.besuglovs.fitness.util.formatDuration
import ru.besuglovs.fitness.util.formatTimer
import ru.besuglovs.fitness.util.weightLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(workoutId: Long, onBack: () -> Unit) {
    val vm: WorkoutDetailViewModel = viewModel(
        key = "detail_$workoutId",
        factory = AppViewModelProvider.Factory
    )
    val details by vm.details.collectAsStateWithLifecycle()
    val heartRateSamples by vm.heartRateSamples.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали тренировки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        val workout = details?.workout
        if (workout == null) {
            Text(
                "Тренировка не найдена",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            )
            return@Scaffold
        }

        val totalVolume = details!!.exercises.sumOf { we ->
            we.sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        }
        val crossRestKeys = remember(details!!.exercises) {
            computeCrossExerciseRests(details!!.exercises)
        }

        if (workout.isCircuit) {
            CircuitDetailList(
                details = details!!,
                totalVolume = totalVolume,
                heartRateSamples = heartRateSamples,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                WorkoutDetailHeader(workout, totalVolume)
            }

            item {
                HeartRateDetailCard(heartRateSamples)
            }

            details!!.exercises.forEach { we ->
                item {
                    ExerciseDetailCard(we, crossRestKeys, heartRateSamples)
                }
                val crossRest = we.sets.firstOrNull {
                    SetRestKey(we.workoutExercise.id, it.setNumber) in crossRestKeys
                }
                if (crossRest != null) {
                    item {
                        CrossExerciseRestBlock(restSeconds = crossRest.restSeconds)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetailHeader(
    workout: ru.besuglovs.fitness.data.Workout,
    totalVolume: Double
) {
    Column {
        Text(
            formatDateTime(workout.startTime),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Длительность ${formatDuration(workout.startTime, workout.endTime)} · " +
                "Общий объём ${weightLabel(totalVolume)} кг",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeartRateDetailCard(samples: List<HeartRateSample>) {
    if (samples.isEmpty()) return
    val (values, labels) = remember(samples) { downsampleHeartRate(samples) }
    val min = samples.minOf { it.bpm }
    val max = samples.maxOf { it.bpm }
    val avg = samples.map { it.bpm }.average().toInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Частота сердечных сокращений",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Средняя $avg · Мин $min · Макс $max уд/мин",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LineChart(values = values, xLabels = labels)
        }
    }
}

private fun downsampleHeartRate(
    samples: List<HeartRateSample>,
    maxPoints: Int = 30
): Pair<List<Float>, List<String>> {
    if (samples.isEmpty()) return emptyList<Float>() to emptyList()
    val first = samples.first().timestamp
    val step = kotlin.math.ceil(samples.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val indices = samples.indices step step
    val values = indices.map { samples[it].bpm.toFloat() }
    val labels = indices.map {
        val m = (samples[it].timestamp - first) / 60000
        "${m}м"
    }
    return values to labels
}

internal fun samplesForSet(
    samples: List<HeartRateSample>,
    set: SetEntry
): List<HeartRateSample> {
    val start = set.setStartTime ?: return emptyList()
    val end = set.doneAt
    if (end < start) return emptyList()
    return samples.filter { it.timestamp in start..end }
}

internal data class HrPhase(
    val samples: List<HeartRateSample>,
    val isRest: Boolean,
    val weightKg: Double? = null,
    val reps: Int? = null
)

internal fun restSamplesForSet(
    samples: List<HeartRateSample>,
    set: SetEntry,
    nextSetStart: Long? = null
): List<HeartRateSample> {
    val restSec = set.restSeconds ?: return emptyList()
    val start = set.doneAt
    if (start <= 0L) return emptyList()
    var end = start + restSec * 1000L
    if (nextSetStart != null && nextSetStart > start) end = minOf(end, nextSetStart)
    if (end <= start) return emptyList()
    return samples.filter { it.timestamp in start..end }
}

internal fun buildSetPhases(
    samples: List<HeartRateSample>,
    set: SetEntry,
    nextSetStart: Long? = null
): List<HrPhase> {
    val phases = mutableListOf(
        HrPhase(
            samples = samplesForSet(samples, set),
            isRest = false,
            weightKg = set.weightKg,
            reps = set.reps
        )
    )
    val rest = restSamplesForSet(samples, set, nextSetStart)
    if (set.restSeconds != null || rest.isNotEmpty()) {
        phases.add(HrPhase(rest, isRest = true))
    }
    return phases
}

internal fun buildExercisePhases(
    samples: List<HeartRateSample>,
    sets: List<SetEntry>
): List<HrPhase> {
    val ordered = sets.sortedBy { it.setNumber }
    val phases = mutableListOf<HrPhase>()
    ordered.forEachIndexed { i, set ->
        val nextStart = ordered.getOrNull(i + 1)?.setStartTime
        phases.addAll(buildSetPhases(samples, set, nextStart))
    }
    return phases
}

private fun buildCirclePhases(
    samples: List<HeartRateSample>,
    circleSets: List<SetEntry>
): List<HrPhase> {
    val ordered = circleSets.sortedBy { it.doneAt }
    val phases = mutableListOf<HrPhase>()
    ordered.forEachIndexed { i, set ->
        val nextStart = ordered.getOrNull(i + 1)?.setStartTime
        phases.addAll(buildSetPhases(samples, set, nextStart))
    }
    return phases
}

internal data class HrChartData(
    val values: List<Float>,
    val labels: List<String>,
    val zones: List<ChartZone>
)

internal fun downsampleWithBase(
    samples: List<HeartRateSample>,
    base: Long,
    maxPoints: Int = 8
): Pair<List<Float>, List<String>> {
    if (samples.isEmpty()) return emptyList<Float>() to emptyList()
    val step = kotlin.math.ceil(samples.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val indices = samples.indices step step
    val values = indices.map { samples[it].bpm.toFloat() }
    val labels = indices.map {
        val sec = (samples[it].timestamp - base) / 1000
        val m = sec / 60
        val s = sec % 60
        if (m > 0) "${m}м${s.toString().padStart(2, '0')}" else "${s}с"
    }
    return values to labels
}

internal fun buildChartData(
    phases: List<HrPhase>,
    setColor: androidx.compose.ui.graphics.Color,
    restColor: androidx.compose.ui.graphics.Color
): HrChartData {
    val base = phases.firstNotNullOfOrNull { it.samples.firstOrNull()?.timestamp }
        ?: return HrChartData(emptyList(), emptyList(), emptyList())
    var offset = 0
    val values = mutableListOf<Float>()
    val labels = mutableListOf<String>()
    val zones = mutableListOf<ChartZone>()
    phases.forEach { phase ->
        val (vals, lbls) = downsampleWithBase(phase.samples, base)
        if (vals.isEmpty()) return@forEach
        val phaseMin = phase.samples.minOf { it.bpm }
        val phaseMax = phase.samples.maxOf { it.bpm }
        val phaseAvg = phase.samples.map { it.bpm }.average().toInt()
        values.addAll(vals)
        labels.addAll(lbls)
        zones.add(
            ChartZone(
                startIndex = offset,
                endIndex = offset + vals.size,
                color = if (phase.isRest) restColor else setColor,
                label = if (phase.isRest) "Отдых" else "Подход",
                topLabel = "$phaseMin/$phaseAvg/$phaseMax",
                bottomLabel = if (!phase.isRest) {
                    "${weightLabel(phase.weightKg)}/${phase.reps ?: "-"}"
                } else {
                    null
                }
            )
        )
        offset += vals.size
    }
    return HrChartData(values, labels, zones)
}

private fun heartRateLabel(set: SetEntry): String =
    if (set.avgHeartRate != null || set.maxHeartRate != null) {
        "${set.avgHeartRate ?: "-"}/${set.maxHeartRate ?: "-"}"
    } else {
        "-"
    }

@Composable
internal fun HeartRateSection(
    label: String,
    phases: List<HrPhase>,
    modifier: Modifier = Modifier
) {
    val allSamples = phases.flatMap { it.samples }
    if (allSamples.isEmpty()) {
        Text(
            "Нет данных о пульсе",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val setColor = MaterialTheme.colorScheme.primary
    val restColor = MaterialTheme.colorScheme.tertiary
    val chartData = remember(phases, setColor, restColor) { buildChartData(phases, setColor, restColor) }
    val min = allSamples.minOf { it.bpm }
    val max = allSamples.maxOf { it.bpm }
    val avg = allSamples.map { it.bpm }.average().toInt()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "$label · ср $avg · мин $min · макс $max уд/мин",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        LineChart(
            values = chartData.values,
            xLabels = chartData.labels,
            zones = chartData.zones
        )
    }
}

private enum class CircuitViewMode { BY_CIRCLE, BY_EXERCISE }

@Composable
private fun CircuitDetailList(
    details: WorkoutWithDetails,
    totalVolume: Double,
    heartRateSamples: List<HeartRateSample>,
    modifier: Modifier = Modifier
) {
    val exercises = details.exercises.sortedBy { it.workoutExercise.orderIndex }
    val maxCircles = exercises.maxOfOrNull { it.sets.size } ?: 0
    var mode by rememberSaveable { mutableStateOf(CircuitViewMode.BY_CIRCLE) }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WorkoutDetailHeader(details.workout, totalVolume)
        }
        item {
            HeartRateDetailCard(heartRateSamples)
        }
        item {
            Text(
                "Круговая тренировка · $maxCircles ${pluralCircles(maxCircles)} · " +
                    "${exercises.size} ${pluralExercises(exercises.size)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            CircuitViewModeToggle(
                mode = mode,
                onModeChange = { mode = it }
            )
        }
        if (mode == CircuitViewMode.BY_CIRCLE) {
            for (circle in 1..maxCircles) {
                item {
                    CircuitCircleCard(circleNumber = circle, exercises = exercises, heartRateSamples = heartRateSamples)
                }
                // Отдых после последнего круга не показываем — «между кругами» его нет.
                val restSeconds = if (circle < maxCircles) interRoundRestSeconds(exercises, circle) else null
                if (restSeconds != null) {
                    item {
                        CircuitRoundRestBlock(restSeconds = restSeconds)
                    }
                }
            }
        } else {
            exercises.forEach { we ->
                item {
                    CircuitExerciseCard(we = we, heartRateSamples = heartRateSamples)
                }
            }
        }
    }
}

@Composable
private fun CircuitViewModeToggle(
    mode: CircuitViewMode,
    onModeChange: (CircuitViewMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == CircuitViewMode.BY_CIRCLE,
            onClick = { onModeChange(CircuitViewMode.BY_CIRCLE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text("По кругам")
        }
        SegmentedButton(
            selected = mode == CircuitViewMode.BY_EXERCISE,
            onClick = { onModeChange(CircuitViewMode.BY_EXERCISE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text("По упражнениям")
        }
    }
}

@Composable
private fun CircuitExerciseCard(
    we: WorkoutExerciseWithExercise,
    heartRateSamples: List<HeartRateSample>
) {
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                we.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (we.muscleGroup.isNotBlank()) {
                Text(
                    we.muscleGroup,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Круг",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Вес",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Повторения",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Пульс ср/макс",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Время",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Отдых",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            we.sets.sortedBy { it.setNumber }.forEach { set ->
                val key = "cset_${we.workoutExercise.id}_${set.setNumber}"
                CircuitExerciseCardRow(
                    circleNumber = set.setNumber,
                    set = set,
                    heartRateSamples = heartRateSamples,
                    expanded = expandedKey == key,
                    onToggle = { expandedKey = if (expandedKey == key) null else key }
                )
            }
        }
    }
}

@Composable
private fun CircuitExerciseCardRow(
    circleNumber: Int,
    set: SetEntry,
    heartRateSamples: List<HeartRateSample>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                circleNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                weightLabel(set.weightKg),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                set.reps?.toString() ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                heartRateLabel(set),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                set.durationSeconds?.let { formatTimer(it.toLong()) } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                set.restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            HeartRateSection(
                label = "Пульс подхода $circleNumber",
                phases = buildSetPhases(heartRateSamples, set)
            )
        }
    }
}

@Composable
private fun CircuitCircleCard(
    circleNumber: Int,
    exercises: List<WorkoutExerciseWithExercise>,
    heartRateSamples: List<HeartRateSample>
) {
    var circleExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val circleSets = remember(exercises, circleNumber) {
        exercises.mapNotNull { we -> we.sets.firstOrNull { it.setNumber == circleNumber } }
    }
    val circleDuration = remember(circleSets) { circleDurationSeconds(circleSets) }
    val lastDoneAt = circleSets.maxOfOrNull { it.doneAt }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { circleExpanded = !circleExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Круг $circleNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    circleDuration?.let { formatTimer(it.toLong()) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (circleExpanded) {
                Spacer(Modifier.height(8.dp))
                HeartRateSection(
                    label = "Пульс круга $circleNumber",
                    phases = buildCirclePhases(
                        heartRateSamples,
                        exercises.mapNotNull { we ->
                            we.sets.firstOrNull { it.setNumber == circleNumber }
                        }
                    )
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Название",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Вес",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Повторения",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Пульс ср/макс",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Время",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Отдых",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            exercises
                .mapNotNull { we ->
                    val set = we.sets.firstOrNull { it.setNumber == circleNumber }
                    if (set != null) we to set else null
                }
                .sortedBy { (_, set) -> set.doneAt }
                .forEach { (we, set) ->
                    val key = "rset_${we.workoutExercise.id}_${set.setNumber}"
                    CircuitExerciseRow(
                        we = we,
                        set = set,
                        heartRateSamples = heartRateSamples,
                        isLastInCircle = set.doneAt == lastDoneAt,
                        expanded = expandedKey == key,
                        onToggle = { expandedKey = if (expandedKey == key) null else key }
                    )
                }
        }
    }
}

@Composable
private fun CircuitExerciseRow(
    we: WorkoutExerciseWithExercise,
    set: SetEntry,
    heartRateSamples: List<HeartRateSample>,
    isLastInCircle: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                we.exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                weightLabel(set.weightKg),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                set.reps?.toString() ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                heartRateLabel(set),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                set.durationSeconds?.let { formatTimer(it.toLong()) } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Text(
                if (isLastInCircle) "-"
                else (set.restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            HeartRateSection(
                label = "Пульс подхода ${set.setNumber}",
                phases = buildSetPhases(heartRateSamples, set)
            )
        }
    }
}

private fun circleSets(
    exercises: List<WorkoutExerciseWithExercise>,
    circleNumber: Int
): List<SetEntry> =
    exercises.mapNotNull { we -> we.sets.firstOrNull { it.setNumber == circleNumber } }

private fun circleDurationSeconds(sets: List<SetEntry>): Int? {
    if (sets.isEmpty()) return null
    val end = sets.maxOfOrNull { it.doneAt } ?: return null
    val start = sets.mapNotNull { it.setStartTime }.minOrNull()
    if (start == null || end <= start) return null
    return ((end - start) / 1000).toInt().coerceAtLeast(0)
}

private fun interRoundRestSeconds(
    exercises: List<WorkoutExerciseWithExercise>,
    circleNumber: Int
): Int? {
    val sets = circleSets(exercises, circleNumber)
    if (sets.isEmpty()) return null
    return sets.maxByOrNull { it.doneAt }?.restSeconds
}

@Composable
private fun CircuitRoundRestBlock(restSeconds: Int?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Отдых между кругами",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun pluralCircles(count: Int): String = pluralize(
    count,
    one = "круг",
    few = "круга",
    many = "кругов"
)

private fun pluralExercises(count: Int): String = pluralize(
    count,
    one = "упражнение",
    few = "упражнения",
    many = "упражнений"
)

private fun pluralize(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    if (mod100 in 11..14) return many
    return when (count % 10) {
        1 -> one
        in 2..4 -> few
        else -> many
    }
}

@Composable
private fun CrossExerciseRestBlock(restSeconds: Int?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Отдых между упражнениями",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class SetRestKey(
    val workoutExerciseId: Long,
    val setNumber: Int
)

private fun computeCrossExerciseRests(
    exercises: List<WorkoutExerciseWithExercise>
): Set<SetRestKey> {
    val pairs = exercises.flatMap { we ->
        we.sets.map { set -> we to set }
    }
    if (pairs.isEmpty()) return emptySet()

    // Новые тренировки записывают doneAt в момент завершения подхода — времена
    // достоверно различаются. Старые сохраняли одно время на все подходы.
    val hasReliableTimes = (pairs.maxOfOrNull { it.second.doneAt } ?: 0L) -
        (pairs.minOfOrNull { it.second.doneAt } ?: 0L) >= 1000

    val ordered = if (hasReliableTimes) {
        pairs.sortedBy { it.second.doneAt }
    } else {
        pairs.sortedWith(
            compareBy<Pair<WorkoutExerciseWithExercise, SetEntry>> { it.first.workoutExercise.orderIndex }
                .thenBy { it.second.setNumber }
        )
    }

    val result = mutableSetOf<SetRestKey>()
    for (i in 0 until ordered.size - 1) {
        val current = ordered[i]
        val next = ordered[i + 1]
        if (current.first.workoutExercise.id != next.first.workoutExercise.id) {
            result.add(SetRestKey(current.first.workoutExercise.id, current.second.setNumber))
        }
    }
    return result
}

@Composable
private fun ExerciseDetailCard(
    we: ru.besuglovs.fitness.data.WorkoutExerciseWithExercise,
    crossRestKeys: Set<SetRestKey>,
    heartRateSamples: List<HeartRateSample>
) {
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseExpanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exerciseExpanded = !exerciseExpanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        we.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (exerciseExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (we.muscleGroup.isNotBlank()) {
                    Text(
                        we.muscleGroup,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (exerciseExpanded) {
                HeartRateSection(
                    label = "Пульс упражнения целиком",
                    phases = buildExercisePhases(heartRateSamples, we.sets)
                )
                Spacer(Modifier.height(8.dp))
            }

            // header
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("№", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp))
                Text("Вес", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Повт", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Пульс ср/макс", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Время", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Отдых", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            we.sets.sortedBy { it.setNumber }.forEach { set ->
                val isCrossRest = SetRestKey(we.workoutExercise.id, set.setNumber) in crossRestKeys
                val key = "set_${we.workoutExercise.id}_${set.setNumber}"
                SetDetailRow(
                    set = set,
                    isCrossRest = isCrossRest,
                    heartRateSamples = heartRateSamples,
                    expanded = expandedKey == key,
                    onToggle = { expandedKey = if (expandedKey == key) null else key }
                )
            }
        }
    }
}

@Composable
private fun SetDetailRow(
    set: SetEntry,
    isCrossRest: Boolean,
    heartRateSamples: List<HeartRateSample>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(set.setNumber.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp))
            Text(weightLabel(set.weightKg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(set.reps?.toString() ?: "-", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                heartRateLabel(set),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                set.durationSeconds?.let { formatTimer(it.toLong()) } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (isCrossRest) "-" else (set.restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            HeartRateSection(
                label = "Пульс подхода ${set.setNumber}",
                phases = buildSetPhases(heartRateSamples, set)
            )
        }
    }
}
