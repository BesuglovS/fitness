package ru.besuglovs.fitness.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.besuglovs.fitness.FitnessApp
import ru.besuglovs.fitness.util.ExportUtils

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<FitnessApp>()
    private val repository get() = app.repository
    private val settings get() = app.settings

    private val _defaultRestSeconds = MutableStateFlow(settings.defaultRestSeconds)
    val defaultRestSeconds: StateFlow<Int> = _defaultRestSeconds.asStateFlow()

    fun setDefaultRestSeconds(value: Int) {
        settings.defaultRestSeconds = value.coerceIn(10, 600)
        _defaultRestSeconds.value = settings.defaultRestSeconds
    }

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    fun exportData() {
        viewModelScope.launch {
            _exportMessage.value = ExportUtils.export(app, repository)
        }
    }
}
