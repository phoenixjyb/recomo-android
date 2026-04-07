package com.recomo.user.data.postrecord

import java.io.File

data class UserPostRecordItem(
    val id: String,
    val file: File,
    val recordedAtMs: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val codecLabel: String?,
    val fileSizeBytes: Long
)
