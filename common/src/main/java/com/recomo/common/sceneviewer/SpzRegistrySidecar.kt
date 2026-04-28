package com.recomo.common.sceneviewer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk sidecar schema for SPZ metadata. Lives next to the `.spz` file as
 * `<name>.spz.meta.json`.
 *
 * Kept separate from [SpzRegistryEntry] so that the in-memory representation can
 * evolve independently of the persisted schema (which needs a stable version).
 */
@Serializable
data class SpzRegistrySidecar(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val defaultAnchor: AnchorPose = AnchorPose.IDENTITY,
    val trajectoryAnchors: Map<String, AnchorPose> = emptyMap(),
    val associatedSessions: List<String> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /** JSON codec — pretty printed for human editing, lenient on unknown keys. */
        val JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun empty(): SpzRegistrySidecar = SpzRegistrySidecar()

        fun parse(text: String): Result<SpzRegistrySidecar> = runCatching {
            JSON.decodeFromString(serializer(), text)
        }
    }

    fun serialize(): String = JSON.encodeToString(serializer(), this)
}

/**
 * Resolve the sidecar filename for a given SPZ file path.
 * e.g. `T8-lobby.spz` → `T8-lobby.spz.meta.json`
 */
fun sidecarFileNameFor(spzFileName: String): String = "$spzFileName.meta.json"
