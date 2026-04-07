package com.recomo.remotecontrol.preview

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.FilamentInstance
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import com.recomo.remotecontrol.preview.urdf.Transform
import com.recomo.remotecontrol.preview.urdf.UrdfCache
import com.recomo.remotecontrol.preview.urdf.Vec3
import java.nio.ByteBuffer

class FilamentPreviewRenderer(private val context: Context) {
    companion object {
        private const val TAG = "FilamentPreviewRenderer"

        /** Distance (meters) — avatar stands in front of the robot, face to face */
        private const val AVATAR_DISTANCE = 2.5

        /** Avatar GLB asset path (RPM medium-quality, A-pose, ~1.7m native height) */
        private const val AVATAR_ASSET_PATH = "avatars/default_avatar.glb"

        init {
            Utils.init()
        }
    }

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0
    private var lightEntity: Int = 0
    private var indirectLight: IndirectLight? = null

    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var swapChain: SwapChain? = null

    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var cubeAsset: FilamentAsset? = null
    private var avatarAsset: FilamentAsset? = null
    private var avatarVisible = true

    // User-applied offsets for interactive object positioning
    private var robotOffsetX = 0.0
    private var robotOffsetY = 0.0
    private var robotYawOffset = 0.0
    private var avatarOffsetX = 0.0
    private var avatarOffsetY = 0.0
    private var avatarYawOffset = 0.0

    // Tracked world positions for hit testing
    private var robotWorldX = 0.0
    private var robotWorldY = 0.0
    private var avatarWorldX = 0.0
    private var avatarWorldY = 0.0

    private var surfaceView: SurfaceView? = null
    private var choreographer: Choreographer? = null
    private var frameCallback: Choreographer.FrameCallback? = null

    private var preview: TrajectoryPreview? = null
    private var previewConfig: RobotKinematicsConfig? = null
    private var previewModel: com.recomo.remotecontrol.preview.urdf.UrdfModel? = null
    private var currentIndex = 0

    private val baseInstances = mutableListOf<FilamentInstance>()
    private val chainInstances = mutableListOf<FilamentInstance>()
    private val cameraInstances = mutableListOf<FilamentInstance>()
    private val basePathInstances = mutableListOf<FilamentInstance>()
    private val eePathInstances = mutableListOf<FilamentInstance>()
    private val meshAssets = mutableMapOf<String, FilamentAsset>()
    private var meshProfileKey: String? = null
    private val keyframeInstances = mutableListOf<FilamentInstance>()
    private var autoFitPending = false

    private var orbitYaw = 0.6
    private var orbitPitch = 0.5
    private var orbitDistance = 3.5
    private var panX = 0.0
    private var panY = 0.0

    fun initialize(surface: SurfaceView) {
        if (engine != null) return
        surfaceView = surface
        engine = Engine.create()
        val engine = engine ?: return
        renderer = engine.createRenderer()
        renderer?.setClearOptions(
            Renderer.ClearOptions().apply {
                clear = true
                clearColor = floatArrayOf(0.06f, 0.06f, 0.08f, 1.0f)
            }
        )
        scene = engine.createScene()
        view = engine.createView()
        view?.scene = scene
        view?.apply {
            isPostProcessingEnabled = true
            antiAliasing = View.AntiAliasing.FXAA
            viewport = Viewport(0, 0, surface.width, surface.height)
        }

        cameraEntity = EntityManager.get().create()
        camera = engine?.createCamera(cameraEntity)
        view?.camera = camera

        assetLoader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        displayHelper = DisplayHelper(context)
        setupLighting(engine)

        attachSurface(surface)
        loadCubeAsset()
        loadAvatarAsset()
        applyPreviewIfReady()
        startRendering()
    }

    fun destroy() {
        stopRendering()
        baseInstances.clear()
        chainInstances.clear()
        cameraInstances.clear()
        basePathInstances.forEach { scene?.removeEntities(it.entities) }
        eePathInstances.forEach { scene?.removeEntities(it.entities) }
        keyframeInstances.forEach { scene?.removeEntities(it.entities) }
        basePathInstances.clear()
        eePathInstances.clear()
        keyframeInstances.clear()
        meshAssets.values.forEach { asset ->
            scene?.removeEntities(asset.entities)
            assetLoader?.destroyAsset(asset)
        }
        meshAssets.clear()
        meshProfileKey = null
        avatarAsset?.let { asset ->
            scene?.removeEntities(asset.entities)
            assetLoader?.destroyAsset(asset)
        }
        avatarAsset = null
        cubeAsset?.let { asset ->
            scene?.removeEntities(asset.entities)
            assetLoader?.destroyAsset(asset)
        }
        cubeAsset = null
        assetLoader?.destroy()
        resourceLoader?.destroy()
        uiHelper?.detach()
        swapChain?.let { sc -> engine?.destroySwapChain(sc) }
        camera?.let { engine?.destroyCameraComponent(cameraEntity) }
        if (lightEntity != 0) {
            engine?.destroyEntity(lightEntity)
            lightEntity = 0
        }
        indirectLight?.let { engine?.destroyIndirectLight(it) }
        indirectLight = null
        view?.let { engine?.destroyView(it) }
        scene?.let { engine?.destroyScene(it) }
        renderer?.let { engine?.destroyRenderer(it) }
        engine?.destroy()
        engine = null
    }

    fun setPreview(preview: TrajectoryPreview?, profileConfig: RobotKinematicsConfig) {
        this.preview = preview
        this.previewConfig = profileConfig
        this.previewModel = UrdfCache.get(context, profileConfig.urdfAsset)
        currentIndex = 0
        autoFitPending = true
        applyPreviewIfReady()
    }

    fun setCurrentIndex(index: Int) {
        currentIndex = index
        updatePose()
    }

    fun setAvatarVisible(visible: Boolean) {
        if (avatarVisible == visible) return
        avatarVisible = visible
        val asset = avatarAsset ?: return
        val scene = scene ?: return
        if (visible) {
            scene.addEntities(asset.entities)
        } else {
            scene.removeEntities(asset.entities)
        }
        updatePose()
    }

    fun setCameraOrbit(yaw: Double, pitch: Double, distance: Double, panX: Double, panY: Double) {
        orbitYaw = yaw
        orbitPitch = pitch
        orbitDistance = distance
        this.panX = panX
        this.panY = panY
        updateCamera()
    }

    // ── Interactive object picking & manipulation ──────────────────────

    /**
     * Unproject screen tap to ground plane (Z=0) and check proximity to objects.
     * @return object name ("robot" or "avatar") or null
     */
    fun hitTestAt(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int): String? {
        // Project object visual centers to screen space and check distance to tap.
        // Robot center ≈ Z=1.0 (half of 1.9m height), Avatar center ≈ Z=0.85 (half of 1.7m).
        val robotScreen = worldToScreen(robotWorldX, robotWorldY, 1.0, viewWidth, viewHeight)
        val avatarScreen = if (avatarAsset != null && avatarVisible)
            worldToScreen(avatarWorldX, avatarWorldY, 0.85, viewWidth, viewHeight)
        else null

        // Screen-space distance threshold (pixels) — generous for touch
        val threshold = 120f

        val robotDist = robotScreen?.let {
            kotlin.math.hypot((screenX - it.first).toDouble(), (screenY - it.second).toDouble())
        } ?: Double.MAX_VALUE
        val avatarDist = avatarScreen?.let {
            kotlin.math.hypot((screenX - it.first).toDouble(), (screenY - it.second).toDouble())
        } ?: Double.MAX_VALUE

        Log.d(TAG, "hitTestAt tap=(${"%.0f".format(screenX)}, ${"%.0f".format(screenY)}) " +
            "robotScreen=${robotScreen?.let { "(%.0f, %.0f)".format(it.first, it.second) }} dist=${"%.0f".format(robotDist)} " +
            "avatarScreen=${avatarScreen?.let { "(%.0f, %.0f)".format(it.first, it.second) }} dist=${"%.0f".format(avatarDist)}")

        val minDist = minOf(robotDist, avatarDist)
        if (minDist > threshold) return null
        val result = if (robotDist <= avatarDist) "robot" else "avatar"
        Log.d(TAG, "  HIT: $result")
        return result
    }

    /**
     * Project a world-space point to screen pixel coordinates.
     * Returns (screenX, screenY) or null if point is behind camera.
     */
    private fun worldToScreen(
        worldX: Double, worldY: Double, worldZ: Double,
        viewWidth: Int, viewHeight: Int
    ): Pair<Float, Float>? {
        val cosPitch = kotlin.math.cos(orbitPitch)
        val sinPitch = kotlin.math.sin(orbitPitch)
        val cosYaw = kotlin.math.cos(orbitYaw)
        val sinYaw = kotlin.math.sin(orbitYaw)

        // Camera eye
        val eyeX = panX + orbitDistance * cosPitch * cosYaw
        val eyeY = panY + orbitDistance * cosPitch * sinYaw
        val eyeZ = 0.6 + orbitDistance * sinPitch

        // Forward (eye → target), normalized
        val fx = panX - eyeX; val fy = panY - eyeY; val fz = 0.6 - eyeZ
        val fl = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-9) return null
        val fdx = fx / fl; val fdy = fy / fl; val fdz = fz / fl

        // Right = normalize(forward × (0,0,1))
        var rx = fdy; var ry = -fdx
        val rl = kotlin.math.sqrt(rx * rx + ry * ry)
        if (rl < 1e-9) return null
        rx /= rl; ry /= rl

        // Up = right × forward
        val ux = ry * fdz /* - 0 * fdy */
        val uy = /* 0 * fdx */ -rx * fdz
        val uz = rx * fdy - ry * fdx

        // Vector from eye to world point
        val vx = worldX - eyeX
        val vy = worldY - eyeY
        val vz = worldZ - eyeZ

        // Camera-space coordinates: depth along forward, x along right, y along up
        val depth = vx * fdx + vy * fdy + vz * fdz
        if (depth <= 0) return null // behind camera
        val cx = vx * rx + vy * ry // + vz * 0
        val cy = vx * ux + vy * uy + vz * uz

        // Perspective divide using FOV
        val tanHalfFov = kotlin.math.tan(Math.toRadians(22.5))
        val aspect = viewWidth.toDouble() / viewHeight.toDouble()
        val ndcX = cx / (depth * aspect * tanHalfFov)
        val ndcY = cy / (depth * tanHalfFov)

        // NDC → screen pixels
        val sx = ((ndcX + 1.0) * 0.5 * viewWidth).toFloat()
        val sy = ((1.0 - ndcY) * 0.5 * viewHeight).toFloat()
        return Pair(sx, sy)
    }

    /**
     * Move object by screen-space delta, projected onto the ground plane.
     * Uses the same mapping as camera pan but with positive direction (object follows finger).
     */
    fun moveObjectByScreenDelta(
        objectName: String, dx: Float, dy: Float,
        @Suppress("UNUSED_PARAMETER") viewWidth: Int,
        @Suppress("UNUSED_PARAMETER") viewHeight: Int
    ) {
        val scale = orbitDistance * 0.002
        val cosY = kotlin.math.cos(orbitYaw)
        val sinY = kotlin.math.sin(orbitYaw)
        // Map screen delta to world XY (same axes as pan, but object follows finger → positive)
        val worldDx = dx * scale * cosY - dy * scale * sinY
        val worldDy = dx * scale * sinY + dy * scale * cosY
        when (objectName) {
            "robot" -> { robotOffsetX += worldDx; robotOffsetY += worldDy }
            "avatar" -> { avatarOffsetX += worldDx; avatarOffsetY += worldDy }
        }
        updatePose()
    }

    /** Rotate object by given angle (radians) around its vertical axis. */
    fun rotateObject(objectName: String, deltaAngle: Double) {
        when (objectName) {
            "robot" -> robotYawOffset += deltaAngle
            "avatar" -> avatarYawOffset += deltaAngle
        }
        updatePose()
    }

    /** Reset all user-applied object offsets to zero. */
    fun resetObjectOffsets() {
        robotOffsetX = 0.0; robotOffsetY = 0.0; robotYawOffset = 0.0
        avatarOffsetX = 0.0; avatarOffsetY = 0.0; avatarYawOffset = 0.0
        updatePose()
    }

    /**
     * Unproject a screen point to world coordinates on the ground plane (Z=0).
     * Uses the orbit camera parameters to construct a view ray and intersect.
     */
    private fun screenToGroundPlane(
        screenX: Float, screenY: Float,
        viewWidth: Int, viewHeight: Int
    ): Pair<Double, Double>? {
        val cosPitch = kotlin.math.cos(orbitPitch)
        val sinPitch = kotlin.math.sin(orbitPitch)
        val cosYaw = kotlin.math.cos(orbitYaw)
        val sinYaw = kotlin.math.sin(orbitYaw)

        // Camera eye position
        val eyeX = panX + orbitDistance * cosPitch * cosYaw
        val eyeY = panY + orbitDistance * cosPitch * sinYaw
        val eyeZ = 0.6 + orbitDistance * sinPitch

        // Forward (eye → target), normalized
        val fx = panX - eyeX; val fy = panY - eyeY; val fz = 0.6 - eyeZ
        val fl = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-9) return null
        val fdx = fx / fl; val fdy = fy / fl; val fdz = fz / fl

        // Right = forward × (0,0,1)
        var rx = fdy; var ry = -fdx; val rz = 0.0
        val rl = kotlin.math.sqrt(rx * rx + ry * ry)
        if (rl < 1e-9) return null
        rx /= rl; ry /= rl

        // Up = right × forward
        val ux = ry * fdz - rz * fdy
        val uy = rz * fdx - rx * fdz
        val uz = rx * fdy - ry * fdx

        // NDC (-1..1), Y flipped for screen coords
        val ndcX = 2.0 * screenX / viewWidth - 1.0
        val ndcY = 1.0 - 2.0 * screenY / viewHeight

        // Perspective projection: vertical FOV 45°
        val tanHalfFov = kotlin.math.tan(Math.toRadians(22.5))
        val aspect = viewWidth.toDouble() / viewHeight.toDouble()

        // Ray direction in world space
        val rayDx = fdx + ndcX * aspect * tanHalfFov * rx + ndcY * tanHalfFov * ux
        val rayDy = fdy + ndcX * aspect * tanHalfFov * ry + ndcY * tanHalfFov * uy
        val rayDz = fdz + ndcX * aspect * tanHalfFov * rz + ndcY * tanHalfFov * uz

        // Intersect ray with Z=0: eyeZ + t * rayDz = 0
        if (kotlin.math.abs(rayDz) < 1e-9) return null
        val t = -eyeZ / rayDz
        if (t < 0) return null // behind camera

        return Pair(eyeX + t * rayDx, eyeY + t * rayDy)
    }

    private fun attachSurface(surface: SurfaceView) {
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(nativeSurface: android.view.Surface) {
                    swapChain?.let { engine?.destroySwapChain(it) }
                    swapChain = engine?.createSwapChain(nativeSurface)
                    surface.display?.let { display ->
                        renderer?.let { displayHelper?.attach(it, display) }
                    }
                }

                override fun onDetachedFromSurface() {
                    swapChain?.let { engine?.destroySwapChain(it) }
                    swapChain = null
                    displayHelper?.detach()
                }

                override fun onResized(width: Int, height: Int) {
                    view?.viewport = Viewport(0, 0, width, height)
                    camera?.setProjection(45.0, width.toDouble() / height.toDouble(), 0.05, 50.0, Camera.Fov.VERTICAL)
                    updateCamera()
                }
            }
            attachTo(surface)
        }
        camera?.setProjection(45.0, surface.width.toDouble() / surface.height.toDouble(), 0.05, 50.0, Camera.Fov.VERTICAL)
        updateCamera()
    }

    private fun startRendering() {
        val choreographer = Choreographer.getInstance()
        this.choreographer = choreographer
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                renderFrame()
                if (frameCallback === this) {
                    choreographer.postFrameCallback(this)
                }
            }
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun stopRendering() {
        val callback = frameCallback
        if (callback != null) {
            choreographer?.removeFrameCallback(callback)
        }
        frameCallback = null
    }

    private fun renderFrame() {
        val engine = engine ?: return
        val renderer = renderer ?: return
        val swapChain = swapChain ?: return
        val view = view ?: return

        if (renderer.beginFrame(swapChain, System.nanoTime())) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun applyPreviewIfReady() {
        val config = previewConfig ?: return
        if (engine == null || scene == null || assetLoader == null || resourceLoader == null) return
        ensureMeshAssets(config)
        rebuildPathInstances()
        if (autoFitPending) {
            autoFitCamera()
            autoFitPending = false
        }
        updatePose()
    }

    fun fitCameraToPreview() {
        autoFitPending = true
        applyPreviewIfReady()
    }

    private fun autoFitCamera() {
        val preview = preview ?: return
        val config = previewConfig ?: return
        val model = previewModel ?: return
        if (preview.samples.isEmpty()) return

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        val downsample = maxOf(1, preview.samples.size / 100)
        preview.samples.filterIndexed { index, _ -> index % downsample == 0 }.forEach { sample ->
            minX = minOf(minX, sample.baseX)
            maxX = maxOf(maxX, sample.baseX)
            minY = minOf(minY, sample.baseY)
            maxY = maxOf(maxY, sample.baseY)
            minZ = minOf(minZ, 0.0)
            maxZ = maxOf(maxZ, 1.9) // Robot height + avatar headroom

            // Include avatar forward offset in bounds
            if (avatarAsset != null && avatarVisible) {
                val cosYaw = kotlin.math.cos(sample.baseYaw)
                val sinYaw = kotlin.math.sin(sample.baseYaw)
                val avX = sample.baseX + cosYaw * AVATAR_DISTANCE
                val avY = sample.baseY + sinYaw * AVATAR_DISTANCE
                minX = minOf(minX, avX)
                maxX = maxOf(maxX, avX)
                minY = minOf(minY, avY)
                maxY = maxOf(maxY, avY)
            }

            val pose = RobotKinematics.computeCameraPose(model, config, sample)
            if (pose != null) {
                minX = minOf(minX, pose.position.x)
                maxX = maxOf(maxX, pose.position.x)
                minY = minOf(minY, pose.position.y)
                maxY = maxOf(maxY, pose.position.y)
                minZ = minOf(minZ, pose.position.z)
                maxZ = maxOf(maxZ, pose.position.z)
            }
        }

        if (!minX.isFinite() || !minY.isFinite()) return
        val centerX = (minX + maxX) * 0.5
        val centerY = (minY + maxY) * 0.5
        val spanX = maxX - minX
        val spanY = maxY - minY
        val spanZ = maxZ - minZ
        val radius = maxOf(spanX, spanY, spanZ).coerceAtLeast(0.5)

        panX = centerX
        panY = centerY
        orbitDistance = (radius * 2.2).coerceIn(1.8, 12.0)
        updateCamera()
    }

    private fun loadCubeAsset() {
        val buffer = loadAssetBuffer("preview/unit_cube.glb")
        if (buffer == null) {
            Log.w(TAG, "Failed to load cube asset")
            return
        }
        val asset = assetLoader?.createAsset(buffer)
        if (asset == null) {
            Log.w(TAG, "Failed to create cube asset")
            return
        }
        resourceLoader?.loadResources(asset)
        cubeAsset = asset
    }

    private fun loadAvatarAsset() {
        val buffer = loadAssetBuffer(AVATAR_ASSET_PATH)
        if (buffer == null) {
            Log.w(TAG, "Avatar asset not found: $AVATAR_ASSET_PATH")
            return
        }
        val loader = assetLoader ?: return
        val resLoader = resourceLoader ?: return
        val scene = scene ?: return
        val asset = loader.createAsset(buffer) ?: run {
            Log.w(TAG, "Failed to create avatar asset")
            return
        }
        resLoader.loadResources(asset)
        asset.releaseSourceData()
        if (avatarVisible) {
            scene.addEntities(asset.entities)
        }
        avatarAsset = asset
        Log.i(TAG, "Avatar loaded: ${asset.entities.size} entities")
        updateAvatarDefaultPose()
    }

    private fun rebuildPathInstances() {
        val scene = scene ?: return
        val asset = cubeAsset ?: return
        val preview = preview ?: return

        basePathInstances.forEach { scene.removeEntities(it.entities) }
        eePathInstances.forEach { scene.removeEntities(it.entities) }
        keyframeInstances.forEach { scene.removeEntities(it.entities) }
        basePathInstances.clear()
        eePathInstances.clear()
        keyframeInstances.clear()

        val downsample = maxOf(1, preview.samples.size / 80)
        val config = previewConfig ?: return
        val model = previewModel ?: return

        preview.samples.filterIndexed { idx, _ -> idx % downsample == 0 }.forEach { sample ->
            val baseInstance = assetLoader?.createInstance(asset) ?: return@forEach
            basePathInstances.add(baseInstance)
            scene.addEntities(baseInstance.entities)
            val basePos = Vec3(sample.baseX, sample.baseY, 0.0)
            setInstanceTransform(baseInstance, transformForMarker(basePos, 0.05))

            val eePose = RobotKinematics.computeCameraPose(model, config, sample)
            if (eePose != null) {
                val eeInstance = assetLoader?.createInstance(asset) ?: return@forEach
                eePathInstances.add(eeInstance)
                scene.addEntities(eeInstance.entities)
                setInstanceTransform(eeInstance, transformForMarker(eePose.position, 0.04))
            }
        }

        preview.keyframes.forEach { frame ->
            val keyInstance = assetLoader?.createInstance(asset) ?: return@forEach
            keyframeInstances.add(keyInstance)
            scene.addEntities(keyInstance.entities)
            val pos = Vec3(frame.baseX, frame.baseY, 0.02)
            setInstanceTransform(keyInstance, transformForMarker(pos, 0.07))
        }
    }

    private fun updatePose() {
        val scene = scene ?: return
        val asset = cubeAsset ?: return
        val preview = preview ?: return
        val config = previewConfig ?: return
        val model = previewModel ?: return
        if (preview.samples.isEmpty()) return

        val rawSample = preview.samples[currentIndex.coerceIn(0, preview.samples.lastIndex)]

        // Apply user offsets to create effective sample for robot
        val sample = rawSample.copy(
            baseX = rawSample.baseX + robotOffsetX,
            baseY = rawSample.baseY + robotOffsetY,
            baseYaw = rawSample.baseYaw + robotYawOffset
        )

        // Track robot world position for hit testing
        robotWorldX = sample.baseX
        robotWorldY = sample.baseY

        val chain = RobotKinematics.computeChainPoints(model, config, sample)
        val cameraPose = RobotKinematics.computeCameraPose(model, config, sample)
        val linkPoints = if (chain.size > 1) chain.drop(1) else emptyList()
        val linkTransforms = RobotKinematics.computeWorldLinkTransforms(model, config, sample)

        ensureInstances(baseInstances, 1, scene, asset)
        ensureInstances(chainInstances, linkPoints.size, scene, asset)
        ensureInstances(cameraInstances, 1, scene, asset)
        if (baseInstances.isEmpty() || cameraInstances.isEmpty()) {
            return
        }

        val baseTransform = Transform.translation(Vec3(sample.baseX, sample.baseY, 0.0))
            .multiply(Transform.rotationZ(sample.baseYaw))
        setInstanceTransform(baseInstances.first(), transformWithScale(baseTransform, 0.18))

        linkPoints.forEachIndexed { index, point ->
            val instance = chainInstances[index]
            setInstanceTransform(instance, transformForMarker(point, 0.06))
        }

        if (cameraPose != null) {
            setInstanceTransform(cameraInstances.first(), transformWithScale(cameraPose.transform, 0.08))
        }

        if (meshAssets.isNotEmpty()) {
            val transformManager = engine?.transformManager ?: return
            for ((linkName, meshAsset) in meshAssets) {
                val worldTransform = linkTransforms[linkName] ?: continue
                val inst = transformManager.getInstance(meshAsset.root)
                transformManager.setTransform(inst, worldTransform.toFloatArray())
            }
        }

        // Position avatar alongside robot base (uses rawSample for trajectory-relative placement + avatar offsets)
        updateAvatarTransform(rawSample)
    }

    /**
     * Place the avatar in front of the robot, face to face.
     *
     * Coordinate conversion: RPM GLB is Y-up (glTF standard), our scene is Z-up.
     * rotationX(+π/2) converts Y-up → Z-up (avatar's Y becomes Z = upright).
     * After that rotation, avatar's original +Z face direction maps to -Y in our scene.
     * The avatar's -Y facing corresponds to angle -π/2 from +X.
     * To face angle α, we rotate Z by (α + π/2).
     *
     * Face-to-face: avatar faces (baseYaw + π) = opposite to robot.
     * So rotZ angle = (baseYaw + π) + π/2 = baseYaw + 3π/2.
     */
    private fun updateAvatarTransform(sample: TrajectorySample) {
        val asset = avatarAsset ?: return
        if (!avatarVisible) return
        val tm = engine?.transformManager ?: return
        val inst = tm.getInstance(asset.root)
        if (inst == 0) return

        val cosYaw = kotlin.math.cos(sample.baseYaw)
        val sinYaw = kotlin.math.sin(sample.baseYaw)

        // Position: AVATAR_DISTANCE meters in front of the robot along its facing direction + user offset
        val avatarX = sample.baseX + cosYaw * AVATAR_DISTANCE + avatarOffsetX
        val avatarY = sample.baseY + sinYaw * AVATAR_DISTANCE + avatarOffsetY

        // Track avatar world position for hit testing
        avatarWorldX = avatarX
        avatarWorldY = avatarY

        // Facing back toward robot = baseYaw + π, + user rotation offset
        // rotZ angle to achieve that = (baseYaw + π) + π/2 = baseYaw + 3π/2
        val facingZ = sample.baseYaw + 3.0 * kotlin.math.PI / 2.0 + avatarYawOffset
        val transform = Transform.translation(Vec3(avatarX, avatarY, 0.0))
            .multiply(Transform.rotationZ(facingZ))
            .multiply(Transform.rotationX(kotlin.math.PI / 2.0))
        tm.setTransform(inst, transform.toFloatArray())
    }

    /**
     * Place avatar at default standing position when no trajectory is loaded.
     * 2.5m in front of robot origin (+X), facing back toward robot (-X).
     */
    private fun updateAvatarDefaultPose() {
        val asset = avatarAsset ?: return
        if (!avatarVisible) return
        val tm = engine?.transformManager ?: return
        val inst = tm.getInstance(asset.root)
        if (inst == 0) return

        val ax = AVATAR_DISTANCE + avatarOffsetX
        val ay = avatarOffsetY
        avatarWorldX = ax
        avatarWorldY = ay

        // Default: robot at origin facing +X, avatar at (2.5, 0, 0) facing -X
        // Facing -X = angle π, rotZ = π + π/2 = 3π/2
        val transform = Transform.translation(Vec3(ax, ay, 0.0))
            .multiply(Transform.rotationZ(3.0 * kotlin.math.PI / 2.0 + avatarYawOffset))
            .multiply(Transform.rotationX(kotlin.math.PI / 2.0))
        tm.setTransform(inst, transform.toFloatArray())
    }

    private fun updateCamera() {
        val centerX = panX
        val centerY = panY
        val centerZ = 0.6
        val x = orbitDistance * kotlin.math.cos(orbitPitch) * kotlin.math.cos(orbitYaw)
        val y = orbitDistance * kotlin.math.cos(orbitPitch) * kotlin.math.sin(orbitYaw)
        val z = orbitDistance * kotlin.math.sin(orbitPitch)
        camera?.lookAt(
            centerX + x,
            centerY + y,
            centerZ + z,
            centerX,
            centerY,
            centerZ,
            0.0,
            0.0,
            1.0
        )
    }

    private fun ensureInstances(
        target: MutableList<FilamentInstance>,
        count: Int,
        scene: Scene,
        asset: FilamentAsset
    ) {
        while (target.size < count) {
            val instance = assetLoader?.createInstance(asset) ?: break
            target.add(instance)
            scene.addEntities(instance.entities)
        }
        if (target.size > count) {
            target.subList(count, target.size).forEach { scene.removeEntities(it.entities) }
            target.subList(count, target.size).clear()
        }
    }

    private fun transformForMarker(position: Vec3, scale: Double): Transform {
        val transform = Transform.identity()
        val m = transform.m
        m[0] = scale
        m[5] = scale
        m[10] = scale
        m[12] = position.x
        m[13] = position.y
        m[14] = position.z
        return Transform(m)
    }

    private fun transformWithScale(transform: Transform, scale: Double): Transform {
        val m = transform.m.copyOf()
        m[0] *= scale
        m[1] *= scale
        m[2] *= scale
        m[4] *= scale
        m[5] *= scale
        m[6] *= scale
        m[8] *= scale
        m[9] *= scale
        m[10] *= scale
        return Transform(m)
    }

    private fun setInstanceTransform(instance: FilamentInstance, transform: Transform) {
        val tm = engine?.transformManager ?: return
        val entity = instance.root
        val inst = tm.getInstance(entity)
        tm.setTransform(inst, transform.toFloatArray())
    }

    private fun Transform.toFloatArray(): FloatArray {
        return FloatArray(16) { index -> m[index].toFloat() }
    }

    private fun loadAssetBuffer(path: String): ByteBuffer? {
        return try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size)
            buffer.put(bytes)
            buffer.flip()
            buffer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read asset $path", e)
            null
        }
    }

    private fun setupLighting(engine: Engine) {
        if (lightEntity == 0) {
            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(80000.0f)
                .direction(0.3f, -1.0f, -0.6f)
                .castShadows(false)
                .build(engine, lightEntity)
            scene?.addEntity(lightEntity)
        }
        if (indirectLight == null) {
            indirectLight = IndirectLight.Builder()
                .intensity(35000.0f)
                .build(engine)
            scene?.indirectLight = indirectLight
        }
    }

    private fun ensureMeshAssets(config: RobotKinematicsConfig) {
        val model = previewModel ?: return
        val key = config.urdfAsset
        if (meshProfileKey == key) return

        meshAssets.values.forEach { asset ->
            scene?.removeEntities(asset.entities)
            assetLoader?.destroyAsset(asset)
        }
        meshAssets.clear()
        meshProfileKey = key

        val loader = assetLoader ?: return
        val resLoader = resourceLoader ?: return
        val scene = scene ?: return

        model.linkMeshes.forEach { (linkName, meshPath) ->
            val assetPath = mapMeshAssetPath(config.urdfAsset, meshPath) ?: return@forEach
            val buffer = loadAssetBuffer(assetPath) ?: return@forEach
            val asset = loader.createAsset(buffer) ?: return@forEach
            resLoader.loadResources(asset)
            asset.releaseSourceData()
            scene.addEntities(asset.entities)
            meshAssets[linkName] = asset
        }
    }

    private fun mapMeshAssetPath(urdfAssetPath: String, meshPath: String): String? {
        val raw = meshPath.trim()
        if (raw.isEmpty()) return null

        if (raw.startsWith("robot_models/")) {
            return raw.substringBeforeLast('.', raw) + ".glb"
        }

        if (raw.startsWith("package://")) {
            val clean = raw.removePrefix("package://")
            val marker = "/meshes/"
            val index = clean.indexOf(marker)
            if (index > 0) {
                val robotName = clean.substring(0, index)
                val filename = clean.substring(index + marker.length)
                val base = filename.substringBeforeLast('.')
                return "robot_models/$robotName/meshes/$base.glb"
            }
        }

        val urdfDir = urdfAssetPath.substringBeforeLast('/', "")
        val relative = raw.removePrefix("./").removePrefix("/")
        if (relative.isEmpty()) return null
        val normalized = relative.substringBeforeLast('.', relative) + ".glb"
        return if (urdfDir.isBlank()) normalized else "$urdfDir/$normalized"
    }
}
