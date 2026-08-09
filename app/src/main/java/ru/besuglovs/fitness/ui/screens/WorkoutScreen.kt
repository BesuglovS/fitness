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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import ru.besuglovs.fitness.ui.viewmodel.WorkoutPhase
import ru.besuglovs.fitness.ui.viewmodel.WorkoutViewModel
import ru.besuglovs.fitness.util.formatGap
import ru.besuglovs.fitness.util.formatTimer
import ru.besuglovs.fitness.util.weightLabel

private val LightGreenBg = Color(0xFFE8F5E9)
private val LightYellowBg = Color(0xFFFFF9C4)
private val LightRedBg = Color(0xFFFFEBEE)
private val RestOverdueRed = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(onFinish: () -> Unit, onExit: () -> Unit) {
    val vm: WorkoutViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val allExercises by vm.allExercises.collectAsStateWithLifecycle()
    val currentExercise by vm.currentExercise.collectAsStateWithLifecycle()
    val setupWeights by vm.setupWeights.collectAsStateWithLifecycle()
    val setupReps by vm.setupReps.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val setElapsed by vm.setElapsed.collectAsStateWithLifecycle()
    val setPaused by vm.setPaused.collectAsStateWithLifecycle()
    val pauseElapsed by vm.pauseElapsed.collectAsStateWithLifecycle()
    val restElapsed by vm.restElapsed.collectAsStateWithLifecycle()
    val entryWeight by vm.entryWeight.collectAsStateWithLifecycle()
    val entryReps by vm.entryReps.collectAsStateWithLifecycle()
    val entryExerciseId by vm.entryExerciseId.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val exited by vm.exited.collectAsStateWithLifecycle()
    val resumeGapSeconds by vm.resumeGapSeconds.collectAsStateWithLifecycle()

    var showFinishConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showAddExercise by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onFinish()
    }

    LaunchedEffect(exited) {
        if (exited) onExit()
    }

    BackHandler {
        if (phase == WorkoutPhase.SETUP) onExit() else showExitConfirm = true
    }

    val backgroundColor = when (phase) {
        WorkoutPhase.SETUP -> MaterialTheme.colorScheme.surface
        WorkoutPhase.EXERCISE -> LightGreenBg
        WorkoutPhase.REST ->
            if (restElapsed > vm.restLimitSeconds) LightRedBg else LightYellowBg
    }

    StatusBarForBackground(backgroundColor)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (phase == WorkoutPhase.SETUP) onExit() else showExitConfirm = true
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (phase != WorkoutPhase.SETUP) {
                        TextButton(onClick = { showFinishConfirm = true }) {
                            Text("Завершить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (phase) {
                WorkoutPhase.SETUP -> {
                    WorkoutSetupContent(
                        exercise = currentExercise,
                        allExercises = allExercises,
                        weight = setupWeights[currentExercise?.id].orEmpty(),
                        reps = setupReps[currentExercise?.id].orEmpty(),
                        onSelectExercise = vm::selectExercise,
                        onWeightChange = { vm.updateSetupWeight(currentExercise?.id ?: -1L, it) },
                        onRepsChange = { vm.updateSetupReps(currentExercise?.id ?: -1L, it) },
                        onStart = vm::startTraining
                    )
                }

                WorkoutPhase.EXERCISE -> {
                    currentExercise?.let { ex ->
                        WorkoutExerciseContent(
                            exercise = ex,
                            setNumber = vm.nextSetNumber(ex.id),
                            lastWeight = vm.lastWeightOf(ex.id),
                            elapsedSeconds = setElapsed,
                            paused = setPaused,
                            pauseElapsed = pauseElapsed,
                            onTogglePause = vm::toggleSetPause,
                            onComplete = vm::completeSet
                        )
                    }
                }

                WorkoutPhase.REST -> {
                    val entryEx = allExercises.firstOrNull { it.id == entryExerciseId }
                    WorkoutRestContent(
                        entryExercise = entryEx,
                        entrySetNumber = vm.nextSetNumber(entryExerciseId),
                        currentExercise = currentExercise,
                        allExercises = allExercises,
                        entryWeight = entryWeight,
                        entryReps = entryReps,
                        entryValid = vm.isEntrySetValid(),
                        currentWeight = setupWeights[currentExercise?.id].orEmpty(),
                        currentReps = setupReps[currentExercise?.id].orEmpty(),
                        restElapsed = restElapsed,
                        restLimit = vm.restLimitSeconds,
                        onEntryWeightChange = vm::updateEntryWeight,
                        onEntryRepsChange = vm::updateEntryReps,
                        onSelectCurrent = vm::selectExercise,
                        onCurrentWeightChange = { vm.updateSetupWeight(currentExercise?.id ?: -1L, it) },
                        onCurrentRepsChange = { vm.updateSetupReps(currentExercise?.id ?: -1L, it) },
                        onNext = vm::nextApproach,
                        onFinish = { showFinishConfirm = true },
                        onAddExercise = { showAddExercise = true }
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
                val total = vm.totalSetsCount()
                Text(if (total == 0) "Подходов ещё не записано. Всё равно завершить?" else "Будет сохранено подходов: $total")
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
            text = { Text("Записанные подходы сохранятся, и ты сможешь продолжить тренировку позже.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    vm.saveAndExit()
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

    if (resumeGapSeconds != null && !showExitConfirm && !showFinishConfirm) {
        val gapText = formatGap(resumeGapSeconds ?: 0L)
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Продолжить тренировку") },
            text = {
                Text("Ты отсутствовал $gapText. Считать это время отдыхом?")
            },
            confirmButton = {
                TextButton(onClick = { vm.onResumeGapDecided(true) }) {
                    Text("Да, отдых")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onResumeGapDecided(false) }) {
                    Text("Нет")
                }
            }
        )
    }

    if (showAddExercise) {
        AddExerciseDialog(
            onSave = { name, muscleGroup, category ->
                showAddExercise = false
                vm.addExercise(name, muscleGroup, category) { created ->
                    vm.selectExercise(created)
                }
            },
            onDismiss = { showAddExercise = false }
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

@Composable
private fun WorkoutSetupContent(
    exercise: Exercise?,
    allExercises: List<Exercise>,
    weight: String,
    reps: String,
    onSelectExercise: (Exercise) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Выбери упражнение и укажи начальный вес и количество повторений.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExerciseDropdown(
            exercises = allExercises,
            selected = exercise,
            onSelect = onSelectExercise
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    exercise?.name ?: "Упражнение не выбрано",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { onWeightChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                        label = { Text("Вес, кг") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { onRepsChange(it.filter(Char::isDigit).take(3)) },
                        label = { Text("Повторения") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStart,
            enabled = exercise != null && weight.isNotBlank() && reps.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Начать подход", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WorkoutExerciseContent(
    exercise: Exercise,
    setNumber: Int,
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
                exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Подход $setNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (lastWeight != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Вес: ${weightLabel(lastWeight)} кг",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            BigTimer(
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
private fun WorkoutRestContent(
    entryExercise: Exercise?,
    entrySetNumber: Int,
    currentExercise: Exercise?,
    allExercises: List<Exercise>,
    entryWeight: String,
    entryReps: String,
    entryValid: Boolean,
    currentWeight: String,
    currentReps: String,
    restElapsed: Long,
    restLimit: Int,
    onEntryWeightChange: (String) -> Unit,
    onEntryRepsChange: (String) -> Unit,
    onSelectCurrent: (Exercise) -> Unit,
    onCurrentWeightChange: (String) -> Unit,
    onCurrentRepsChange: (String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onAddExercise: () -> Unit
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
    val valueAlpha = if (overdue) 0.15f + 0.85f * blinkValue else 1f
    var showEntries by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showEntries) {
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                entryExercise?.let { ex ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                "Выполнено · ${ex.name} · подход $entrySetNumber",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = entryWeight,
                                    onValueChange = { onEntryWeightChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                                    label = { Text("Вес, кг") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = entryReps,
                                    onValueChange = { onEntryRepsChange(it.filter(Char::isDigit).take(3)) },
                                    label = { Text("Повторения") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Text(
                    "Следующее упражнение",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ExerciseDropdown(
                    exercises = allExercises,
                    selected = currentExercise,
                    onSelect = onSelectCurrent
                )
                TextButton(
                    onClick = onAddExercise,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("+ Добавить новое упражнение", style = MaterialTheme.typography.bodyMedium)
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            currentExercise?.name ?: "Упражнение не выбрано",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = currentWeight,
                                onValueChange = { onCurrentWeightChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                                label = { Text("Вес, кг") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currentReps,
                                onValueChange = { onCurrentRepsChange(it.filter(Char::isDigit).take(3)) },
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

        val canNext = entryValid && currentExercise != null && currentWeight.isNotBlank() && currentReps.isNotBlank()

        BigTimer(
            label = "Отдых",
            value = formatTimer(restElapsed),
            valueColor = if (overdue) RestOverdueRed else MaterialTheme.colorScheme.onSurface,
            valueAlpha = valueAlpha,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canNext) { onNext() }
                .then(
                    if (showEntries) Modifier.height(150.dp)
                    else Modifier.weight(1f)
                )
        )

        Text(
            "Нажми на таймер, чтобы начать следующий подход",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = { showEntries = !showEntries },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (showEntries) "Скрыть" else "Показать", style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNext,
                enabled = canNext,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDropdown(
    exercises: List<Exercise>,
    selected: Exercise?,
    onSelect: (Exercise) -> Unit
) {
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
private fun AddExerciseDialog(
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Силовая") }
    var muscleGroup by remember { mutableStateOf("") }
    var showCategoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое упражнение") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                CategoryDropdown(
                    value = category,
                    options = listOf("Силовая", "Кардио", "Свободный вес", "Растяжка", "Другое"),
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it },
                    onSelect = { category = it }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = muscleGroup,
                    onValueChange = { muscleGroup = it },
                    label = { Text("Группа мышц") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, muscleGroup, category) },
                enabled = name.isNotBlank()
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Категория") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun BigTimer(
    label: String? = null,
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
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
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