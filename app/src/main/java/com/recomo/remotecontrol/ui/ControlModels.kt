package com.recomo.remotecontrol.ui

enum class ControlFocus {
    CHASSIS,
    ARM,
    GIMBAL
}

enum class ControlMode {
    FOCUS,
    POSITION,
    UPPER,
    CINE,
    ALL_DOF
}

enum class StepMode {
    FINE,
    NORMAL,
    LEAP
}

enum class ArmControlMode {
    EE_POSITION,
    JOINT_ANGLE
}

enum class LibraryTarget {
    POI_SESSION,
    FOI_SESSION
}

enum class ControlViewMode {
    WORLD_VIEW,    // Traditional view with chassis, arm, gimbal panels
    CAMERA_VIEW    // "See through lens" - EE position + gimbal only
}

enum class OperationMode {
    MANUAL,              // Direct teleop via on-screen buttons (default)
    MOTION_REPLICA,      // Map → POI → Prepare → Trajectory execution
    SUBJECT_FOLLOWING,   // Bounding box tracking + autonomous chase
    FIXED_POSITION       // Arm/gimbal only, chassis locked
}
