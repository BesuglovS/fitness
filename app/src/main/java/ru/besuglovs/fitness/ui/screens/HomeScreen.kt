package ru.besuglovs.fitness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.besuglovs.fitness.data.Workout
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.viewmodel.HomeViewModel
import ru.besuglovs.fitness.util.formatDateTime
import ru.besuglovs.fitness.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartWorkout: (Long, Int) -> Unit,
    onStartCircuit: (Long, Int) -> Unit,
    onResumeWorkout: (Long, Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenWorkout: (Long) -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val recentWorkouts by vm.recentWorkouts.collectAsStateWithLifecycle()
    val workoutCount by vm.workoutCount.collectAsStateWithLifecycle()
    val exerciseCount by vm.exerciseCount.collectAsStateWithLifecycle()
    val unfinished by vm.unfinished.collectAsStateWithLifecycle()
    val createdId by vm.createdWorkoutId.collectAsStateWithLifecycle()
    val defaultRest by vm.defaultRestSeconds.collectAsStateWithLifecycle()
    var showRestDialog by remember { mutableStateOf(false) }
    var pendingRestSeconds by remember { mutableStateOf(60) }
    var pendingMode by remember { mutableStateOf("normal") }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshDefaultRest()
    }

    LaunchedEffect(createdId) {
        createdId?.let { id ->
            vm.onWorkoutOpened()
            if (pendingMode == "circuit") {
                onStartCircuit(id, pendingRestSeconds)
            } else {
                onStartWorkout(id, pendingRestSeconds)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Фитнес-дневник") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = {
                        pendingMode = "normal"
                        pendingRestSeconds = defaultRest
                        showRestDialog = true
                    },
                    enabled = unfinished == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Начать тренировку", style = MaterialTheme.typography.titleMedium)
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        pendingMode = "circuit"
                        pendingRestSeconds = defaultRest
                        showRestDialog = true
                    },
                    enabled = unfinished == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Filled.Repeat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Круговая тренировка", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (unfinished != null) {
                item {
                    Text(
                        "Сначала завершите или удалите текущую тренировку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Тренировок",
                        value = workoutCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Упражнений в базе",
                        value = exerciseCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (unfinished != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Есть незавершённая тренировка от " +
                                    formatDateTime(unfinished!!.startTime),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Продолжи с того места, где остановился.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        onResumeWorkout(unfinished!!.id, unfinished!!.isCircuit)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Продолжить")
                                }
                                TextButton(onClick = { showDiscardConfirm = true }) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Последние тренировки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentWorkouts.isEmpty()) {
                item {
                    Text(
                        "Пока нет тренировок. Нажми «Начать тренировку».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(recentWorkouts, key = { it.id }) { workout ->
                WorkoutListItem(workout = workout, onClick = { onOpenWorkout(workout.id) })
            }

            item {
                OutlinedButton(
                    onClick = onOpenLibrary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Справочник упражнений")
                }
            }
        }
    }

    if (showRestDialog) {
        RestTimeDialog(
            title = if (pendingMode == "circuit") "Отдых между кругами" else "Время отдыха между подходами",
            initialSeconds = defaultRest,
            onConfirm = { seconds ->
                pendingRestSeconds = seconds
                showRestDialog = false
                vm.startWorkout()
            },
            onDismiss = { showRestDialog = false }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Удалить незавершённую тренировку?") },
            text = { Text("Все записанные подходы будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    vm.discardUnfinished()
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimeDialog(
    title: String,
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var secondsText by remember { mutableStateOf(initialSeconds.toString()) }
    val presets = listOf(30, 60, 90, 120, 150, 180, 240, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Секунд") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = secondsText.toIntOrNull() == preset,
                            onClick = { secondsText = preset.toString() },
                            label = { Text("${preset / 60}:${"%02d".format(preset % 60)}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    secondsText.toIntOrNull()?.coerceIn(10, 600)?.let(onConfirm)
                },
                enabled = secondsText.toIntOrNull() != null
            ) {
                Text("Начать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutListItem(workout: Workout, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(formatDateTime(workout.startTime)) },
            supportingContent = {
                Text("Длительность: ${formatDuration(workout.startTime, workout.endTime)}")
            }
        )
    }
}
