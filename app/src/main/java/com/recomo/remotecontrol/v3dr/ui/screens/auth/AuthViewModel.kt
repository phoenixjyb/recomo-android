package com.recomo.remotecontrol.v3dr.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.data.model.User
import com.recomo.remotecontrol.v3dr.data.repository.AuthRepository
import com.recomo.remotecontrol.v3dr.data.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
    val currentUser: StateFlow<User?> = authRepository.currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.login(email, password)
                .onSuccess { response ->
                    // After login, register device
                    authRepository.registerDevice()
                        .onSuccess {
                            _loginSuccess.value = true
                        }
                        .onFailure { e ->
                            // Login succeeded but device registration failed - still OK
                            _loginSuccess.value = true
                        }
                }
                .onFailure { e ->
                    _errorMessage.value = e.message ?: "Login failed"
                }

            _isLoading.value = false
        }
    }

    fun register(email: String, username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.register(email, username, password)
                .onSuccess { response ->
                    // After registration, register device
                    authRepository.registerDevice()
                        .onSuccess {
                            _loginSuccess.value = true
                        }
                        .onFailure {
                            // Registration succeeded but device registration failed - still OK
                            _loginSuccess.value = true
                        }
                }
                .onFailure { e ->
                    _errorMessage.value = e.message ?: "Registration failed"
                }

            _isLoading.value = false
        }
    }

    fun logout() {
        authRepository.logout()
        _loginSuccess.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun isAuthenticated(): Boolean = authRepository.isAuthenticated()
}
