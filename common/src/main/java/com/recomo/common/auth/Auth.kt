package com.recomo.common.auth

import com.google.gson.annotations.SerializedName

/**
 * Authentication data models for V3DR Lake server
 */

data class User(
    @SerializedName("user_id") val userId: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String?
)

data class Device(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_model") val deviceModel: String?,
    @SerializedName("device_manufacturer") val deviceManufacturer: String?,
    @SerializedName("android_version") val androidVersion: String?,
    @SerializedName("app_version") val appVersion: String?,
    @SerializedName("device_serial") val deviceSerial: String?,
    @SerializedName("registered_at") val registeredAt: String?,
    @SerializedName("last_seen_at") val lastSeenAt: String?,
    @SerializedName("is_active") val isActive: Boolean
)

// Request models
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    @SerializedName("display_name") val displayName: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class DeviceRegisterRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_serial") val deviceSerial: String?,
    @SerializedName("device_model") val deviceModel: String?,
    @SerializedName("device_manufacturer") val deviceManufacturer: String?,
    @SerializedName("android_version") val androidVersion: String?,
    @SerializedName("app_version") val appVersion: String?
)

// Response models
data class AuthResponse(
    val user: User,
    val token: String
)

data class DeviceRegisterResponse(
    val device: Device,
    val token: String
)

data class TokenRefreshResponse(
    val token: String
)

// Upload-specific models
data class UploadResponse(
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("scene_id") val sceneId: String?,
    val message: String?,
    val error: String?
)
