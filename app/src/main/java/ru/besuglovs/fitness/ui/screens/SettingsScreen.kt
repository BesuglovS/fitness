package ru.besuglovs.fitness.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.besuglovs.fitness.ui.AppViewModelProvider
import ru.besuglovs.fitness.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val context = LocalContext.current

    val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()

    var restSecondsText by remember { mutableStateOf(vm.defaultRestSeconds.value.toString()) }
    var restFieldFocused by remember { mutableStateOf(false) }
    var showExportDenied by remember { mutableStateOf(false) }

    fun commitRestSeconds() {
        val parsed = restSecondsText.toIntOrNull()?.coerceIn(10, 600)
        if (parsed != null) {
            vm.setDefaultRestSeconds(parsed)
        }
        restSecondsText = vm.defaultRestSeconds.value.toString()
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.exportData()
        } else {
            showExportDenied = true
        }
    }

    fun startExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vm.exportData()
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.exportData()
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle("Тренировка")

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Время отдыха между подходами") },
                    supportingContent = {
                        OutlinedTextField(
                            value = restSecondsText,
                            onValueChange = { value ->
                                restSecondsText = value.filter { it.isDigit() }.take(4)
                            },
                            label = { Text("Секунд") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .width(120.dp)
                                .padding(top = 8.dp)
                                .onFocusChanged { state ->
                                    if (restFieldFocused && !state.isFocused) {
                                        commitRestSeconds()
                                    }
                                    restFieldFocused = state.isFocused
                                }
                        )
                    }
                )
            }

            SectionTitle("Данные")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { startExport() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Экспортировать данные (JSON)")
                    }
                    if (showExportDenied) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Нет разрешения на запись в Downloads. Разрешите доступ к хранилищу и повторите.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    exportMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { vm.clearExportMessage() },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                        ) {
                            Text("Понятно")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
