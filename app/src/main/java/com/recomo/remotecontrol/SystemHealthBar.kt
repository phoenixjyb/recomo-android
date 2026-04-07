package com.recomo.remotecontrol

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
import androidx.compose.material3.MaterialTheme
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
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.ui.ClockSyncStatus
import com.recomo.remotecontrol.ui.GroupHealth
import com.recomo.remotecontrol.ui.OrinResources
import com.recomo.remotecontrol.ui.RecomoControlViewModel
import com.recomo.remotecontrol.ui.ServiceHealthSummary
import com.recomo.remotecontrol.ui.SyncSeverity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Status colours shared across health components
private val ColorGreen = Color(0xFF4CAF50)
private val ColorYellow = Color(0xFFFFC107)
private val ColorOrange = Color(0xFFFF9800)
private val ColorRed = Color(0xFFF44336)
private val ColorGray = Color(0xFF888888)

// ---------------------------------------------------------------------------
// SystemHealthBar — compact top-center pill with icon indicators
// ---------------------------------------------------------------------------

@Composable
fun SystemHealthBar(
    viewModel: RecomoControlViewModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val clockSync by viewModel.clockSyncStatus.collectAsState()
    val resources by viewModel.orinResources.collectAsState()
    val serviceHealth by viewModel.serviceHealthSummary.collectAsState()
    val robotState by viewModel.robotState.collectAsState()

    // Battery from robotState — read from "battery" object { ok, voltage, percentage, ... }
    val batteryObj = robotState?.get("battery")?.jsonObject
    val batteryPct = batteryObj?.get("percentage")?.jsonPrimitive?.floatOrNull

    Surface(
        color = Color(0xCC1A1A1A),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .clickable { onTap() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gateway status
            val gwColor = when (connectionState) {
                ConnectionState.Connected -> ColorGreen
                ConnectionState.Connecting -> ColorYellow
                else -> ColorRed
            }
            HealthIcon(Icons.Default.Link, gwColor)

            // Clock sync
            val syncColor = clockSync?.let { syncSeverityColor(it.severity) } ?: ColorGray
            HealthIcon(Icons.Default.Schedule, syncColor)

            // Battery (if available)
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
                val worst = listOfNotNull(
                    res.cpuOverallPct,
                    res.gpuLoadPct,
                    res.memoryUsedPct
                ).maxOrNull() ?: 0f
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

@Composable
private fun HealthIcon(icon: ImageVector, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun HealthIconWithText(icon: ImageVector, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
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

// ---------------------------------------------------------------------------
// ORIN Tab detail cards — call OrinGroupContent from SettingsOverlay
// ---------------------------------------------------------------------------

@Composable
fun OrinGroupContent(viewModel: RecomoControlViewModel) {
    val clockSync by viewModel.clockSyncStatus.collectAsState()
    val resources by viewModel.orinResources.collectAsState()
    val serviceHealth by viewModel.serviceHealthSummary.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val stateHz by viewModel.stateHz.collectAsState()
    val gatewayApiStatus by viewModel.gatewayApiStatus.collectAsState()
    val gatewayApiLatency by viewModel.gatewayApiLatencyMs.collectAsState()
    val gatewayUrl by viewModel.gatewayUrl.collectAsState()
    val robotState by viewModel.robotState.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Robot Battery Card
        RobotBatteryCard(robotState)

        // 2. Clock Sync Card
        ClockSyncCard(clockSync)

        // 3. Gateway Health Card
        GatewayHealthCard(
            connectionState = connectionState,
            stateHz = stateHz,
            apiStatus = gatewayApiStatus,
            apiLatencyMs = gatewayApiLatency,
            gatewayUrl = gatewayUrl
        )

        // 4. Resource Usage Card
        ResourceUsageCard(resources)

        // 5. Signals & Safety Card
        SignalsCard(viewModel)

        // 6. Topic Health Card
        TopicHealthCard(viewModel)

        // 7. Service Health Card
        ServiceHealthCard(serviceHealth)
    }
}

// ---------------------------------------------------------------------------
// Card 1: Clock Sync
// ---------------------------------------------------------------------------

@Composable
private fun ClockSyncCard(clockSync: ClockSyncStatus?) {
    HealthCard(title = "Time Synchronization") {
        if (clockSync == null) {
            StatusText("No data yet — waiting for gateway state frames")
        } else {
            // Row 1: Absolute offset (informational)
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
                Text(
                    "$offsetSign$offsetStr",
                    color = Color(0xFF999999),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))

            // Row 2: Drift from baseline (this is what matters)
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
                SyncSeverity.ERROR -> "Clock drift detected — commands may be rejected"
                SyncSeverity.CRITICAL -> "Critical clock drift — all timed commands will be rejected"
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
private fun SeverityBadge(severity: SyncSeverity) {
    val (label, color) = when (severity) {
        SyncSeverity.OK -> "OK" to ColorGreen
        SyncSeverity.WARNING -> "WARN" to ColorYellow
        SyncSeverity.ERROR -> "ERROR" to ColorOrange
        SyncSeverity.CRITICAL -> "CRITICAL" to ColorRed
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Card 2: Gateway Health
// ---------------------------------------------------------------------------

@Composable
private fun GatewayHealthCard(
    connectionState: ConnectionState,
    stateHz: Double,
    apiStatus: String,
    apiLatencyMs: Long?,
    gatewayUrl: String
) {
    HealthCard(title = "Gateway Connection") {
        val statusLabel = connectionLabel(connectionState)
        val statusColor = when (connectionState) {
            ConnectionState.Connected -> ColorGreen
            ConnectionState.Connecting -> ColorYellow
            else -> ColorRed
        }
        CardStatusLine("URL", gatewayUrl.ifBlank { "--" })
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Status", color = Color(0xFFBBBBBB), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(2.dp))
        CardStatusLine("State stream", if (stateHz > 0.0) String.format("%.1f Hz", stateHz) else "--")
        val apiLabel = when {
            apiStatus == "OK" && apiLatencyMs != null -> "OK ${apiLatencyMs}ms"
            apiStatus == "OK" -> "OK"
            apiStatus == "ERR" -> "ERR"
            else -> "--"
        }
        CardStatusLine("API health", apiLabel)
    }
}

// ---------------------------------------------------------------------------
// Card 3: Resource Usage
// ---------------------------------------------------------------------------

@Composable
private fun ResourceUsageCard(resources: OrinResources?) {
    HealthCard(title = "Orin Resources") {
        if (resources == null) {
            StatusText("No data — endpoint /api/system/resources not available")
        } else {
            // CPU overall
            ResourceBar("CPU Overall", resources.cpuOverallPct)
            Spacer(Modifier.height(4.dp))
            // Per-core mini bars (up to 8 shown)
            if (resources.cpuCores.isNotEmpty()) {
                Text("CPU Cores", color = Color(0xFFBBBBBB), fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    resources.cpuCores.take(12).forEach { pct ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    resourceColor(pct).copy(alpha = 0.8f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            // GPU
            if (resources.gpuLoadPct != null) {
                ResourceBar("GPU Load", resources.gpuLoadPct)
                Spacer(Modifier.height(4.dp))
            }
            // Memory
            val memLabel = if (resources.memoryTotalMb > 0) {
                "${resources.memoryUsedMb}MB / ${resources.memoryTotalMb}MB"
            } else {
                "${resources.memoryUsedMb}MB"
            }
            ResourceBar("Memory", resources.memoryUsedPct, memLabel)
            // Temperatures
            Spacer(Modifier.height(6.dp))
            val temps = listOfNotNull(
                resources.cpuTempC?.let { "CPU: ${it.toInt()}°C" },
                resources.gpuTempC?.let { "GPU: ${it.toInt()}°C" }
            )
            if (temps.isNotEmpty()) {
                Text(
                    "Temps: ${temps.joinToString("  ")}",
                    color = Color(0xFFBBBBBB),
                    fontSize = 11.sp
                )
            }
        }
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

// ---------------------------------------------------------------------------
// Card 4: Signals & Safety
// ---------------------------------------------------------------------------

@Composable
private fun SignalsCard(viewModel: RecomoControlViewModel) {
    val robotState by viewModel.robotState.collectAsState()

    val baseOk = robotState?.get("base")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val armOk = robotState?.get("arm")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val gimbalOk = robotState?.get("gimbal")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val cameraOk = robotState?.get("camera")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false

    val safetyObj = robotState?.get("safety")?.jsonObject
    val estop = safetyObj?.get("estop")?.jsonPrimitive?.booleanOrNull ?: false
    val freezeAll = safetyObj?.get("freeze_all")?.jsonPrimitive?.booleanOrNull ?: false
    val commOk = safetyObj?.get("comm_ok")?.jsonPrimitive?.booleanOrNull ?: true
    val deadmanOk = safetyObj?.get("deadman_ok")?.jsonPrimitive?.booleanOrNull ?: true

    val lastWarning = robotState?.get("safety")?.jsonObject
        ?.get("last_warning")?.jsonPrimitive?.contentOrNull

    HealthCard(title = "Signals & Safety") {
        // Driver row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Drivers:", color = Color(0xFFBBBBBB), fontSize = 11.sp, modifier = Modifier.width(54.dp))
            SignalDot(baseOk)
            Text("base", color = Color(0xFFCCCCCC), fontSize = 11.sp)
            SignalDot(armOk)
            Text("arm", color = Color(0xFFCCCCCC), fontSize = 11.sp)
            SignalDot(gimbalOk)
            Text("gimbal", color = Color(0xFFCCCCCC), fontSize = 11.sp)
            SignalDot(cameraOk)
            Text("cam", color = Color(0xFFCCCCCC), fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        // Safety row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Safety:", color = Color(0xFFBBBBBB), fontSize = 11.sp, modifier = Modifier.width(54.dp))
            // estop: green = NOT triggered (ok), red = triggered
            SignalDot(!estop)
            Text("estop", color = Color(0xFFCCCCCC), fontSize = 11.sp)
            SignalDot(!freezeAll)
            Text("freeze", color = Color(0xFFCCCCCC), fontSize = 11.sp)
            SignalDot(commOk)
            Text("comm", color = Color(0xFFCCCCCC), fontSize = 11.sp)
            SignalDot(deadmanOk)
            Text("dman", color = Color(0xFFCCCCCC), fontSize = 11.sp)
        }
        if (lastWarning != null && lastWarning.isNotBlank() && lastWarning != "null") {
            Spacer(Modifier.height(4.dp))
            Text("Last warning: $lastWarning", color = ColorYellow, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SignalDot(ok: Boolean) {
    Box(
        Modifier
            .size(8.dp)
            .background(if (ok) ColorGreen else ColorRed, CircleShape)
    )
}

// ---------------------------------------------------------------------------
// Card 5: Topic Health
// ---------------------------------------------------------------------------

private data class TopicRow(
    val label: String,
    val pubs: Int?,       // null if topic has no publisher-count field
    val ok: Boolean
)

@Composable
private fun TopicHealthCard(viewModel: RecomoControlViewModel) {
    val robotState by viewModel.robotState.collectAsState()
    val th = robotState?.get("topic_health")?.jsonObject

    fun pubs(key: String) = th?.get(key)?.jsonPrimitive?.intOrNull
    fun ok(key: String) = th?.get(key)?.jsonPrimitive?.booleanOrNull ?: false

    // Build topic rows per category (label, pubs, ok)
    val chassisRows = listOf(
        TopicRow("odom_wheel", pubs("odom_pubs"), ok("odom_ok")),
        TopicRow("chassis_imu", pubs("imu_pubs"), ok("imu_ok"))
    )
    val armRows = listOf(
        TopicRow("arm_state", pubs("arm_state_pubs"), ok("arm_ok"))
    )
    val gimbalRows = listOf(
        TopicRow("gimbal_state", pubs("gimbal_state_pubs"), ok("gimbal_ok"))
    )
    val cameraRows = listOf(
        TopicRow("camera_pose", null, ok("camera_ok")),
        TopicRow("camera_tf", null, ok("camera_tf_ok"))
    )
    val slamRows = listOf(
        TopicRow("robot_pose", pubs("robot_pose_pubs"), ok("robot_pose_ok"))
    )
    val pncRows = listOf(
        TopicRow("fixed_pos", pubs("fixed_position_status_pubs"), (pubs("fixed_position_status_pubs") ?: 0) > 0),
        TopicRow("fp_artifact", pubs("fixed_position_artifact_status_pubs"), (pubs("fixed_position_artifact_status_pubs") ?: 0) > 0),
        TopicRow("fp_executor", pubs("fixed_position_executor_state_pubs"), (pubs("fixed_position_executor_state_pubs") ?: 0) > 0),
        TopicRow("fp_stage2", pubs("fixed_position_stage2_status_pubs"), (pubs("fixed_position_stage2_status_pubs") ?: 0) > 0)
    )
    val pipelineRows = listOf(
        TopicRow("simpletrack", pubs("simple_track_status_pubs"), (pubs("simple_track_status_pubs") ?: 0) > 0),
        TopicRow("live_pnc", pubs("run_live_pnc_status_pubs"), (pubs("run_live_pnc_status_pubs") ?: 0) > 0)
    )

    HealthCard(title = "Topic Health") {
        if (th == null) {
            StatusText("No topic_health in state — gateway may be older version")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Left column: Chassis, Gimbal, SLAM, PnC
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TopicCategoryHeader("Chassis")
                    chassisRows.forEach { TopicRowItem(it) }
                    Spacer(Modifier.height(4.dp))
                    TopicCategoryHeader("Gimbal")
                    gimbalRows.forEach { TopicRowItem(it) }
                    Spacer(Modifier.height(4.dp))
                    TopicCategoryHeader("SLAM")
                    slamRows.forEach { TopicRowItem(it) }
                    Spacer(Modifier.height(4.dp))
                    TopicCategoryHeader("PnC")
                    pncRows.forEach { TopicRowItem(it) }
                }
                // Right column: Arm, Camera, Pipeline
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TopicCategoryHeader("Arm")
                    armRows.forEach { TopicRowItem(it) }
                    Spacer(Modifier.height(4.dp))
                    TopicCategoryHeader("Camera")
                    cameraRows.forEach { TopicRowItem(it) }
                    Spacer(Modifier.height(4.dp))
                    TopicCategoryHeader("Pipeline")
                    pipelineRows.forEach { TopicRowItem(it) }
                }
            }
        }
    }
}

@Composable
private fun TopicCategoryHeader(text: String) {
    Text(
        text,
        color = Color(0xFFBBBBBB),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun TopicRowItem(row: TopicRow) {
    val dotColor = when {
        row.ok -> ColorGreen
        (row.pubs ?: 0) > 0 -> ColorGreen
        else -> ColorGray
    }
    val statusText = when {
        row.pubs != null -> if (row.ok || row.pubs > 0) "${row.pubs} ok" else "${row.pubs} --"
        else -> if (row.ok) "ok" else "--"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Text(row.label, color = Color(0xFFCCCCCC), fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(statusText, color = dotColor, fontSize = 10.sp)
    }
}

// ---------------------------------------------------------------------------
// Card 6: Service Health
// ---------------------------------------------------------------------------

@Composable
private fun ServiceHealthCard(summary: ServiceHealthSummary?) {
    HealthCard(title = "Services") {
        if (summary == null) {
            StatusText("No data")
        } else {
            CardStatusLine(
                "Overall",
                "${summary.runningServices}/${summary.totalServices} running"
            )
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
                        .background(
                            if (group.allHealthy) ColorGreen else ColorYellow,
                            CircleShape
                        )
                )
                Text(
                    group.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

// ---------------------------------------------------------------------------
// Shared helpers for cards
// ---------------------------------------------------------------------------

@Composable
private fun HealthCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Card: Robot Battery
// ---------------------------------------------------------------------------

@Composable
private fun RobotBatteryCard(robotState: JsonObject?) {
    val batteryObj = robotState?.get("battery")?.jsonObject
    HealthCard(title = "Robot Battery") {
        if (batteryObj == null) {
            StatusText("No battery data — chassis driver may not be running")
        } else {
            val pct = batteryObj["percentage"]?.jsonPrimitive?.floatOrNull
            val voltage = batteryObj["voltage"]?.jsonPrimitive?.doubleOrNull
            val temp = batteryObj["temperature"]?.jsonPrimitive?.doubleOrNull
            val current = batteryObj["current"]?.jsonPrimitive?.doubleOrNull
            val present = batteryObj["present"]?.jsonPrimitive?.booleanOrNull ?: false

            // Percentage bar
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
                        // Mini bar
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

            // Voltage
            if (voltage != null && !voltage.isNaN()) {
                CardStatusLine("Voltage", String.format("%.1fV", voltage))
            }

            // Temperature
            if (temp != null && !temp.isNaN() && temp != 0.0) {
                CardStatusLine("Temperature", String.format("%.1f\u00B0C", temp))
            }

            // Current
            if (current != null && !current.isNaN()) {
                CardStatusLine("Current", String.format("%.2fA", current))
            }

            // Present
            if (!present) {
                Spacer(Modifier.height(4.dp))
                Text("Battery not present", color = ColorRed, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CardStatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp)
        Text(value, color = Color(0xFFDDDDDD), fontSize = 12.sp)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = Color(0xFF888888), fontSize = 11.sp)
}

// connectionLabel is defined in SystemStatusPanel.kt as internal — accessible in same package
