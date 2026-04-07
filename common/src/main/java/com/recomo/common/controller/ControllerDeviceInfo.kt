package com.recomo.common.controller

data class ControllerDeviceInfo(
    val connected: Boolean = false,
    val deviceId: Int? = null,
    val deviceName: String? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val profileName: String? = null,
    val lastEventAtMs: Long = 0L
)
