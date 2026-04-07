package com.recomo.remotecontrol.v3dr.ui.screens.vio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VioDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: VioDiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIO Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            InfoSection(title = "Native Backend") {
                InfoRow("JNI loaded", state.jniLoaded.toString())
                InfoRow("Native version", state.nativeVersion)
                InfoRow("Deps check", "${state.depsResult.status} — ${state.depsResult.message}")
                InfoRow("Last updated", state.lastUpdated)
            }

            InfoSection(title = "Device") {
                InfoRow("Model", state.deviceModel)
                InfoRow("Android", state.androidVersion)
                InfoRow("ABIs", formatList(state.abiList))
            }

            InfoSection(title = "App Build") {
                InfoRow("Version", "${state.appVersionName} (${state.appVersionCode})")
            InfoRow("Debug", state.debug.toString())
            InfoRow("OPENVINS_ENABLE", state.openvinsConfig.enable)
            InfoRow("OPENVINS_DEPS_ONLY", state.openvinsConfig.depsOnly)
            InfoRow("OPENVINS_LINK_OPENCV", state.openvinsConfig.linkOpenCv)
        }

            InfoSection(title = "OpenVINS Build Paths (host)") {
                InfoRow("OPENVINS_ROOT", state.openvinsConfig.root)
                InfoRow("OPENVINS_EIGEN_ROOT", state.openvinsConfig.eigen)
                InfoRow("OPENVINS_OPENCV_DIR", state.openvinsConfig.opencv)
                InfoRow("OPENVINS_BOOST_ROOT", state.openvinsConfig.boost)
            }

            InfoSection(title = "Native Libraries") {
                InfoRow("Native lib dir", state.nativeLibDir)
                InfoRow("OpenVINS libs", formatList(state.openvinsLibs))
                InfoRow("OpenCV libs", formatList(state.opencvLibs))
                InfoRow("Boost libs", formatList(state.boostLibs))
                InfoRow("All libs (${state.nativeLibs.size})", formatList(state.nativeLibs))
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatList(items: List<String>): String {
    return if (items.isEmpty()) {
        "-"
    } else {
        items.joinToString(separator = "\n")
    }
}
