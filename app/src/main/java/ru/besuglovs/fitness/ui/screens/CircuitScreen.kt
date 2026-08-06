package ru.besuglovs.fitness.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.cos
import ru.besuglovs.fitness.data.Exercise
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.viewmodel.CircuitPhase
import ru.besuglovs.fitness.ui.viewmodel.CircuitViewModel
import ru.besuglovs.fitness.util.formatTimer
import ru.besuglovs.fitness.util.weightLabel

private val LightGreenBg = Color(0xFFE8F5E9)
private val LightYellowBg = Color(0xFFFFF9C4)
private val LightRedBg = Color(0xFFFFEBEE)
private val RestOverdueRed = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitScreen(onFinish: () -> Unit) {
    val vm: CircuitViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val allExercises by vm.allExercises.collectAsStateWithLifecycle()
    val selectedExercises by vm.selectedExercises.collectAsStateWithLifecycle()
    val setupWeights by vm.setupWeights.collectAsStateWithLifecycle()
    val setupReps by vm.setupReps.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val circuitNumber by vm.circuitNumber.collectAsStateWithLifecycle()
    val exerciseIndex by vm.exerciseIndex.collectAsStateWithLifecycle()
    val activeExercise by vm.activeExercise.collectAsStateWithLifecycle()
    val setElapsed by vm.setElapsed.collectAsStateWithLifecycle()
    val setPaused by vm.setPaused.collectAsStateWithLifecycle()
    val pauseElapsed by vm.pauseElapsed.collectAsStateWithLifecycle()
    val restElapsed by vm.restElapsed.collectAsStateWithLifecycle()
    val completedSets by vm.completedSets.collectAsStateWithLifecycle()
    val entryWeights by vm.entryWeights.collectAsStateWithLifecycle()
    val entryReps by vm.entryReps.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()

    var showFinishConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onFinish()
    }

    BackHandler {
        if (phase == CircuitPhase.SETUP) onFinish() else showFinishConfirm = true
    }

    val backgroundColor = when (phase) {
        CircuitPhase.SETUP -> MaterialTheme.colorScheme.surface
        CircuitPhase.EXERCISE -> LightGreenBg
        CircuitPhase.REP_ENTRY ->
            if (restElapsed > vm.restLimitSeconds) LightRedBg else LightYellowBg
    }

    StatusBarForBackground(backgroundColor)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
                title = { Text("Круговая тренировка") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (phase == CircuitPhase.SETUP) onFinish() else showFinishConfirm = true
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (phase != CircuitPhase.SETUP) {
                        TextButton(onClick = { showFinishConfirm = true }) {
                            Text("Завершить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (phase) {
                CircuitPhase.SETUP -> {
                    CircuitSetupContent(
                        allExercises = allExercises,
                        selected = selectedExercises,
                        setupWeights = setupWeights,
                        setupReps = setupReps,
                        onAdd = vm::addExercise,
                        onRemove = vm::removeExercise,
                        onWeightChange = vm::updateSetupWeight,
                        onRepsChange = vm::updateSetupReps,
                        onStart = vm::startTraining
                    )
                }

                CircuitPhase.EXERCISE -> {
                    activeExercise?.let { ex ->
                        CircuitExerciseContent(
                            circuitNumber = circuitNumber,
                            index = exerciseIndex,
                            count = selectedExercises.size,
                            exercise = ex,
                            lastWeight = vm.lastWeightOf(ex.id),
                            elapsedSeconds = setElapsed,
                            paused = setPaused,
                            pauseElapsed = pauseElapsed,
                            onTogglePause = vm::toggleSetPause,
                            onComplete = vm::completeSet
                        )
                    }
                }

                CircuitPhase.REP_ENTRY -> {
                    CircuitEntryContent(
                        circuitNumber = circuitNumber,
                        exercises = selectedExercises,
                        entryWeights = entryWeights,
                        entryReps = entryReps,
                        restElapsed = restElapsed,
                        restLimit = vm.restLimitSeconds,
                        restPaused = vm.restPaused.collectAsStateWithLifecycle().value,
                        onToggleRestPause = vm::toggleRestPause,
                        onWeightChange = vm::updateEntryWeight,
                        onRepsChange = vm::updateEntryReps,
                        onNext = vm::startNextCircuit,
                        onFinish = { showFinishConfirm = true }
                    )
                }
            }
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Завершить тренировку?") },
            text = {
                val setsCount = vm.totalSetsCount()
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
}

@Composable
private fun StatusBarForBackground(backgroundColor: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as Activity).window
    val controller = WindowCompat.getInsetsController(window, view)
    val lightStatusBars = backgroundColor.luminance() > 0.5f

    DisposableEffect(backgroundColor) {
        val previousLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = lightStatusBars
        onDispose {
            controller.isAppearanceLightStatusBars = previousLight
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircuitSetupContent(
    allExercises: List<Exercise>,
    selected: List<Exercise>,
    setupWeights: Map<Long, String>,
    setupReps: Map<Long, String>,
    onAdd: (Exercise) -> Unit,
    onRemove: (Long) -> Unit,
    onWeightChange: (Long, String) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Составь круг из упражнений и укажи вес и начальное количество повторений. Они повторяются по кругу, а вес подставится из предыдущего круга.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AddExerciseDropdown(exercises = allExercises, onSelect = onAdd)

        if (selected.isEmpty()) {
            Text(
                "Пока не выбрано ни одного упражнения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                selected.forEach { ex ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ex.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        listOf(ex.category, ex.muscleGroup).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemove(ex.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = setupWeights[ex.id] ?: "",
                                    onValueChange = {
                                        onWeightChange(ex.id, it.filter { c -> c.isDigit() || c == '.' || c == ',' })
                                    },
                                    label = { Text("Вес, кг") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = setupReps[ex.id] ?: "",
                                    onValueChange = {
                                        onRepsChange(ex.id, it.filter(Char::isDigit).take(3))
                                    },
                                    label = { Text("Повторения") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onStart,
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Начать тренировку", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseDropdown(
    exercises: List<Exercise>,
    onSelect: (Exercise) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Добавить упражнение из справочника") },
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
                            onSelect(ex)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CircuitExerciseContent(
    circuitNumber: Int,
    index: Int,
    count: Int,
    exercise: Exercise,
    lastWeight: Double?,
    elapsedSeconds: Long,
    paused: Boolean,
    pauseElapsed: Long,
    onTogglePause: () -> Unit,
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
                "Круг $circuitNumber · Упражнение ${index + 1} из $count",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (lastWeight != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (circuitNumber <= 1) {
                        "Вес: ${weightLabel(lastWeight)} кг"
                    } else {
                        "Вес прошлого круга: ${weightLabel(lastWeight)} кг"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            CircuitBigTimer(
                label = if (paused) "Пауза · внеплановый отдых" else "Время подхода",
                value = formatTimer(if (paused) pauseElapsed else elapsedSeconds),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = half)
                    .weight(1f)
                    .clickable { onComplete() }
            )

            Text(
                "Нажми на таймер, чтобы завершить подход",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onTogglePause,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (paused) "Продолжить" else "Пауза", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CircuitEntryContent(
    circuitNumber: Int,
    exercises: List<Exercise>,
    entryWeights: Map<Long, String>,
    entryReps: Map<Long, String>,
    restElapsed: Long,
    restLimit: Int,
    restPaused: Boolean,
    onToggleRestPause: () -> Unit,
    onWeightChange: (Long, String) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val overdue = restElapsed > restLimit
    val blinkTransition = rememberInfiniteTransition(label = "restBlink")
    val blinkProgress by blinkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "restBlinkProgress"
    )
    val blinkValue = (1f - cos(blinkProgress * (2f * PI.toFloat()))) / 2f
    val valueAlpha = if (overdue && !restPaused) 0.15f + 0.85f * blinkValue else 1f
    var showEntries by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Круг $circuitNumber завершён",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (showEntries) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                exercises.forEach { ex ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = entryWeights[ex.id] ?: "",
                                    onValueChange = {
                                        onWeightChange(ex.id, it.filter { c -> c.isDigit() || c == '.' || c == ',' })
                                    },
                                    label = { Text("Вес, кг") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = entryReps[ex.id] ?: "",
                                    onValueChange = {
                                        onRepsChange(ex.id, it.filter(Char::isDigit).take(3))
                                    },
                                    label = { Text("Повторения") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        CircuitBigTimer(
            label = if (restPaused) "Отдых · Пауза" else "Отдых до следующего круга",
            value = formatTimer(restElapsed),
            valueColor = if (overdue) RestOverdueRed else MaterialTheme.colorScheme.onSurface,
            valueAlpha = valueAlpha,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleRestPause() }
                .then(
                    if (showEntries) Modifier.height(100.dp)
                    else Modifier.weight(1f)
                )
        )

        TextButton(
            onClick = { showEntries = !showEntries },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (showEntries) "Скрыть список" else "Показать список", style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Text("Следующий", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Text("Завершить", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun CircuitBigTimer(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueAlpha: Float = 1f
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val fontSize = if (value.isNotEmpty()) {
            val byWidth = maxWidth.value / (value.length * 0.6f)
            val byHeight = maxHeight.value * 0.55f
            minOf(byWidth, byHeight).coerceIn(32f, 400f)
        } else {
            (maxHeight.value * 0.55f).coerceIn(32f, 300f)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alpha(valueAlpha)
            )
        }
    }
}
