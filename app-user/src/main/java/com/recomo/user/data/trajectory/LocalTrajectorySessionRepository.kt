package com.recomo.user.data.trajectory

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalTrajectorySessionRepository(
    private val context: Context,
    private val assetsRoot: String = DEFAULT_ASSETS_ROOT,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {
    suspend fun listSessions(filesystemRootPath: String? = null): List<LocalTrajectorySessionSummary> = withContext(Dispatchers.IO) {
        loadLibrary(filesystemRootPath).sessions
    }

    suspend fun saveSessionFile(
        rawJson: JsonObject,
        sessionId: String,
        filesystemRootPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmedId = sessionId.trim()
        val trimmedRoot = filesystemRootPath.trim()
        if (trimmedId.isEmpty() || trimmedRoot.isEmpty()) return@withContext false
        runCatching {
            val rootDir = File(trimmedRoot).apply { if (!exists()) mkdirs() }
            if (!rootDir.isDirectory) return@runCatching false
            val file = File(rootDir, "$trimmedId.json")
            file.writeText(json.encodeToString(JsonObject.serializer(), rawJson))
            true
        }.getOrDefault(false)
    }

    suspend fun loadSession(sessionId: String, filesystemRootPath: String? = null): LocalTrajectorySessionDetail? = withContext(Dispatchers.IO) {
        loadLibrary(filesystemRootPath).details[sessionId.trim()]
    }

    suspend fun loadLibrary(filesystemRootPath: String? = null): LocalTrajectorySessionLibrary = withContext(Dispatchers.IO) {
        val sessionsById = linkedMapOf<String, LocalTrajectorySessionSummary>()
        val detailsById = linkedMapOf<String, LocalTrajectorySessionDetail>()

        readAssetsLibrary().forEach { entry ->
            sessionsById[entry.summary.sessionId] = entry.summary
            detailsById[entry.detail.sessionId] = entry.detail
        }

        // Studio Dance sessions bundled in assets/studio_dance/
        readAssetsLibraryFrom(STUDIO_DANCE_ASSETS_ROOT).forEach { entry ->
            sessionsById[entry.summary.sessionId] = entry.summary
            detailsById[entry.detail.sessionId] = entry.detail
        }

        readFilesystemLibrary(filesystemRootPath).forEach { entry ->
            sessionsById[entry.summary.sessionId] = entry.summary
            detailsById[entry.detail.sessionId] = entry.detail
        }

        LocalTrajectorySessionLibrary(
            sessions = sessionsById.values.toList(),
            details = detailsById
        )
    }

    private fun readAssetsLibrary(): List<LoadedTrajectorySession> =
        readAssetsLibraryFrom(assetsRoot)

    private fun readAssetsLibraryFrom(root: String): List<LoadedTrajectorySession> {
        val indexText = readAssetText("$root/index.json")
        return when {
            indexText != null -> parseIndexedSessions(
                indexText = indexText,
                source = LocalTrajectorySessionSource(
                    kind = LocalTrajectorySessionSourceKind.Assets,
                    rootPath = root
                ),
                readDetail = { relativePath ->
                    readAssetText("$root/$relativePath")
                }
            )
            else -> scanAssetSessionsFrom(root)
        }
    }

    private fun scanAssetSessions(): List<LoadedTrajectorySession> =
        scanAssetSessionsFrom(assetsRoot)

    private fun scanAssetSessionsFrom(root: String): List<LoadedTrajectorySession> {
        val source = LocalTrajectorySessionSource(
            kind = LocalTrajectorySessionSourceKind.Assets,
            rootPath = root
        )
        val names = runCatching { context.assets.list(root)?.toList().orEmpty() }.getOrDefault(emptyList())
        return names
            .asSequence()
            .filter { it.endsWith(".json", ignoreCase = true) && !it.equals("index.json", ignoreCase = true) }
            .mapNotNull { fileName ->
                val text = readAssetText("$root/$fileName") ?: return@mapNotNull null
                parseSessionFile(text, source)
            }
            .toList()
    }

    private fun readFilesystemLibrary(filesystemRootPath: String?): List<LoadedTrajectorySession> {
        val root = filesystemRootPath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isDirectory }
            ?: return emptyList()
        val roots = buildList {
            add(root)
            root.resolve("sample_sessions").takeIf { it.exists() && it.isDirectory }?.let { add(it) }
        }
        val loaded = linkedMapOf<String, LoadedTrajectorySession>()
        for (currentRoot in roots) {
            val source = LocalTrajectorySessionSource(
                kind = LocalTrajectorySessionSourceKind.Filesystem,
                rootPath = currentRoot.absolutePath
            )
            val indexFile = File(currentRoot, "index.json")
            if (indexFile.isFile) {
                val indexText = indexFile.readTextSafely() ?: continue
                parseIndexedSessions(
                    indexText = indexText,
                    source = source,
                    readDetail = { relativePath ->
                        readFileText(resolveRelativeFile(currentRoot, relativePath))
                    }
                ).forEach { loaded[it.summary.sessionId] = it }
                continue
            }

            currentRoot.walkTopDown()
                .filter { it.isFile && it.extension.equals("json", ignoreCase = true) && !it.name.equals("index.json", ignoreCase = true) }
                .forEach { file ->
                    val text = file.readTextSafely() ?: return@forEach
                    parseSessionFile(text, source)?.let { loaded[it.summary.sessionId] = it }
                }
        }
        return loaded.values.toList()
    }

    private fun parseIndexedSessions(
        indexText: String,
        source: LocalTrajectorySessionSource,
        readDetail: (String) -> String?
    ): List<LoadedTrajectorySession> {
        val entries = runCatching { json.parseToJsonElement(indexText).jsonArray }
            .getOrElse { return emptyList() }
        return entries.mapNotNull { element ->
            runCatching {
                val obj = element as? JsonObject ?: return@runCatching null
                val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val fileName = obj["file"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (sessionId.isBlank() || fileName.isBlank()) return@runCatching null
                val summary = parseSummary(obj, source)
                val detailText = readDetail(fileName) ?: return@runCatching null
                val detail = parseSessionDetail(detailText, source, fallbackSummary = summary)
                    ?: return@runCatching null
                LoadedTrajectorySession(summary = summary, detail = detail)
            }.getOrNull()
        }
    }

    private fun parseSessionFile(
        text: String,
        source: LocalTrajectorySessionSource
    ): LoadedTrajectorySession? {
        val detail = runCatching {
            parseSessionDetail(text, source, fallbackSummary = null)
        }.getOrNull() ?: return null
        val summary = detail.toSummary()
        return LoadedTrajectorySession(summary = summary, detail = detail)
    }

    private fun parseSummary(
        obj: JsonObject,
        source: LocalTrajectorySessionSource
    ): LocalTrajectorySessionSummary {
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val sessionName = obj["session_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return LocalTrajectorySessionSummary(
            sessionId = sessionId,
            sessionName = sessionName,
            robotName = obj["robot_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            frameId = obj["frame_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            category = obj["category"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            count = obj["foi_count"]?.jsonPrimitive?.intOrNull ?: 0,
            source = source,
            sessionType = obj["type"]?.jsonPrimitive?.contentOrNull?.trim(),
            bpm = obj["bpm"]?.jsonPrimitive?.intOrNull,
            musicFile = obj["music_file"]?.jsonPrimitive?.contentOrNull?.trim()
        )
    }

    private fun parseSessionDetail(
        text: String,
        source: LocalTrajectorySessionSource,
        fallbackSummary: LocalTrajectorySessionSummary?
    ): LocalTrajectorySessionDetail? {
        val session = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val sessionId = session["session_id"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: fallbackSummary?.sessionId
            ?: return null
        val sessionName = session["session_name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.ifBlank { null }
            ?: fallbackSummary?.sessionName
            ?: ""
        val robotName = session["robot_name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: fallbackSummary?.robotName
            ?: ""
        val frameId = session["frame_id"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: fallbackSummary?.frameId
            ?: ""
        val category = session["category"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: fallbackSummary?.category
            ?: ""
        val sessionType = session["type"]?.jsonPrimitive?.contentOrNull?.trim()
        val timestampMs = session["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val frames = parseFrames(session["fois"] ?: session["frames"])
        val musicFile = session["music_file"]?.jsonPrimitive?.contentOrNull?.trim()
        val musicOffsetMs = session["music_offset_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val bpm = session["bpm"]?.jsonPrimitive?.intOrNull
        return LocalTrajectorySessionDetail(
            sessionId = sessionId,
            sessionName = sessionName,
            robotName = robotName,
            frameId = frameId,
            category = category,
            sessionType = sessionType,
            timestampMs = timestampMs,
            frames = frames,
            source = source,
            musicFile = musicFile,
            musicOffsetMs = musicOffsetMs,
            bpm = bpm
        )
    }

    private fun parseFrames(element: JsonElement?): List<LocalTrajectoryFrameRecord> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { frameElement ->
            val obj = frameElement as? JsonObject ?: return@mapNotNull null
            val poi = obj["poi"]?.jsonObject
            val basePose = poi?.get("base_pose")?.jsonObject
            LocalTrajectoryFrameRecord(
                name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                sequenceIndex = obj["sequence_index"]?.jsonPrimitive?.intOrNull ?: 0,
                sessionRole = obj["session_role"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                baseX = poi?.get("x")?.jsonPrimitive?.doubleOrNull
                    ?: basePose?.get("x")?.jsonPrimitive?.doubleOrNull
                    ?: 0.0,
                baseY = poi?.get("y")?.jsonPrimitive?.doubleOrNull
                    ?: basePose?.get("y")?.jsonPrimitive?.doubleOrNull
                    ?: 0.0,
                baseYaw = poi?.get("yaw")?.jsonPrimitive?.doubleOrNull
                    ?: basePose?.get("yaw")?.jsonPrimitive?.doubleOrNull
                    ?: 0.0,
                armQ = obj["arm_q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList(),
                gimbalQ = obj["gimbal_q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList(),
                timestampMs = obj["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull,
                dwellS = obj["dwell_s"]?.jsonPrimitive?.doubleOrNull,
                transitionS = obj["transition_s"]?.jsonPrimitive?.doubleOrNull,
                ease = obj["ease"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private fun LocalTrajectorySessionDetail.toSummary(): LocalTrajectorySessionSummary {
        return LocalTrajectorySessionSummary(
            sessionId = sessionId,
            sessionName = sessionName,
            robotName = robotName,
            frameId = frameId,
            category = category,
            count = frames.size,
            source = source,
            sessionType = sessionType,
            bpm = bpm,
            musicFile = musicFile
        )
    }

    private fun resolveRelativeFile(root: File, relativePath: String): File {
        val normalized = relativePath.trim().removePrefix("./")
        val direct = File(root, normalized)
        if (direct.isFile) return direct
        val nested = File(root, "sample_sessions/$normalized")
        if (nested.isFile) return nested
        return direct
    }

    private fun readAssetText(path: String): String? {
        return runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun readFileText(file: File): String? {
        return runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    private fun File.readTextSafely(): String? = readFileText(this)

    private data class LoadedTrajectorySession(
        val summary: LocalTrajectorySessionSummary,
        val detail: LocalTrajectorySessionDetail
    )

    companion object {
        const val DEFAULT_ASSETS_ROOT = "sample_sessions"
        const val STUDIO_DANCE_ASSETS_ROOT = "studio_dance"
    }
}
