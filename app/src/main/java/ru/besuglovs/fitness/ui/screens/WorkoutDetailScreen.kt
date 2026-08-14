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

private fun samplesForSet(
    samples: List<HeartRateSample>,
    set: SetEntry
): List<HeartRateSample> {
    val start = set.setStartTime ?: return emptyList()
    val end = set.doneAt
    if (end < start) return emptyList()
    return samples.filter { it.timestamp in start..end }
}

private fun samplesForCircle(
    samples: List<HeartRateSample>,
    circleSets: List<SetEntry>
): List<HeartRateSample> {
    val starts = circleSets.mapNotNull { it.setStartTime }
    if (starts.isEmpty()) return emptyList()
    val start = starts.minOrNull() ?: return emptyList()
    val end = circleSets.maxOf { it.doneAt }
    if (end < start) return emptyList()
    return samples.filter { it.timestamp in start..end }
}

private fun heartRateLabel(set: SetEntry): String =
    if (set.avgHeartRate != null || set.maxHeartRate != null) {
        "${set.avgHeartRate ?: "-"}/${set.maxHeartRate ?: "-"}"
    } else {
        "-"
    }

@Composable
private fun HeartRateSection(
    label: String,
    samples: List<HeartRateSample>,
    modifier: Modifier = Modifier
) {
    if (samples.isEmpty()) {
        Text(
            "Нет данных о пульсе",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val (values, labels) = remember(samples) { downsampleHeartRate(samples) }
    val min = samples.minOf { it.bpm }
    val max = samples.maxOf { it.bpm }
    val avg = samples.map { it.bpm }.average().toInt()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "$label · ср $avg · мин $min · макс $max уд/мин",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        LineChart(values = values, xLabels = labels)
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
                samples = samplesForSet(heartRateSamples, set)
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
            }
            if (circleExpanded) {
                Spacer(Modifier.height(8.dp))
                HeartRateSection(
                    label = "Пульс круга $circleNumber",
                    samples = samplesForCircle(
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
                set.restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            HeartRateSection(
                label = "Пульс подхода ${set.setNumber}",
                samples = samplesForSet(heartRateSamples, set)
            )
        }
    }
}

private fun pluralCircles(count: Int): String = when (count) {
    1 -> "круг"
    2, 3, 4 -> "круга"
    else -> "кругов"
}

private fun pluralExercises(count: Int): String = when (count) {
    1 -> "упражнение"
    2, 3, 4 -> "упражнения"
    else -> "упражнений"
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(we.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (we.muscleGroup.isNotBlank()) {
                Text(
                    we.muscleGroup,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

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
                samples = samplesForSet(heartRateSamples, set)
            )
        }
    }
}
