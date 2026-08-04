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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.components.LineChart
import ru.besuglovs.fitness.ui.viewmodel.ProgressViewModel
import ru.besuglovs.fitness.util.epley1rm
import ru.besuglovs.fitness.util.formatShortDate
import ru.besuglovs.fitness.util.weightLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen() {
    val vm: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val progression by vm.progression.collectAsStateWithLifecycle()
    val maxWeight by vm.maxWeight.collectAsStateWithLifecycle()
    val totalWorkouts by vm.totalWorkouts.collectAsStateWithLifecycle()
    val selectedId by vm.selectedExerciseId.collectAsStateWithLifecycle()
    val selected = exercises.firstOrNull { it.id == selectedId } ?: exercises.firstOrNull()

    var menuExpanded by remember { mutableStateOf(false) }

    val best1rm = progression.mapNotNull { p ->
        val w = p.maxWeight
        val r = p.maxReps
        if (w != null && r != null) epley1rm(w, r) else null
    }.maxOrNull()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Прогресс") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selected != null) {
                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selected.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Упражнение") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        exercises.forEach { ex ->
                            DropdownMenuItem(
                                text = { Text(ex.name) },
                                onClick = {
                                    vm.selectExercise(ex.id)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecordCard(
                        title = "Макс вес",
                        value = weightLabel(maxWeight) + " кг",
                        modifier = Modifier.weight(1f)
                    )
                    RecordCard(
                        title = "Макс 1ПМ",
                        value = best1rm?.let { weightLabel(it) + " кг" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecordCard(
                        title = "Тренировок",
                        value = totalWorkouts.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    RecordCard(
                        title = "Тренировок с этим упр.",
                        value = progression.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (progression.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Нет данных по этому упражнению.\nЗапиши пару тренировок — появится график.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Прогресс рабочего веса",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            LineChart(
                                values = progression.map { (it.maxWeight ?: 0.0).toFloat() },
                                xLabels = progression.map { formatShortDate(it.workoutStart) }
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Справочник упражнений пуст. Добавь упражнения и запиши тренировку.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecordCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
