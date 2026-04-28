package com.recomo.user.ui.screens.viewer

import android.content.Context
import android.util.Log
import com.recomo.common.sceneviewer.SceneAssetRepository
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException
import java.net.ServerSocket

/**
 * Tiny HTTP server that makes the bundled spzviewer assets plus user-provided SPZ
 * and generated TUM files reachable by an external browser on the same device.
 *
 * Routes:
 * - GET /viewer.html — bundled viewer HTML
 * - GET /js/... — bundled js and assets from assets/spzviewer/
 * - GET /spz/entryId — the SPZ file for the registry entry with that id
 * - GET /tum/filename — a TUM file in [tumCacheDir]
 *
 * Bound to 127.0.0.1 only (loopback), on a random free port picked at start().
 * The caller is expected to call [start] before firing a browser intent and
 * [stop] when the app is backgrounded or the viewer is no longer needed.
 *
 * Not a long-running server — short-lived per SceneViewer session.
 */
class SceneViewerHttpServer(
    private val context: Context,
    private val repository: SceneAssetRepository,
    private val tumCacheDir: File
) {

    private var server: NanoHTTPD? = null
    private var boundPort: Int = -1

    /** `http://127.0.0.1:<port>` — valid only while the server is running. */
    fun baseUrl(): String? =
        if (boundPort > 0) "http://127.0.0.1:$boundPort" else null

    /** Start the server on a random free port. Idempotent — returns the base URL. */
    @Synchronized
    fun start(): String {
        server?.let { return baseUrl()!! }
        val port = pickFreePort()
        val instance = Impl(port)
        instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        server = instance
        boundPort = port
        Log.i(TAG, "SceneViewer HTTP server started on 127.0.0.1:$port")
        return baseUrl()!!
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        boundPort = -1
        Log.i(TAG, "SceneViewer HTTP server stopped")
    }

    private fun pickFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private inner class Impl(port: Int) : NanoHTTPD("127.0.0.1", port) {
        override fun serve(session: IHTTPSession): Response {
            val rawUri = session.uri?.trimStart('/') ?: ""
            return try {
                when {
                    rawUri.startsWith("spz/") -> serveSpz(rawUri.removePrefix("spz/"))
                    rawUri.startsWith("tum/") -> serveTum(rawUri.removePrefix("tum/"))
                    rawUri.isEmpty() || rawUri == "viewer.html" ->
                        serveBundled("spzviewer/viewer.html", "text/html")
                    else -> serveBundled("spzviewer/$rawUri", guessMime(rawUri))
                }
            } catch (error: Throwable) {
                Log.e(TAG, "serve failed for /$rawUri", error)
                notFound("server error: ${error.message ?: "unknown"}")
            }
        }

        private fun serveBundled(assetPath: String, mimeType: String): Response = try {
            // Read the whole asset into memory. Needed because:
            // 1. openFd() throws for compressed assets (HTML/JS are compressed by default)
            // 2. NanoHTTPD's chunked response without a Content-Length confuses some
            //    Android browsers (Edge buffers forever).
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            ).also {
                it.addHeader("Access-Control-Allow-Origin", "*")
            }
        } catch (error: IOException) {
            Log.w(TAG, "bundled asset not found: $assetPath (${error.message})")
            notFound("bundled asset not found: $assetPath")
        }

        private fun serveSpz(entryId: String): Response {
            val entry = repository.findById(entryId)
                ?: return notFound("no SPZ entry with id=$entryId")
            val file = File(entry.absolutePath)
            if (!file.isFile) return notFound("SPZ file missing on disk: ${entry.absolutePath}")
            return fileResponse(file, "application/octet-stream")
        }

        private fun serveTum(fileName: String): Response {
            val safeName = fileName.substringAfterLast('/') // strip any path traversal
            val file = File(tumCacheDir, safeName)
            if (!file.isFile) return notFound("no TUM file at ${file.absolutePath}")
            return fileResponse(file, "text/plain")
        }

        private fun fileResponse(file: File, mimeType: String): Response {
            val stream = file.inputStream()
            return newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                stream,
                file.length()
            ).also {
                it.addHeader("Access-Control-Allow-Origin", "*")
            }
        }

        private fun notFound(message: String): Response =
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", message)
    }

    private fun guessMime(path: String): String = when {
        path.endsWith(".html", ignoreCase = true) -> "text/html"
        path.endsWith(".js", ignoreCase = true) -> "application/javascript"
        path.endsWith(".css", ignoreCase = true) -> "text/css"
        path.endsWith(".json", ignoreCase = true) -> "application/json"
        path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        path.endsWith(".png", ignoreCase = true) -> "image/png"
        path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        path.endsWith(".spz", ignoreCase = true) ||
            path.endsWith(".splat", ignoreCase = true) ||
            path.endsWith(".ply", ignoreCase = true) -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    companion object {
        private const val TAG = "SceneViewerHttpServer"
    }
}
