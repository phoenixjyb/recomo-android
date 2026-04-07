package com.recomo.remotecontrol.v3dr.network

import com.recomo.remotecontrol.v3dr.data.model.SceneReconstruction
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service for v3dr_lake server
 * Backend API endpoints for 3D reconstruction service
 */
interface V3DRApiService {

    /**
     * List available 3D scenes
     * GET /api/scenes
     */
    @GET("api/v1/scenes")
    suspend fun listScenes(
        @Query("status") status: String? = null
    ): Response<ScenesResponse>

    /**
     * Create a reconstruction for a recording session
     * POST /api/v1/scenes
     */
    @POST("api/v1/scenes")
    suspend fun createScene(
        @Body payload: CreateSceneRequest
    ): Response<SceneDetailResponse>

    /**
     * Get scene details
     * GET /api/scenes/{sceneId}
     */
    @GET("api/v1/scenes/{sceneId}")
    suspend fun getScene(
        @Path("sceneId") sceneId: String
    ): Response<SceneDetailResponse>

    /**
     * Get scene by session ID
     * GET /api/v1/scenes/by-session/{sessionId}
     */
    @GET("api/v1/scenes/by-session/{sessionId}")
    suspend fun getSceneBySession(
        @Path("sessionId") sessionId: String
    ): Response<SceneDetailResponse>

    /**
     * Upload video for reconstruction
     * POST /api/upload
     */
    @Multipart
    @POST("api/v1/upload")
    suspend fun uploadVideo(
        @Part("session_id") sessionId: RequestBody,
        @Part video: MultipartBody.Part,
        @Part metadata: MultipartBody.Part
    ): Response<UploadResponse>

    /**
     * Download 3D model file
     * GET /api/scenes/{sceneId}/download
     */
    @Streaming
    @GET("api/v1/scenes/{sceneId}/download")
    suspend fun downloadAsset(
        @Path("sceneId") sceneId: String,
        @Query("asset") asset: String = "glb"
    ): Response<ResponseBody>

    /**
     * Delete scene
     * DELETE /api/scenes/{sceneId}
     */
    @DELETE("api/v1/scenes/{sceneId}")
    suspend fun deleteScene(
        @Path("sceneId") sceneId: String
    ): Response<DeleteResponse>

    /**
     * Health check
     * GET /api/health
     */
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}

/**
 * API Response models
 */
data class ScenesResponse(
    val scenes: List<ScenePayload>
)

data class SceneAsset(
    val path: String?,
    val size_bytes: Long?
)

data class ScenePayload(
    val scene_id: String,
    val session_id: String,
    val scene_name: String?,
    val method: String?,
    val status: String,
    val processing_stage: String?,
    val progress_percent: Int?,
    val created_at: String?,
    val processing_started_at: String?,
    val processing_completed_at: String?,
    val point_cloud: SceneAsset?,
    val mesh: SceneAsset?,
    val glb: SceneAsset?,
    val splat: SceneAsset?,
    val preview_video_path: String?
)

data class SceneDetailResponse(
    val scene: ScenePayload
)

data class CreateSceneRequest(
    val session_id: String,
    val method: String? = "colmap",
    val quality: String? = "draft",
    val scene_name: String? = null
)

data class VideoMetadata(
    val duration: Double,
    val frame_count: Int,
    val resolution: String,
    val fps: Double,
    val codec: String
)

data class UploadResponse(
    val success: Boolean,
    val scene_id: String?,
    val message: String
)

data class DeleteResponse(
    val success: Boolean,
    val message: String
)

data class HealthResponse(
    val status: String,
    val version: String?,
    val timestamp: Long
)

/**
 * Extension to convert DTO to domain model
 */
fun ScenePayload.toDomain(baseUrl: String = ""): SceneReconstruction {
    fun combine(path: String?): String? = path?.let { if (it.startsWith("http")) it else baseUrl.trimEnd('/') + "/" + it.trimStart('/') }
    fun parseMillis(iso: String?): Long? = iso?.let {
        try {
            java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
    return SceneReconstruction(
        sceneId = scene_id,
        sessionId = session_id,
        status = when (status.lowercase()) {
            "pending" -> com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus.PENDING
            "processing" -> com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus.PROCESSING
            "completed" -> com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus.COMPLETED
            "failed" -> com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus.FAILED
            else -> com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus.PENDING
        },
        pointCloudUrl = combine(point_cloud?.path),
        meshUrl = combine(mesh?.path),
        glbUrl = combine(glb?.path),
        splatUrl = combine(splat?.path),
        previewUrl = combine(preview_video_path),
        thumbnailUrl = null,
        fileSizeBytes = 0,
        createdAtIso = created_at,
        completedAtIso = processing_completed_at,
        createdAtMillis = parseMillis(created_at),
        completedAtMillis = parseMillis(processing_completed_at),
        progressPercent = progress_percent ?: 0,
        errorMessage = null,
        localCachePath = null
    )
}
