package com.recomo.user.ui.screens.main

enum class MainControlTone {
    Neutral,
    Brand,
    Secondary,
    Success,
    Warning,
    Danger
}

enum class MainControlToolKey {
    Grid,
    Focus,
    Frame,
    Lens,
    Navigation,
    Maps,
    Gallery,
    Settings
}

data class MainControlToolButtonUiState(
    val key: MainControlToolKey,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true
)

data class MainControlMetricUiState(
    val label: String,
    val value: String,
    val tone: MainControlTone = MainControlTone.Neutral
)

data class MainControlHeaderUiState(
    val deviceName: String,
    val connectionLabel: String,
    val recordingLabel: String? = null,
    val telemetry: List<MainControlMetricUiState> = emptyList()
)

data class MainControlPreviewUiState(
    val title: String,
    val detail: String? = null,
    val statusLabel: String? = null,
    val showGrid: Boolean = false,
    val showReticle: Boolean = true,
    val horizonLabel: String = "0.0°",
    val cameraParams: List<MainControlMetricUiState> = emptyList()
)

enum class MainControlShortcutKey {
    MotionLibrary,
    CreateMotion,
    SmartFollow
}

data class MainControlShortcutUiState(
    val key: MainControlShortcutKey,
    val title: String,
    val detail: String,
    val tone: MainControlTone = MainControlTone.Neutral,
    val enabled: Boolean = true
)

enum class MainControlTransportControlKey {
    Reset,
    PlayPause,
    Record,
    Save,
    Stop
}

data class MainControlTransportControlUiState(
    val key: MainControlTransportControlKey,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true
)

data class MainControlPrimaryActionUiState(
    val label: String,
    val detail: String? = null,
    val enabled: Boolean = true
)

data class MainControlBottomDockUiState(
    val shortcuts: List<MainControlShortcutUiState> = emptyList(),
    val transportControls: List<MainControlTransportControlUiState> = emptyList(),
    val primaryAction: MainControlPrimaryActionUiState? = null
)

data class MainControlJointUiState(
    val label: String,
    val angleLabel: String,
    val fillFraction: Float,
    val tone: MainControlTone = MainControlTone.Brand
)

data class MainControlRecentMotionUiState(
    val id: String,
    val title: String,
    val detail: String,
    val tone: MainControlTone = MainControlTone.Brand
)

data class MainControlSidebarUiState(
    val connectionLabel: String,
    val statusMetrics: List<MainControlMetricUiState> = emptyList(),
    val joints: List<MainControlJointUiState> = emptyList(),
    val recentMotions: List<MainControlRecentMotionUiState> = emptyList()
)

data class MainControlUiState(
    val header: MainControlHeaderUiState,
    val preview: MainControlPreviewUiState,
    val primaryTools: List<MainControlToolButtonUiState> = emptyList(),
    val secondaryTools: List<MainControlToolButtonUiState> = emptyList(),
    val bottomDock: MainControlBottomDockUiState = MainControlBottomDockUiState(),
    val sidebar: MainControlSidebarUiState? = null
) {
    companion object {
        fun sample(): MainControlUiState =
            MainControlUiState(
                header = MainControlHeaderUiState(
                    deviceName = "RECOMO T8",
                    connectionLabel = "Connected",
                    recordingLabel = "REC 01:24",
                    telemetry = listOf(
                        MainControlMetricUiState("Signal", "92%", MainControlTone.Brand),
                        MainControlMetricUiState("Battery", "76%", MainControlTone.Success),
                        MainControlMetricUiState("Latency", "18 ms", MainControlTone.Success),
                        MainControlMetricUiState("Frame", "30 fps", MainControlTone.Neutral)
                    )
                ),
                preview = MainControlPreviewUiState(
                    title = "Live Camera",
                    detail = "Wide lens · 1080p stream",
                    statusLabel = "Operator ready",
                    showGrid = true,
                    horizonLabel = "0.0°",
                    cameraParams = listOf(
                        MainControlMetricUiState("ISO", "800"),
                        MainControlMetricUiState("F", "2.8"),
                        MainControlMetricUiState("SS", "1/60"),
                        MainControlMetricUiState("WB", "5600K"),
                        MainControlMetricUiState("EV", "+0.3")
                    )
                ),
                primaryTools = listOf(
                    MainControlToolButtonUiState(MainControlToolKey.Grid, "Grid", selected = true),
                    MainControlToolButtonUiState(MainControlToolKey.Focus, "Focus"),
                    MainControlToolButtonUiState(MainControlToolKey.Frame, "Frame"),
                    MainControlToolButtonUiState(MainControlToolKey.Lens, "Lens")
                ),
                secondaryTools = listOf(
                    MainControlToolButtonUiState(MainControlToolKey.Maps, "Maps"),
                    MainControlToolButtonUiState(MainControlToolKey.Gallery, "Gallery"),
                    MainControlToolButtonUiState(MainControlToolKey.Settings, "Settings")
                ),
                bottomDock = MainControlBottomDockUiState(
                    shortcuts = listOf(
                        MainControlShortcutUiState(
                            key = MainControlShortcutKey.MotionLibrary,
                            title = "Motion Library",
                            detail = "5 saved motions"
                        ),
                        MainControlShortcutUiState(
                            key = MainControlShortcutKey.CreateMotion,
                            title = "Create Motion",
                            detail = "Quick capture setup",
                            tone = MainControlTone.Brand
                        )
                    ),
                    transportControls = listOf(
                        MainControlTransportControlUiState(MainControlTransportControlKey.Reset, "Reset"),
                        MainControlTransportControlUiState(MainControlTransportControlKey.PlayPause, "Play"),
                        MainControlTransportControlUiState(
                            MainControlTransportControlKey.Record,
                            "Record",
                            selected = true
                        ),
                        MainControlTransportControlUiState(MainControlTransportControlKey.Save, "Save"),
                        MainControlTransportControlUiState(MainControlTransportControlKey.Stop, "Stop")
                    ),
                    primaryAction = MainControlPrimaryActionUiState(
                        label = "Touch Control",
                        detail = "Manual operator mode"
                    )
                ),
                sidebar = MainControlSidebarUiState(
                    connectionLabel = "Connected",
                    statusMetrics = listOf(
                        MainControlMetricUiState("Signal", "92%", MainControlTone.Brand),
                        MainControlMetricUiState("Battery", "76%", MainControlTone.Success),
                        MainControlMetricUiState("Temp", "38°C", MainControlTone.Neutral),
                        MainControlMetricUiState("Latency", "18 ms", MainControlTone.Success)
                    ),
                    joints = listOf(
                        MainControlJointUiState("J1", "45°", 0.62f, MainControlTone.Brand),
                        MainControlJointUiState("J2", "-12°", 0.47f, MainControlTone.Brand),
                        MainControlJointUiState("J3", "90°", 0.75f, MainControlTone.Secondary),
                        MainControlJointUiState("J4", "0°", 0.50f, MainControlTone.Secondary),
                        MainControlJointUiState("J5", "33°", 0.59f, MainControlTone.Success),
                        MainControlJointUiState("J6", "-5°", 0.49f, MainControlTone.Success)
                    ),
                    recentMotions = listOf(
                        MainControlRecentMotionUiState("welcome", "Welcome to T8", "30 s"),
                        MainControlRecentMotionUiState(
                            "coffee",
                            "Grab a Coffee",
                            "22 s",
                            MainControlTone.Warning
                        ),
                        MainControlRecentMotionUiState(
                            "product360",
                            "Product 360",
                            "15 s",
                            MainControlTone.Success
                        )
                    )
                )
            )
    }
}
