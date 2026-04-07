package com.recomo.remotecontrol.v3dr.ui.screens.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.data.model.UploadProgress
import com.recomo.remotecontrol.v3dr.upload.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for Upload screen
 */
@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadRepository: UploadRepository
) : ViewModel() {

    val uploads: StateFlow<List<UploadProgress>> = uploadRepository.uploads
        .map { it.values.sortedByDescending { upload -> upload.startTime } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uploadUrl: StateFlow<String?> = uploadRepository.uploadUrl

    fun setUploadUrl(url: String) {
        uploadRepository.setUploadUrl(url)
    }

    fun retryUpload(sessionId: String) {
        uploadRepository.retryUpload(sessionId)
    }

    fun cancelUpload(sessionId: String) {
        uploadRepository.cancelUpload(sessionId)
    }
}
