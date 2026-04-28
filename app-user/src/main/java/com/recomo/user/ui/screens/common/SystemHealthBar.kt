package com.recomo.user.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.common.model.ConnectionState
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Status colours
private val ColorGreen = Color(0xFF4CAF50)
private val ColorYellow = Color(0xFFFFC107)
private val ColorOrange = Color(0xFFFF9800)
private val ColorRed = Color(0xFFF44336)
private val ColorGray = Color(0xFF888888)

/**
 * Compact top-center pill showing gateway, clock sync, battery, resources, services.
 */
@Composable
fun SystemHealthBar(
    viewModel: SystemHealthViewModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val clockSync by viewModel.clockSyncStatus.collectAsState()
    val resources by viewModel.orinResources.collectAsState()
    val serviceHealth by viewModel.serviceHealthSummary.collectAsState()
    val robotState by viewModel.robotState.collectAsState()

    val batteryObj = robotState?.get("battery")?.jsonObject
    val batteryPct = batteryObj?.get("percentage")?.jsonPrimitive?.floatOrNull

    Surface(
        color = Color(0xCC1A1A1A),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = modifier.clickable { onTap() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gateway
            val gwColor = when (connectionState) {
                ConnectionState.Connected -> ColorGreen
                ConnectionState.Connecting -> ColorYellow
                else -> ColorRed
            }
            HealthIcon(Icons.Default.Link, gwColor)

            // Clock sync
            val syncColor = clockSync?.let { syncSeverityColor(it.severity) } ?: ColorGray
            HealthIcon(Icons.Default.Schedule, syncColor)

            // Battery
            if (batteryPct != null) {
                val batColor = when {
                    batteryPct > 25f -> ColorGreen
                    batteryPct > 10f -> ColorYellow
                    else -> ColorRed
                }
                HealthIconWithText(Icons.Default.BatteryStd, "${batteryPct.toInt()}%", batColor)
            }

            // Resources
            val resColor = resources?.let { res ->
                val worst = listOfNotNull(res.cpuOverallPct, res.gpuLoadPct, res.memoryUsedPct)
                    .maxOrNull() ?: 0f
                resourceColor(worst)
            } ?: ColorGray
            val resText = resources?.let { res ->
                val worst = listOfNotNull(res.cpuOverallPct, res.gpuLoadPct, res.memoryUsedPct)
                    .maxOrNull() ?: 0f
                "${worst.toInt()}%"
            } ?: "--"
            HealthIconWithText(Icons.Default.Memory, resText, resColor)

            // Services
            val svcColor = serviceHealth?.let { sh ->
                when {
                    sh.runningServices == sh.totalServices -> ColorGreen
                    sh.runningServices > 0 -> ColorYellow
                    else -> ColorRed
                }
            } ?: ColorGray
            val svcText = serviceHealth?.let { "${it.runningServices}/${it.totalServices}" } ?: "--"
            HealthIconWithText(Icons.Default.Settings, svcText, svcColor)
        }
    }
}

/**
 * Expanded detail panel showing all health cards. Wrap in a dialog or overlay.
 */
@Composable
fun SystemHealthDetail(viewModel: SystemHealthViewModel) {
    val clockSync by viewModel.clockSyncStatus.collectAsState()
    val resources by viewModel.orinResources.collectAsState()
    val serviceHealth by viewModel.serviceHealthSummary.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val stateHz by viewModel.stateHz.collectAsState()
    val robotState by viewModel.robotState.collectAsState()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Battery
        RobotBatteryCard(robotState)
        // Clock sync
        ClockSyncCard(clockSync)
        // Gateway
        GatewayHealthCard(connectionState, stateHz)
        // Resources
        ResourceUsageCard(resources)
        // Services
        ServiceHealthCard(serviceHealth)
    }
}

// ── Cards ───────────────────────────────────────────────────────────

@Composable
private fun RobotBatteryCard(robotState: kotlinx.serialization.json.JsonObject?) {
    val batteryObj = robotState?.get("battery")?.jsonObject
    HealthCard(title = "Robot Battery") {
        if (batteryObj == null) {
            StatusText("No battery data")
        } else {
            val pct = batteryObj["percentage"]?.jsonPrimitive?.floatOrNull
            val voltage = batteryObj["voltage"]?.jsonPrimitive?.let {
                it.doubleOrNull
            }
            if (pct != null) {
                val pctColor = when {
                    pct > 50f -> ColorGreen
                    pct > 25f -> ColorYellow
                    pct > 10f -> ColorOrange
                    else -> ColorRed
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Charge", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .width(60.dp)
                                .height(10.dp)
                                .background(Color(0xFF333333), RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                Modifier
                                    .width((60 * (pct / 100f).coerceIn(0f, 1f)).dp)
                                    .height(10.dp)
                                    .background(pctColor, RoundedCornerShape(3.dp))
                            )
                        }
                        Text(
                            "${pct.toInt()}%",
                            color = pctColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (voltage != null && !voltage.isNaN()) {
                CardStatusLine("Voltage", String.format("%.1fV", voltage))
            }
        }
    }
}

@Composable
private fun ClockSyncCard(clockSync: ClockSyncStatus?) {
    HealthCard(title = "Time Synchronization") {
        if (clockSync == null) {
            StatusText("No data yet")
        } else {
            val offsetAbs = kotlin.math.abs(clockSync.offsetMs)
            val offsetStr = when {
                offsetAbs < 1000 -> "${clockSync.offsetMs}ms"
                else -> String.format("%.2fs", clockSync.offsetMs / 1000.0)
            }
            val offsetSign = if (clockSync.offsetMs >= 0) "+" else ""
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Offset (Orin - Tablet)", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                Text("$offsetSign$offsetStr", color = Color(0xFF999999), fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))

            val driftAbs = kotlin.math.abs(clockSync.driftMs)
            val driftStr = when {
                !clockSync.baselineSet -> "calibrating..."
                driftAbs < 1000 -> "${clockSync.driftMs}ms"
                else -> String.format("%.2fs", clockSync.driftMs / 1000.0)
            }
            val driftSign = if (clockSync.driftMs >= 0) "+" else ""
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Drift from baseline", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                Text(
                    if (clockSync.baselineSet) "$driftSign$driftStr" else driftStr,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", color = Color(0xFFBBBBBB), fontSize = 12.sp)
                SeverityBadge(clockSync.severity)
            }
            val warningText = when (clockSync.severity) {
                SyncSeverity.ERROR -> "Clock drift detected"
                SyncSeverity.CRITICAL -> "Critical clock drift — commands will be rejected"
                else -> null
            }
            if (warningText != null) {
                Spacer(Modifier.height(6.dp))
                Text(warningText, color = ColorOrange, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GatewayHealthCard(connectionState: ConnectionState, stateHz: Double) {
    HealthCard(title = "Gateway Connection") {
        val statusLabel = when (connectionState) {
            ConnectionState.Connected -> "Connected"
            ConnectionState.Connecting -> "Connecting"
            ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Error -> "Error"
        }
        val statusColor = when (connectionState) {
            ConnectionState.Connected -> ColorGreen
            ConnectionState.Connecting -> ColorYellow
            else -> ColorRed
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Status", color = Color(0xFFBBBBBB), fontSize = 12.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(2.dp))
        CardStatusLine(
            "State stream",
            if (stateHz > 0.0) String.format("%.1f Hz", stateHz) else "--"
        )
    }
}

@Composable
private fun ResourceUsageCard(resources: OrinResources?) {
    HealthCard(title = "Orin Resources") {
        if (resources == null) {
            StatusText("No data")
        } else {
            ResourceBar("CPU Overall", resources.cpuOverallPct)
            Spacer(Modifier.height(4.dp))
            if (resources.cpuCores.isNotEmpty()) {
                Text("CPU Cores", color = Color(0xFFBBBBBB), fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    resources.cpuCores.take(12).forEach { pct ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(resourceColor(pct).copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (resources.gpuLoadPct != null) {
                ResourceBar("GPU Load", resources.gpuLoadPct)
                Spacer(Modifier.height(4.dp))
            }
            val memLabel = if (resources.memoryTotalMb > 0) {
                "${resources.memoryUsedMb}MB / ${resources.memoryTotalMb}MB"
            } else {
                "${resources.memoryUsedMb}MB"
            }
            ResourceBar("Memory", resources.memoryUsedPct, memLabel)
            Spacer(Modifier.height(6.dp))
            val temps = listOfNotNull(
                resources.cpuTempC?.let { "CPU: ${it.toInt()}\u00B0C" },
                resources.gpuTempC?.let { "GPU: ${it.toInt()}\u00B0C" }
            )
            if (temps.isNotEmpty()) {
                Text("Temps: ${temps.joinToString("  ")}", color = Color(0xFFBBBBBB), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ServiceHealthCard(summary: ServiceHealthSummary?) {
    HealthCard(title = "Services") {
        if (summary == null) {
            StatusText("No data")
        } else {
            CardStatusLine("Overall", "${summary.runningServices}/${summary.totalServices} running")
            Spacer(Modifier.height(6.dp))
            summary.groups.values.forEach { group ->
                ServiceGroupRow(group)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ServiceGroupRow(group: GroupHealth) {
    var expanded by remember { mutableStateOf(true) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (group.allHealthy) ColorGreen else ColorYellow, CircleShape)
                )
                Text(group.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(16.dp)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                group.services.forEach { svc ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(svc.displayName, color = Color(0xFFCCCCCC), fontSize = 11.sp)
                        Text(
                            if (svc.running) "Running" else "Stopped",
                            color = if (svc.running) ColorGreen else ColorRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Shared helpers ──────────────────────────────────────────────────

@Composable
private fun HealthIcon(icon: ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}

@Composable
private fun HealthIconWithText(icon: ImageVector, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = text, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SeverityBadge(severity: SyncSeverity) {
    val (label, color) = when (severity) {
        SyncSeverity.OK -> "OK" to ColorGreen
        SyncSeverity.WARNING -> "WARN" to ColorYellow
        SyncSeverity.ERROR -> "ERROR" to ColorOrange
        SyncSeverity.CRITICAL -> "CRITICAL" to ColorRed
    }
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
        Text(
            label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ResourceBar(label: String, pct: Float, rightLabel: String? = null) {
    val color = resourceColor(pct)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 11.sp, modifier = Modifier.width(68.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color(0xFF333333), RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Text(
            rightLabel ?: "${pct.toInt()}%",
            color = color,
            fontSize = 11.sp,
            modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
private fun HealthCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CardStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp)
        Text(value, color = Color(0xFFDDDDDD), fontSize = 12.sp)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = Color(0xFF888888), fontSize = 11.sp)
}

private fun syncSeverityColor(severity: SyncSeverity) = when (severity) {
    SyncSeverity.OK -> ColorGreen
    SyncSeverity.WARNING -> ColorYellow
    SyncSeverity.ERROR -> ColorOrange
    SyncSeverity.CRITICAL -> ColorRed
}

private fun resourceColor(pct: Float) = when {
    pct < 70f -> ColorGreen
    pct < 90f -> ColorYellow
    else -> ColorRed
}
