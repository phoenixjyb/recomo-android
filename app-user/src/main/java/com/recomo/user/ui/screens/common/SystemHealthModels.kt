package com.recomo.user.ui.screens.common

enum class SyncSeverity { OK, WARNING, ERROR, CRITICAL }

data class ClockSyncStatus(
    val offsetMs: Long,
    val baselineMs: Long,
    val baselineSet: Boolean,
    val driftMs: Long,
    val severity: SyncSeverity
)

data class OrinResources(
    val cpuOverallPct: Float,
    val cpuCores: List<Float>,
    val gpuLoadPct: Float?,
    val memoryUsedPct: Float,
    val memoryUsedMb: Int,
    val memoryTotalMb: Int,
    val cpuTempC: Float?,
    val gpuTempC: Float?
)

data class ServiceEntry(
    val id: String,
    val displayName: String,
    val running: Boolean
)

data class GroupHealth(
    val name: String,
    val services: List<ServiceEntry>,
    val allHealthy: Boolean
)

data class ServiceHealthSummary(
    val totalServices: Int,
    val runningServices: Int,
    val groups: Map<String, GroupHealth>
)
