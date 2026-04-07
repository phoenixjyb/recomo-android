package com.recomo.remotecontrol.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StepSettingsViewModel @Inject constructor(
    private val repository: StepSettingsRepository
) : ViewModel() {
    val stepSettings: StateFlow<StepSettings> = repository.stepSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StepSettings())

    fun update(settings: StepSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)
        }
    }
}
