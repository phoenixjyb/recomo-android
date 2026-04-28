package com.recomo.common.sceneviewer

/**
 * In-memory representation of a discovered SPZ scene file and its metadata.
 *
 * Built by [SceneAssetRepository] from an `.spz` file + optional `<name>.spz.meta.json`
 * sidecar. Not persisted directly — persistence goes through [SpzRegistrySidecar].
 */
data class SpzRegistryEntry(
    /** Stable identifier derived from the absolute path (SHA-256, first 16 chars). */
    val id: String,
    /** Display name, typically the file name without the `.spz` extension. */
    val displayName: String,
    /** Absolute path to the `.spz` file on device storage. */
    val absolutePath: String,
    /** File size in bytes. */
    val fileSize: Long,
    /** Default anchor pose applied when no per-trajectory override is set. */
    val defaultAnchor: AnchorPose,
    /** Per-trajectory anchor overrides, keyed by trajectory id. */
    val trajectoryAnchors: Map<String, AnchorPose>,
    /** Session / preset ids this scene is associated with (best-match hints). */
    val associatedSessions: List<String>
) {
    /**
     * Resolve the effective anchor for a given trajectory id, falling back to
     * [defaultAnchor] if no override exists.
     */
    fun anchorFor(trajectoryId: String?): AnchorPose =
        trajectoryId?.let { trajectoryAnchors[it] } ?: defaultAnchor
}
