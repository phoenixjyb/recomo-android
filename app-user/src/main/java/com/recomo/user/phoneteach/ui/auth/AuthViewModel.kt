package com.recomo.user.phoneteach.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.auth.AuthRepository
import com.recomo.common.auth.AuthState
import com.recomo.common.auth.Device
import com.recomo.common.auth.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auth view model for Phone Teach. Wraps :common's [AuthRepository] and exposes the subset
 * the UI needs: login (email + password → token + device registration), logout, and live
 * auth/user/device state.
 *
 * The register flow is omitted — pre-created test accounts are used per the migration plan
 * ("no session ownership for now — test user for the whole pipeline").
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
    val currentUser: StateFlow<User?> = authRepository.currentUser
    val currentDevice: StateFlow<Device?> = authRepository.currentDevice

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    companion object {
        private const val TAG = "PhoneTeachAuth"
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email and password required"
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            authRepository.login(email.trim(), password).fold(
                onSuccess = {
                    Log.i(TAG, "Login ok for ${it.user.email}; registering device")
                    authRepository.registerDevice().fold(
                        onSuccess = {
                            Log.i(TAG, "Device registered: ${it.device.deviceId}")
                        },
                        onFailure = { err ->
                            Log.w(TAG, "Device register failed (login still ok): ${err.message}")
                        }
                    )
                },
                onFailure = { err ->
                    Log.e(TAG, "Login failed", err)
                    _errorMessage.value = err.message ?: "Login failed"
                }
            )
            _isLoading.value = false
        }
    }

    fun logout() {
        authRepository.logout()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
