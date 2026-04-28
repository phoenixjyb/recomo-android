package com.recomo.common.sceneviewer

import java.io.File

/**
 * Reads a single pose from a TUM-format file.
 *
 * TUM line format: `timestamp x y z qx qy qz qw`. Blank lines and `#` comments
 * are skipped. Only the first valid data line is used — these anchor files
 * conventionally contain one line representing the scene-alignment pose.
 *
 * Used by [SceneAssetRepository] to auto-load a shared `default_anchor.tum`
 * or a per-scene `<name>.anchor.tum` sidecar from the user's scenes folder,
 * so users don't have to hand-pick an anchor every time they load a scene.
 */
object AnchorTumParser {

    const val DEFAULT_ANCHOR_FILENAME = "default_anchor.tum"

    /**
     * Parse the first valid TUM data line from [file] into an [AnchorPose].
     * Returns null if the file doesn't exist, is empty, or has a malformed
     * first line — callers should fall back to the next anchor source.
     */
    fun readAnchorFile(file: File): AnchorPose? {
        if (!file.isFile || !file.canRead()) return null
        return runCatching {
            file.useLines { lines ->
                for (rawLine in lines) {
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 8) continue
                    val values = parts.take(8).map { it.toDouble() }
                    return@useLines AnchorPose(
                        x = values[1],
                        y = values[2],
                        z = values[3],
                        qx = values[4],
                        qy = values[5],
                        qz = values[6],
                        qw = values[7]
                    )
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Resolve the effective default anchor for an SPZ file by checking, in
     * priority order:
     *   1. `<scene-name>.anchor.tum` next to the SPZ
     *   2. `<folder>/default_anchor.tum` (shared)
     *   3. null — caller should use sidecar metadata or identity
     */
    fun resolveFolderAnchor(spzFile: File): AnchorPose? {
        val folder = spzFile.parentFile ?: return null
        val perScene = File(folder, "${spzFile.nameWithoutExtension}.anchor.tum")
        readAnchorFile(perScene)?.let { return it }
        val shared = File(folder, DEFAULT_ANCHOR_FILENAME)
        return readAnchorFile(shared)
    }
}
