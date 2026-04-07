package com.recomo.remotecontrol.v3dr.vio

import java.io.File

interface VioBackend {
    val id: String
    val displayName: String
    val version: String

    fun isAvailable(): Boolean

    suspend fun checkDependencies(): VioDepsResult

    suspend fun run(
        session: VioSession,
        syncPackets: List<SyncPacket>,
        outputDir: File
    ): VioResult
}
