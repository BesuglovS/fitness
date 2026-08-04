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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.besuglovs.fitness.data.SetEntry
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.viewmodel.WorkoutDetailViewModel
import ru.besuglovs.fitness.util.formatDateTime
import ru.besuglovs.fitness.util.formatDuration
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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

            details!!.exercises.forEach { we ->
                item {
                    ExerciseDetailCard(we)
                }
            }
        }
    }
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
