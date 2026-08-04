package ru.besuglovs.fitness.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.viewmodel.ActiveSetUi
import ru.besuglovs.fitness.ui.viewmodel.WorkoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(onFinish: () -> Unit) {
    val vm: WorkoutViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val allExercises by vm.allExercises.collectAsStateWithLifecycle()
    val restElapsed by vm.restElapsed.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val selectedExerciseId by vm.selectedExerciseId.collectAsStateWithLifecycle()
    val weightInput by vm.weightInput.collectAsStateWithLifecycle()
    val activeSet by vm.activeSet.collectAsStateWithLifecycle()
    val setElapsed by vm.setElapsed.collectAsStateWithLifecycle()

    var showFinishConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showRepsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onFinish()
    }

    BackHandler {
        if (exercises.isNotEmpty()) showExitConfirm = true else onFinish()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (exercises.isNotEmpty()) showExitConfirm = true else onFinish()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { showFinishConfirm = true }) {
                        Text("Завершить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val active = activeSet
            if (active != null) {
                SetInProgressContent(
                    activeSet = active,
                    elapsedSeconds = setElapsed,
                    onComplete = { showRepsDialog = true }
                )
            } else {
                RestPhaseContent(
                    restElapsed = restElapsed,
                    restLimitSeconds = vm.restLimitSeconds,
                    exercises = allExercises,
                    selectedExerciseId = selectedExerciseId,
                    weight = weightInput,
                    onSelectExercise = vm::selectExercise,
                    onWeightChange = vm::updateWeight,
                    onStartSet = vm::startSet
                )
            }
        }
    }

    if (showRepsDialog) {
        val active = activeSet
        if (active != null) {
            RepsDialog(
                exerciseName = active.exercise.name,
                weight = active.weight,
                onConfirm = { reps ->
                    vm.completeSet(reps)
                    showRepsDialog = false
                },
                onDismiss = { showRepsDialog = false }
            )
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Завершить тренировку?") },
            text = {
                val setsCount = exercises.sumOf { it.sets.size }
                Text(if (setsCount == 0) "Подходов ещё не записано. Всё равно завершить?" else "Будет сохранено подходов: $setsCount")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    vm.finishWorkout()
                }) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Выйти из тренировки?") },
            text = { Text("Несохранённые подходы будут потеряны.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onFinish()
                }) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun RestPhaseContent(
    restElapsed: Int?,
    restLimitSeconds: Int,
    exercises: List<Exercise>,
    selectedExerciseId: Long?,
    weight: String,
    onSelectExercise: (Long) -> Unit,
    onWeightChange: (String) -> Unit,
    onStartSet: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val half = maxHeight * 0.5f
        Column(modifier = Modifier.fillMaxSize()) {
            BigTimer(
                label = "Отдых",
                value = formatSetTime((restElapsed ?: 0).toLong()),
                valueColor = if (restElapsed != null && restElapsed > restLimitSeconds) {
                    RestOverdueRed
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = half)
                    .weight(1f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExerciseDropdown(
                    exercises = exercises,
                    selectedExerciseId = selectedExerciseId,
                    onSelect = onSelectExercise
                )

                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = { Text("Вес, кг") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onStartSet,
                    enabled = selectedExerciseId != null && weight.trim().isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Начать подход", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDropdown(
    exercises: List<Exercise>,
    selectedExerciseId: Long?,
    onSelect: (Long) -> Unit
) {
    val selected = exercises.firstOrNull { it.id == selectedExerciseId }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Упражнение") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (exercises.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("В библиотеке нет упражнений") },
                    onClick = { expanded = false }
                )
            } else {
                exercises.forEach { ex ->
                    DropdownMenuItem(
                        text = { Text(ex.name) },
                        onClick = {
                            onSelect(ex.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetInProgressContent(
    activeSet: ActiveSetUi,
    elapsedSeconds: Long,
    onComplete: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val half = maxHeight * 0.5f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                activeSet.exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${activeSet.weight} кг",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BigTimer(
                label = "Время подхода",
                value = formatSetTime(elapsedSeconds),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = half)
                    .weight(1f)
            )

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Завершить подход", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BigTimer(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val fontSize = if (value.isNotEmpty()) {
            (maxWidth.value / (value.length * 0.6f)).coerceIn(64f, 220f)
        } else {
            120f
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private val RestOverdueRed = Color(0xFFE53935)

@Composable
private fun RepsDialog(
    exerciseName: String,
    weight: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var repsInput by remember { mutableStateOf("") }
    val reps = repsInput.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Завершить подход") },
        text = {
            Column {
                Text("$exerciseName · $weight кг", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it.filter(Char::isDigit).take(3) },
                    label = { Text("Повторения") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reps?.takeIf { it > 0 }?.let(onConfirm) },
                enabled = reps?.let { it > 0 } == true
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun formatSetTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
