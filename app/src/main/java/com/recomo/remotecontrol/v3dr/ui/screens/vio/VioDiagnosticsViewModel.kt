package com.recomo.remotecontrol.v3dr.ui.screens.vio

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.vio.VioDepsResult
import com.recomo.remotecontrol.v3dr.vio.VioDepsStatus
import com.recomo.remotecontrol.v3dr.vio.VioRunner
import com.recomo.remotecontrol.v3dr.vio.openvins.OpenVinsNative
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class VioDiagnosticsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    data class OpenVinsBuildConfig(
        val enable: String,
        val depsOnly: String,
        val linkOpenCv: String,
        val root: String,
        val eigen: String,
        val opencv: String,
        val boost: String
    )

    data class VioDiagnosticsState(
        val isRefreshing: Boolean = false,
        val jniLoaded: Boolean = false,
        val nativeVersion: String = "unknown",
        val depsResult: VioDepsResult = VioDepsResult(
            status = VioDepsStatus.NOT_AVAILABLE,
            message = "Not checked"
        ),
        val deviceModel: String = Build.MODEL,
        val androidVersion: String = Build.VERSION.RELEASE ?: "unknown",
        val abiList: List<String> = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
        val appVersionName: String = "unknown",
        val appVersionCode: Int = 0,
        val debug: Boolean = false,
        val openvinsConfig: OpenVinsBuildConfig = OpenVinsBuildConfig(
            enable = "false",
            depsOnly = "false",
            linkOpenCv = "false",
            root = "N/A",
            eigen = "N/A",
            opencv = "N/A",
            boost = "N/A"
        ),
        val nativeLibDir: String = "",
        val nativeLibs: List<String> = emptyList(),
        val openvinsLibs: List<String> = emptyList(),
        val opencvLibs: List<String> = emptyList(),
        val boostLibs: List<String> = emptyList(),
        val lastUpdated: String = ""
    )

    private val _state = MutableStateFlow(VioDiagnosticsState())
    val state: StateFlow<VioDiagnosticsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isRefreshing = true)

            val app = getApplication<Application>()
            val nativeDir = app.applicationInfo.nativeLibraryDir ?: ""
            val libs = File(nativeDir).list()?.sorted() ?: emptyList()
            val openvinsLibs = libs.filter { it.contains("openvins", ignoreCase = true) }
            val opencvLibs = libs.filter { it.contains("opencv", ignoreCase = true) }
            val boostLibs = libs.filter { it.contains("boost", ignoreCase = true) }

            val jniLoaded = OpenVinsNative.isLoaded()
            val nativeVersion = if (jniLoaded) {
                runCatching { OpenVinsNative.nativeVersion() }
                    .getOrElse { "error: ${it.message ?: "unknown"}" }
            } else {
                "not loaded"
            }

            val depsResult = runCatching { VioRunner().checkDependencies() }
                .getOrElse {
                    VioDepsResult(
                        status = VioDepsStatus.ERROR,
                        message = it.message ?: "Deps check failed"
                    )
                }

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            _state.value = _state.value.copy(
                isRefreshing = false,
                jniLoaded = jniLoaded,
                nativeVersion = nativeVersion,
                depsResult = depsResult,
                nativeLibDir = nativeDir,
                nativeLibs = libs,
                openvinsLibs = openvinsLibs,
                opencvLibs = opencvLibs,
                boostLibs = boostLibs,
                lastUpdated = timestamp
            )
        }
    }
}
