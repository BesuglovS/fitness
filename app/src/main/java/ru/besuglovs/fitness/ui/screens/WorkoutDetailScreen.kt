package ru.besuglovs.fitness.ui.screens

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
import ru.besuglovs.fitness.data.WorkoutWithDetails
import ru.besuglovs.fitness.data.WorkoutExerciseWithExercise
import ru.besuglovs.fitness.ui.AppViewModelProvider
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

        if (workout.isCircuit) {
            CircuitDetailList(
                details = details!!,
                totalVolume = totalVolume,
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

            details!!.exercises.forEach { we ->
                item {
                    ExerciseDetailCard(we)
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

private enum class CircuitViewMode { BY_CIRCLE, BY_EXERCISE }

@Composable
private fun CircuitDetailList(
    details: WorkoutWithDetails,
    totalVolume: Double,
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
                    CircuitCircleCard(circleNumber = circle, exercises = exercises)
                }
            }
        } else {
            exercises.forEach { we ->
                item {
                    CircuitExerciseCard(we = we)
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
    we: WorkoutExerciseWithExercise
) {
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
                    "Время",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            we.sets.sortedBy { it.setNumber }.forEach { set ->
                CircuitExerciseCardRow(circleNumber = set.setNumber, set = set)
            }
        }
    }
}

@Composable
private fun CircuitExerciseCardRow(
    circleNumber: Int,
    set: SetEntry
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
            set.durationSeconds?.let { formatTimer(it.toLong()) } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun CircuitCircleCard(
    circleNumber: Int,
    exercises: List<WorkoutExerciseWithExercise>
) {
    val restSeconds = exercises.firstNotNullOfOrNull { we ->
        we.sets.firstOrNull { it.setNumber == circleNumber }?.restSeconds
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Круг $circleNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (restSeconds != null) {
                    Text(
                        "Отдых: ${formatTimer(restSeconds.toLong())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    "Время",
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
                    CircuitExerciseRow(we = we, set = set)
                }
        }
    }
}

@Composable
private fun CircuitExerciseRow(
    we: WorkoutExerciseWithExercise,
    set: SetEntry
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
            set.durationSeconds?.let { formatTimer(it.toLong()) } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
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
private fun ExerciseDetailCard(we: ru.besuglovs.fitness.data.WorkoutExerciseWithExercise) {
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
                Text("Отдых", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            we.sets.sortedBy { it.setNumber }.forEach { set ->
                SetDetailRow(set)
            }
        }
    }
}

@Composable
private fun SetDetailRow(set: SetEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(set.setNumber.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp))
        Text(weightLabel(set.weightKg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(set.reps?.toString() ?: "-", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            set.restSeconds?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
