package com.recomo.user.phoneteach.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.common.auth.AuthState

/**
 * Phone Teach auth screen. Login to V3DR Lake (or eventual production cloud endpoint) to get
 * a bearer token that [com.recomo.common.upload.UploadWorker] attaches to multipart uploads.
 */
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentDevice by viewModel.currentDevice.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Auth",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFF3F3F3),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Sign in to the upload server to attach a bearer token to captured sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9A9A9A)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = authState) {
                is AuthState.Authenticated -> {
                    LoggedInCard(
                        userEmail = currentUser?.email ?: "unknown",
                        userName = currentUser?.username ?: "—",
                        displayName = currentUser?.displayName,
                        deviceName = currentDevice?.deviceName ?: "(device not registered)",
                        deviceId = currentDevice?.deviceId,
                        onLogout = { viewModel.logout() }
                    )
                }
                AuthState.NotAuthenticated,
                AuthState.Unknown -> {
                    LoginForm(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onDismissError = { viewModel.clearError() },
                        onLogin = { email, password -> viewModel.login(email, password) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    isLoading: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(email, password) {
        if (errorMessage != null && (email.isNotEmpty() || password.isNotEmpty())) {
            onDismissError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF131313),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFF2D6CDF)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign in",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFEFEFEF)
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { onLogin(email, password) },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D6CDF))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signing in…")
                } else {
                    Text("Sign in")
                }
            }

            Text(
                text = "The cloud endpoint isn't live yet. Uploads will queue and fail-loud until the server migration lands — that's expected.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7A7A7A)
            )
        }
    }
}

@Composable
private fun LoggedInCard(
    userEmail: String,
    userName: String,
    displayName: String?,
    deviceName: String,
    deviceId: String?,
    onLogout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF131313),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF1F8A3F)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Signed in",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFEFEFEF)
                )
            }

            InfoRow(label = "Email", value = userEmail)
            InfoRow(label = "Username", value = userName)
            displayName?.let { InfoRow(label = "Display name", value = it) }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DEVICE",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7A7A7A),
                fontWeight = FontWeight.SemiBold
            )
            InfoRow(label = "Name", value = deviceName)
            deviceId?.let { InfoRow(label = "Id", value = it) }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9A9A9A)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFEFEFEF)
        )
    }
}

