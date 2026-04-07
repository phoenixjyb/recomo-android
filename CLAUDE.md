# ReCoMo Android — Agent Instructions

## Project Overview

This is the Android tablet app for the ReCoMo robot platform. Three Gradle modules:

| Module | Package | Purpose |
|--------|---------|---------|
| `:app` | `com.recomo.remotecontrol` | Engineering app (internal use, don't modify unless asked) |
| `:common` | `com.recomo.common` | Shared library — gateway client, models, video, 3D preview, controller |
| `:app-user` | `com.recomo.user` | **User-facing app** — this is where most work happens |

## Build & Install

```bash
# Build user app
./gradlew :app-user:assembleDebug

# Install on connected tablet
adb install -r app-user/build/outputs/apk/debug/app-user-debug.apk

# Build engineering app (rarely needed)
./gradlew :app:assembleDebug
```

## Architecture

### :app-user module structure

```
app-user/src/main/java/com/recomo/user/
├── UserMainActivity.kt          — Activity, Bluetooth controller wiring
├── UserApplication.kt           — Hilt app entry point
├── controller/
│   └── UserControllerRouter.kt  — Gamepad → gateway command mapping
├── control/                     — ViewModels (business logic)
│   ├── UserGatewayViewModel.kt  — Gateway connection + robot state
│   ├── UserTouchControlViewModel.kt — Chassis/arm/gimbal commands
│   ├── UserRunCommandViewModel.kt   — RUN mode (LivePnC, SimpleTrack)
│   ├── UserNavigationViewModel.kt   — PnC navigation, POI, GO/STOP
│   ├── UserSystemViewModel.kt       — Prepare/dismiss, driver lifecycle
│   ├── UserMapLocalizationViewModel.kt — Map switching, localization
│   ├── UserLibraryViewModel.kt      — Motion library browser
│   ├── UserMotionCreatorViewModel.kt — CopyStyle preset selection
│   ├── UserMediaGalleryViewModel.kt  — On-device media browser
│   ├── UserPostRecordViewModel.kt    — Post-recording review
│   └── UserRunVideoViewModel.kt     — Live video stream in RUN mode
├── data/                        — Repositories & data layer
│   ├── UserSettingsRepository.kt
│   ├── media/UserMediaManager.kt    — On-device file storage
│   ├── video/UserVideoStreamClient.kt — MJPEG/H.265 video from gateway
│   ├── video/UserVideoRecorder.kt   — Record MJPEG stream locally
│   ├── trajectory/LocalTrajectorySessionRepository.kt
│   ├── system/UserOrinServiceRepository.kt
│   └── postrecord/UserPostRecordRepository.kt
├── di/UserAppModule.kt          — Hilt dependency injection
└── ui/screens/                  — Compose UI
    ├── UserMainScreen.kt        — Root screen, workspace routing
    ├── UserMainViewModel.kt     — Top-level UI state
    ├── connection/ConnectionScreen.kt — Gateway IP entry
    ├── main/MainControlScreen.kt     — Main control dashboard
    ├── touch/TouchControlScreen.kt   — Manual chassis/arm/gimbal control
    ├── run/RunWorkspace.kt           — RUN mode (mission execution)
    ├── map/SlamMapsWorkspace.kt      — Map/localization management
    ├── library/MotionLibraryWorkspace.kt — Browse motion sessions
    ├── creator/MotionCreatorShell.kt — CopyStyle preset selector
    ├── media/MediaGalleryWorkspace.kt — On-device recordings
    ├── postrecord/PostRecordWorkspace.kt — Review after recording
    ├── preview/TrajectoryPreviewScreen.kt — 3D trajectory viewer
    ├── settings/SettingsPanel.kt     — App settings
    ├── system/SystemCards.kt         — System status cards
    └── runner/MotionRunnerScreen.kt  — Active motion execution
```

### :common module (shared, changes affect both apps)

```
common/src/main/java/com/recomo/common/
├── network/OrinGatewayClient.kt — WebSocket client (ws://<orin-ip>:9077)
├── model/                       — RobotState, RobotProfile, DriverState, etc.
├── controller/                  — Bluetooth gamepad input (ControllerManager, profiles)
├── video/VideoDecoder.kt        — H.265 hardware decoding
├── chat/                        — Chat UI + trajectory resolver
├── preview/                     — 3D preview (Filament, URDF, kinematics)
└── service/                     — Service control + video management clients
```

**Important:** `:common` is shared between `:app` and `:app-user`. Changes here affect the engineering app too. Keep changes backward-compatible or coordinate.

## How the App Talks to the Robot

The app does NOT control the robot directly. It connects to the **gateway** (a ROS2 C++ node on the Orin) via WebSocket:

```
Tablet App  ──ws://orin:9077──►  Gateway Node  ──ROS2 topics──►  Robot Hardware
                                      │
                                      ▼
                              JSON commands/state
```

- **State:** Gateway pushes `RobotState` JSON every ~100ms (safety, base, arm, gimbal, nav, drivers)
- **Commands:** App sends JSON control messages (`DriveCmd`, `JogCmd`, `SafetyCmd`, `FixedPositionCmd`, etc.)
- **Video:** MJPEG stream via separate WebSocket bridge on port 9091

You do NOT need the gateway source code to develop the app. The JSON protocol is the contract.

### Key command types

| Command | Purpose |
|---------|---------|
| `DriveCmd` | Chassis velocity (vx, vy, wz) |
| `JogCmd` | Arm or gimbal velocity/position |
| `SafetyCmd` | E-stop, clear, freeze |
| `ArmHomeCmd` | Send arm to home pose |
| `FixedPositionCmd` | LivePnC: start trajectory at POI |
| `PreparePose` | Move to prepare position |
| `DismissCmd` | Shut down actuators |

## Bluetooth Controller Mapping

Gamepad support is wired in `UserMainActivity` → `UserControllerRouter`:

| Input | Action |
|-------|--------|
| L1 (hold) | Deadman — must hold for any motion |
| Left stick | Chassis translate (forward/back/strafe) |
| Right stick X | Chassis yaw (turn) |
| Right stick Y | Gimbal pitch (tilt) |
| L2/R2 triggers | Chassis yaw (alternative) |
| D-pad up/down | Arm joint nudge |
| L3/R3 | Cycle arm joint (0/1/2) |
| A | Arm homing |
| B | Gimbal homing |
| START/HOME hold (1.5s) | E-stop |
| Y hold (1.5s) | Clear e-stop |

Profiles: Generic + BSP-D9 (auto-detected).

## On-Device Media

CopyStyle preview videos are stored on-device (not in the APK):
```
/sdcard/Android/data/com.recomo.user/files/Recomo/
├── CopyStyle/     — copystyle_video_{1..4}.mp4 (pushed via adb)
├── Recordings/    — tablet-side MJPEG recordings
├── Library/       — downloaded trajectory sessions
└── Downloads/     — media synced from Orin
```

## i18n

String resources in `app-user/src/main/res/values/strings.xml` (EN) and `values-zh/strings.xml` (ZH). 105 string resources. Use `stringResource(R.string.xxx)` in Compose, never hardcode user-visible text.

## Git Workflow

- **Branch off `main`** for all work
- **File a Merge Request** — direct push to `main` is blocked
- Keep changes within `app-user/` and `common/` only
- Don't modify `:app` (engineering app) unless explicitly asked

## Tech Stack

- Kotlin, Jetpack Compose, Material3
- Hilt (dependency injection)
- Kotlin Coroutines + Flow (async/state)
- ExoPlayer (video playback)
- SceneView/Filament (3D preview)
- OkHttp WebSocket (gateway connection)
