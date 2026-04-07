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
class ControllerSettingsViewModel @Inject constructor(
    private val repository: ControllerSettingsRepository
) : ViewModel() {
    val settings: StateFlow<ControllerSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ControllerSettings())

    fun update(settings: ControllerSettings) {
        viewModelScope.launch {
            repository.update(settings)
        }
    }
}
