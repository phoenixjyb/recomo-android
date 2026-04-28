package com.recomo.user.ui.screens.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.recomo.common.sceneviewer.AnchorPose
import com.recomo.common.sceneviewer.SpzRegistryEntry
import com.recomo.common.sceneviewer.SpzRegistrySidecar
import com.recomo.common.sceneviewer.TrajectoryExporter
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/**
 * Builds a `http://127.0.0.1:<port>/viewer.html?...` URL for a given launch request
 * and fires a browser intent to open it. The [SceneViewerHttpServer] must be running
 * before [launch] is called.
 *
 * - Converts inline-JSON trajectories to TUM via [TrajectoryExporter], writing to
 *   the server's cache directory so the URL can reference `/tum/<file>`.
 * - Leaves the app in the background — the user switches back via recents / back.
 * - Does not cover all legacy [SceneTrajectorySource] variants (e.g. SessionReference
 *   with no inline body); those show a toast for now and skip the trajectory param.
 */
object SceneViewerLauncher {

    private const val TAG = "SceneViewerLauncher"

    fun launch(
        context: Context,
        request: SceneViewerLaunchRequest,
        selectedEntry: SpzRegistryEntry?,
        anchorOverride: AnchorPose?,
        server: SceneViewerHttpServer,
        tumCacheDir: File
    ): Boolean {
        val baseUrl = server.baseUrl()
            ?: run {
                toast(context, "Scene viewer server not running")
                return false
            }

        val sceneUrl = buildSceneUrl(baseUrl, selectedEntry, request.sceneSource)
        val anchor = anchorOverride
            ?: selectedEntry?.anchorFor(trajectoryIdFromRequest(request))
            ?: AnchorPose.IDENTITY
        // The viewer applies the anchor via its own loadAnchorFromTUMUrl pipeline
        // (which feeds into applyAnchorPose + recalculateTrajectoryWithAnchor).
        // We therefore export the trajectory in its ORIGINAL frame — not pre-transformed.
        val trajectoryUrl = buildTrajectoryUrl(context, baseUrl, request, tumCacheDir)
        val anchorTumUrl = buildAnchorTumUrl(context, baseUrl, request, anchor, tumCacheDir)

        if (sceneUrl == null && trajectoryUrl == null) {
            toast(context, "No scene or trajectory to show")
            return false
        }

        // Cloud-authored TUMs are in a world-y-up frame (CineDATA rescales
        // to head-height bounds), so the viewer's default rdf→babylon y-flip
        // inverts their up direction. Signal the convention on the URL so
        // the viewer skips the flip for these trajectories.
        val frameHint = when (request.entrySource) {
            SceneViewerEntrySource.AiChat -> "y_up_world"
            else -> null
        }

        val viewerUrl = buildViewerUrl(
            baseUrl,
            request.title,
            sceneUrl,
            trajectoryUrl,
            anchor,
            anchorTumUrl,
            frameHint
        )
        Log.i(TAG, "launching browser with URL: $viewerUrl")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewerUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "No browser available to handle scene viewer intent", error)
            toast(context, "No browser installed to open the scene viewer")
            false
        }
    }

    /**
     * Resolve the scene asset URL. Priority:
     * 1. [selectedEntry] (user picked or best-match from registry) → `/spz/<id>`
     * 2. Legacy sceneSource on the request (AppAsset / LocalFile / RemoteUrl) — passed
     *    through as-is to the viewer, which may or may not be able to load it
     */
    private fun buildSceneUrl(
        baseUrl: String,
        selectedEntry: SpzRegistryEntry?,
        fallbackSource: SceneAssetSource?
    ): String? {
        if (selectedEntry != null) {
            return "$baseUrl/spz/${selectedEntry.id}"
        }
        return when (fallbackSource) {
            is SceneAssetSource.RemoteUrl -> fallbackSource.url
            is SceneAssetSource.LocalFile -> "file://${fallbackSource.absolutePath}"
            is SceneAssetSource.AppAsset -> "$baseUrl/${fallbackSource.relativePath.removePrefix("/")}"
            null -> null
        }
    }

    /**
     * Export an inline-JSON / inline-TUM trajectory to a TUM file in the cache dir
     * and return the `/tum/<file>` URL. The trajectory is exported in its original
     * recording frame — the anchor transform is applied separately by the viewer
     * via [buildAnchorTumUrl]. Returns null for unsupported trajectory source types.
     */
    private fun buildTrajectoryUrl(
        context: Context,
        baseUrl: String,
        request: SceneViewerLaunchRequest,
        tumCacheDir: File
    ): String? {
        val source = request.trajectorySource ?: return null
        val fileName = safeCacheFileName(request, ".tum")
        val outFile = File(tumCacheDir, fileName)

        return runCatching {
            when (source) {
                is SceneTrajectorySource.InlineJson -> {
                    // Export with identity anchor — the scene-aligned anchor comes
                    // via the separate anchor TUM sidecar.
                    TrajectoryExporter.convertJsonToTumFile(source.json, AnchorPose.IDENTITY, outFile)
                }
                is SceneTrajectorySource.InlineTum -> {
                    outFile.parentFile?.mkdirs()
                    outFile.writeText(source.tumText)
                }
                is SceneTrajectorySource.LocalFile -> {
                    val src = File(source.absolutePath)
                    if (src.isFile) {
                        outFile.parentFile?.mkdirs()
                        src.copyTo(outFile, overwrite = true)
                    } else {
                        return@runCatching null
                    }
                }
                is SceneTrajectorySource.AppAssetTum -> {
                    outFile.parentFile?.mkdirs()
                    context.assets.open(source.relativePath).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                is SceneTrajectorySource.SessionReference,
                is SceneTrajectorySource.TrajectoryReference -> {
                    // No inline body available — nothing we can serve.
                    return@runCatching null
                }
            }
            "$baseUrl/tum/$fileName"
        }.getOrElse { error ->
            Log.e(TAG, "failed to prepare trajectory", error)
            null
        }
    }

    /**
     * Write a single-line anchor TUM file and return its `/tum/<file>` URL.
     *
     * Priority:
     *   1. If the request carries `anchorTumAssetPath`, copy the bundled asset
     *      bytes into the cache dir verbatim (CopyStyle presets with a POI in
     *      the trajectory JSON — the scene-aligned anchor lives in that file).
     *   2. Otherwise, synthesize the TUM from the in-memory [anchor] pose.
     *
     * The viewer's `loadAnchorFromTUMUrl` fetches the result and feeds it
     * through `applyAnchorPose` — same code path the manual anchor-file
     * picker uses.
     */
    private fun buildAnchorTumUrl(
        context: Context,
        baseUrl: String,
        request: SceneViewerLaunchRequest,
        anchor: AnchorPose,
        tumCacheDir: File
    ): String? {
        val fileName = safeCacheFileName(request, "_anchor.tum")
        val outFile = File(tumCacheDir, fileName)
        val assetPath = request.anchorTumAssetPath
        return runCatching {
            outFile.parentFile?.mkdirs()
            if (assetPath != null) {
                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                val line = String.format(
                    Locale.US,
                    "%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f\n",
                    0.0,
                    anchor.x, anchor.y, anchor.z,
                    anchor.qx, anchor.qy, anchor.qz, anchor.qw
                )
                outFile.writeText(line)
            }
            "$baseUrl/tum/$fileName"
        }.getOrElse { error ->
            Log.e(TAG, "failed to write anchor TUM", error)
            null
        }
    }

    private fun safeCacheFileName(request: SceneViewerLaunchRequest, suffix: String): String {
        val key = request.sessionId ?: request.title.ifBlank { "trajectory" }
        val safeKey = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$safeKey$suffix"
    }

    private fun buildViewerUrl(
        baseUrl: String,
        title: String?,
        sceneUrl: String?,
        trajectoryUrl: String?,
        anchor: AnchorPose,
        anchorTumUrl: String?,
        frameHint: String?
    ): String {
        val params = mutableListOf<String>()
        sceneUrl?.let { params += "scene=${encode(it)}" }
        trajectoryUrl?.let { params += "trajectory=${encode(it)}" }
        anchorTumUrl?.let { params += "anchorTum=${encode(it)}" }
        title?.takeIf { it.isNotBlank() }?.let { params += "title=${encode(it)}" }
        val anchorJson = SpzRegistrySidecar.JSON.encodeToString(AnchorPose.serializer(), anchor)
        params += "anchor=${encode(anchorJson)}"
        frameHint?.let { params += "frame=${encode(it)}" }
        return "$baseUrl/viewer.html?" + params.joinToString("&")
    }

    private fun trajectoryIdFromRequest(request: SceneViewerLaunchRequest): String? {
        val source = request.trajectorySource ?: return request.sessionId
        return when (source) {
            is SceneTrajectorySource.SessionReference -> source.sessionId
            is SceneTrajectorySource.TrajectoryReference -> source.trajectoryId
            is SceneTrajectorySource.InlineJson,
            is SceneTrajectorySource.InlineTum,
            is SceneTrajectorySource.LocalFile,
            is SceneTrajectorySource.AppAssetTum -> request.sessionId
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
