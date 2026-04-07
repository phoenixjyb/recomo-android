package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.user.data.postrecord.UserPostRecordItem
import com.recomo.user.data.postrecord.UserPostRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserPostRecordUiState(
    val recordings: List<UserPostRecordItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class UserPostRecordViewModel @Inject constructor(
    private val repository: UserPostRecordRepository
) : ViewModel() {
    val uiState: StateFlow<UserPostRecordUiState> = combine(
        repository.recordings,
        repository.isLoading,
        repository.error
    ) { recordings, isLoading, errorMessage ->
        UserPostRecordUiState(
            recordings = recordings,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPostRecordUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
        }
    }

    fun deleteRecording(item: UserPostRecordItem) {
        viewModelScope.launch {
            repository.deleteRecording(item)
        }
    }
}
