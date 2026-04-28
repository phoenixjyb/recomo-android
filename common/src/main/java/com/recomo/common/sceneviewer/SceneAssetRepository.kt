package com.recomo.common.sceneviewer

import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scans a user-configured folder for `.spz` files, loads their `<name>.spz.meta.json`
 * sidecars (if present), and caches the resulting registry in memory.
 *
 * Callers set the active folder via [setScenesFolder] and drive rescans via [refresh].
 * The current list of entries is exposed as [entries] — a hot [StateFlow] suitable
 * for Compose collection.
 *
 * Anchor updates are written back through [updateAnchor], which rewrites the sidecar
 * on disk and refreshes the in-memory entry.
 *
 * This repository does not scan recursively — only the top level of the configured
 * folder. It also does not watch the folder; callers must call [refresh] explicitly.
 */
class SceneAssetRepository {

    private val _entries = MutableStateFlow<List<SpzRegistryEntry>>(emptyList())
    val entries: StateFlow<List<SpzRegistryEntry>> = _entries.asStateFlow()

    private var currentFolder: File? = null

    /**
     * Set the folder to scan for SPZ files and immediately rescan. Passing `null`
     * clears the registry.
     */
    fun setScenesFolder(folder: File?) {
        currentFolder = folder
        refresh()
    }

    fun currentFolder(): File? = currentFolder

    /** Rescan the currently-configured folder and rebuild the entry list. */
    fun refresh() {
        val folder = currentFolder
        if (folder == null || !folder.isDirectory) {
            _entries.value = emptyList()
            return
        }
        val spzFiles = folder.listFiles { f -> f.isFile && f.name.endsWith(".spz", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        _entries.value = spzFiles.mapNotNull { buildEntry(it) }
    }

    fun findById(id: String): SpzRegistryEntry? = _entries.value.firstOrNull { it.id == id }

    /**
     * Return the entry whose sidecar lists [sessionId] in `associatedSessions`,
     * or null if no match. If multiple match, the first (alphabetical) wins.
     */
    fun findBestMatchForSession(sessionId: String): SpzRegistryEntry? =
        _entries.value.firstOrNull { sessionId in it.associatedSessions }

    /**
     * Persist an anchor change for an entry. If [trajectoryId] is null, the
     * update is written to `defaultAnchor`; otherwise it's written to
     * `trajectoryAnchors[trajectoryId]`.
     *
     * Returns the updated entry, or null if the entry id is not in the registry.
     */
    fun updateAnchor(entryId: String, trajectoryId: String?, anchor: AnchorPose): SpzRegistryEntry? {
        val existing = findById(entryId) ?: return null
        val spzFile = File(existing.absolutePath)
        if (!spzFile.isFile) {
            Log.w(TAG, "updateAnchor: SPZ file no longer exists at ${existing.absolutePath}")
            return null
        }

        val sidecar = loadSidecar(spzFile) ?: SpzRegistrySidecar.empty()
        val newSidecar = if (trajectoryId == null) {
            sidecar.copy(defaultAnchor = anchor)
        } else {
            sidecar.copy(trajectoryAnchors = sidecar.trajectoryAnchors + (trajectoryId to anchor))
        }

        val sidecarFile = File(spzFile.parentFile, sidecarFileNameFor(spzFile.name))
        return runCatching {
            sidecarFile.writeText(newSidecar.serialize())
            val refreshed = existing.copy(
                defaultAnchor = newSidecar.defaultAnchor,
                trajectoryAnchors = newSidecar.trajectoryAnchors,
                associatedSessions = newSidecar.associatedSessions
            )
            _entries.value = _entries.value.map { if (it.id == entryId) refreshed else it }
            refreshed
        }.getOrElse { error ->
            Log.e(TAG, "updateAnchor: failed to write sidecar for $entryId", error)
            null
        }
    }

    private fun buildEntry(spzFile: File): SpzRegistryEntry? {
        val sidecar = loadSidecar(spzFile)
        val id = stableIdFor(spzFile.absolutePath)

        // Default anchor priority:
        //   1. Sidecar JSON defaultAnchor (if the user has explicitly saved one)
        //   2. Companion TUM file (<name>.anchor.tum or default_anchor.tum)
        //   3. AnchorPose.IDENTITY
        val sidecarHasExplicitAnchor = sidecar != null && sidecar.defaultAnchor != AnchorPose.IDENTITY
        val defaultAnchor: AnchorPose = when {
            sidecarHasExplicitAnchor -> sidecar!!.defaultAnchor
            else -> AnchorTumParser.resolveFolderAnchor(spzFile) ?: AnchorPose.IDENTITY
        }

        return SpzRegistryEntry(
            id = id,
            displayName = spzFile.nameWithoutExtension,
            absolutePath = spzFile.absolutePath,
            fileSize = spzFile.length(),
            defaultAnchor = defaultAnchor,
            trajectoryAnchors = sidecar?.trajectoryAnchors ?: emptyMap(),
            associatedSessions = sidecar?.associatedSessions ?: emptyList()
        )
    }

    private fun loadSidecar(spzFile: File): SpzRegistrySidecar? {
        val sidecarFile = File(spzFile.parentFile, sidecarFileNameFor(spzFile.name))
        if (!sidecarFile.isFile) return null
        return runCatching {
            SpzRegistrySidecar.parse(sidecarFile.readText()).getOrNull()
        }.getOrNull()
    }

    private fun stableIdFor(absolutePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(absolutePath.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    companion object {
        private const val TAG = "SceneAssetRepository"
    }
}
