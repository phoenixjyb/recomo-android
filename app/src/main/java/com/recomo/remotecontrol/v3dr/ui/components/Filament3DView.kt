package com.recomo.remotecontrol.v3dr.ui.components

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lighting configuration for 3D viewer
 */
data class LightingSettings(
    val mainLightIntensity: Float = 100000.0f,
    val fillLightIntensity: Float = 60000.0f,
    val backLightIntensity: Float = 40000.0f,
    val ambientIntensity: Float = 80000.0f,
    val enableShadows: Boolean = false
)

/**
 * Rendering configuration for 3D viewer
 */
data class RenderingSettings(
    val antiAliasing: View.AntiAliasing = View.AntiAliasing.FXAA,
    val dithering: View.Dithering = View.Dithering.TEMPORAL,
    val bloomEnabled: Boolean = false,
    val ambientOcclusion: View.AmbientOcclusion = View.AmbientOcclusion.NONE
)

/**
 * Camera configuration for 3D viewer
 */
data class CameraSettings(
    val fieldOfView: Double = 45.0,
    val nearPlane: Double = 0.1,
    val farPlane: Double = 100.0,
    val initialDistance: Float = 2.5f
)

/**
 * Filament-based 3D model viewer with touch controls
 * Supports GLB/GLTF format models
 */
@Composable
fun Filament3DView(
    modelPath: String,
    modifier: Modifier = Modifier,
    lightingSettings: LightingSettings = LightingSettings(),
    renderingSettings: RenderingSettings = RenderingSettings(),
    cameraSettings: CameraSettings = CameraSettings()
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    val filamentEngine = remember(context, lightingSettings, renderingSettings, cameraSettings) { 
        FilamentEngine(context, lightingSettings, renderingSettings, cameraSettings) 
    }
    
    LaunchedEffect(modelPath) {
        try {
            Log.d("Filament3DView", "Starting to load model: $modelPath")
            // Check if path is a file path or asset path
            if (modelPath.startsWith("/")) {
                // Absolute file path
                filamentEngine.loadModelFromFile(modelPath)
            } else {
                // Asset path
                filamentEngine.loadModelFromAssets(modelPath)
            }
        } catch (e: Exception) {
            Log.e("Filament3DView", "Failed to load model: $modelPath", e)
        }
    }
    
    LaunchedEffect(surfaceView) {
        surfaceView?.let {
            Log.d("Filament3DView", "Attaching surface in LaunchedEffect")
            filamentEngine.attachSurface(it)
        }
    }
    
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> filamentEngine.onResume()
                Lifecycle.Event.ON_PAUSE -> filamentEngine.onPause()
                Lifecycle.Event.ON_DESTROY -> filamentEngine.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        
        onDispose {
            lifecycle.removeObserver(observer)
            filamentEngine.onDestroy()
        }
    }
    
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { surface ->
                surfaceView = surface
            }
        },
        update = { view ->
            // Force layout update on configuration changes
            view.requestLayout()
        },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Filament rendering engine wrapper
 */
private class FilamentEngine(
    private val context: Context,
    private val lightingSettings: LightingSettings,
    private val renderingSettings: RenderingSettings,
    private val cameraSettings: CameraSettings
) {
    companion object {
        private const val TAG = "FilamentEngine"
        
        init {
            Utils.init()
        }
    }
    
    // Filament core components
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0
    
    // Surface management
    private var surfaceView: SurfaceView? = null
    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var swapChain: SwapChain? = null
    
    // Asset loading
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var materialProvider: MaterialProvider? = null
    private var asset: com.google.android.filament.gltfio.FilamentAsset? = null
    
    // Lifecycle
    private var isDestroyed = false
    
    // Rendering
    private var choreographer: Choreographer? = null
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isDestroyed) {
                choreographer?.postFrameCallback(this)
                render(frameTimeNanos)
            }
        }
    }
    
    // Camera control
    private var cameraDistance = cameraSettings.initialDistance
    private var cameraAngleX = 0.0f
    private var cameraAngleY = 0.5f  // Higher angle to look down at model
    
    // Touch handling
    private var lastX = 0f
    private var lastY = 0f
    private var initialDistance = 0f
    
    init {
        Log.d(TAG, "Initializing FilamentEngine")
        setupFilament()
        setupCamera()
        setupLight()
        Log.d(TAG, "FilamentEngine initialized successfully")
    }
    
    private fun setupFilament() {
        Log.d(TAG, "Setting up Filament")
        engine = Engine.create()
        renderer = engine?.createRenderer()
        scene = engine?.createScene()
        view = engine?.createView()
        
        view?.scene = scene
        
        // Apply rendering settings
        view?.apply {
            antiAliasing = renderingSettings.antiAliasing
            dithering = renderingSettings.dithering
        }
        
        // Set clear color to white background
        renderer?.setClearOptions(Renderer.ClearOptions().apply {
            clearColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)  // White (RGBA)
            clear = true
        })
        Log.d(TAG, "Filament core components created with AA: ${renderingSettings.antiAliasing}")
        
        // Create material provider for GLTF loading
        engine?.let { eng ->
            materialProvider = UbershaderProvider(eng)
            assetLoader = AssetLoader(eng, materialProvider!!, EntityManager.get())
            resourceLoader = ResourceLoader(eng)
            Log.d(TAG, "Asset loaders created")
        }
    }
    
    private fun setupCamera() {
        engine?.let { eng ->
            cameraEntity = EntityManager.get().create()
            camera = eng.createCamera(cameraEntity)
            view?.camera = camera
            
            updateCameraPosition()
            Log.d(TAG, "Camera configured - FOV: ${cameraSettings.fieldOfView}, distance: ${cameraSettings.initialDistance}")
        }
    }
    
    private fun setupLight() {
        engine?.let { eng ->
            // Main directional light (sun)
            val sunEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(lightingSettings.mainLightIntensity)
                .direction(0.3f, -1.0f, -0.3f)
                .castShadows(lightingSettings.enableShadows)
                .build(eng, sunEntity)
            scene?.addEntity(sunEntity)
            
            // Fill light from opposite side
            val fillLightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(lightingSettings.fillLightIntensity)
                .direction(-0.5f, -0.5f, 0.5f)
                .castShadows(false)
                .build(eng, fillLightEntity)
            scene?.addEntity(fillLightEntity)
            
            // Back light for rim lighting
            val backLightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(lightingSettings.backLightIntensity)
                .direction(0.0f, 0.5f, 1.0f)
                .castShadows(false)
                .build(eng, backLightEntity)
            scene?.addEntity(backLightEntity)
            
            // Create indirect light (ambient)
            val ibl = IndirectLight.Builder()
                .intensity(lightingSettings.ambientIntensity)
                .build(eng)
            scene?.indirectLight = ibl
            
            Log.d(TAG, "Lights configured - main: ${lightingSettings.mainLightIntensity}, ambient: ${lightingSettings.ambientIntensity}")
        }
    }
    
    private fun updateCameraPosition() {
        camera?.let { cam ->
            // Calculate camera position based on angles and distance
            val x = cameraDistance * cos(cameraAngleY) * sin(cameraAngleX)
            val y = cameraDistance * sin(cameraAngleY)
            val z = cameraDistance * cos(cameraAngleY) * cos(cameraAngleX)
            
            cam.lookAt(
                x.toDouble(), y.toDouble(), z.toDouble(),  // eye
                0.0, 0.0, 0.0,                              // center
                0.0, 1.0, 0.0                               // up
            )
        }
    }
    
    fun attachSurface(surface: SurfaceView) {
        Log.d(TAG, "Attaching surface")
        surfaceView = surface
        
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: android.view.Surface) {
                    if (isDestroyed) return
                    Log.d(TAG, "onNativeWindowChanged called")
                    swapChain?.let { engine?.destroySwapChain(it) }
                    swapChain = engine?.createSwapChain(surface)
                    val display = surfaceView?.display
                    if (renderer != null && display != null && !isDestroyed) {
                        displayHelper?.attach(renderer!!, display)
                        Log.d(TAG, "DisplayHelper attached")
                    }
                }
                
                override fun onDetachedFromSurface() {
                    if (isDestroyed) return
                    swapChain?.let { engine?.destroySwapChain(it) }
                    swapChain = null
                    displayHelper?.detach()
                }
                
                override fun onResized(width: Int, height: Int) {
                    if (isDestroyed) return
                    Log.d(TAG, "onResized: ${width}x${height}")
                    view?.viewport = Viewport(0, 0, width, height)
                    camera?.setProjection(
                        cameraSettings.fieldOfView, 
                        width.toDouble() / height.toDouble(), 
                        cameraSettings.nearPlane, 
                        cameraSettings.farPlane, 
                        Camera.Fov.VERTICAL
                    )
                    Log.d(TAG, "Viewport and projection configured")
                }
            }
            attachTo(surface)
        }
        
        displayHelper = DisplayHelper(context)
        
        // Setup touch handling
        surface.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }
    
    suspend fun loadModelFromFile(filePath: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading model from file: $filePath")
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Model file does not exist: $filePath")
                    return@withContext
                }
                
                val buffer = java.io.FileInputStream(file).use { input ->
                    val bytes = input.readBytes()
                    Log.d(TAG, "Read ${bytes.size} bytes from file")
                    ByteBuffer.allocateDirect(bytes.size).apply {
                        put(bytes)
                        rewind()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    assetLoader?.let { loader ->
                        Log.d(TAG, "Creating asset from buffer (${buffer.capacity()} bytes)")
                        asset = loader.createAsset(buffer)
                        asset?.let { loadedAsset ->
                            Log.d(TAG, "Asset created, loading resources and textures")
                            
                            // Load all resources including textures - this must complete before rendering
                            resourceLoader?.let { resLoader ->
                                // asyncBeginLoad starts loading textures/materials
                                resLoader.asyncBeginLoad(loadedAsset)
                                
                                // Wait for all resources to load
                                var resourcesReady = false
                                while (!resourcesReady) {
                                    withContext(Dispatchers.IO) {
                                        kotlinx.coroutines.delay(16) // ~60fps check
                                    }
                                    withContext(Dispatchers.Main) {
                                        resLoader.asyncUpdateLoad()
                                        resourcesReady = resLoader.asyncGetLoadProgress() >= 1.0f
                                    }
                                }
                                Log.d(TAG, "All resources loaded (progress: 100%)")
                            }
                            
                            // Add entities to scene after resources are fully loaded
                            scene?.addEntities(loadedAsset.entities)
                            Log.d(TAG, "Model loaded successfully: $filePath, entities: ${loadedAsset.entities.size}")
                        } ?: Log.e(TAG, "Failed to create asset from buffer")
                    } ?: Log.e(TAG, "AssetLoader is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model from file: $filePath", e)
            }
        }
    }
    
    suspend fun loadModelFromAssets(assetPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val buffer = context.assets.open(assetPath).use { input ->
                    val bytes = input.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).apply {
                        put(bytes)
                        rewind()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    assetLoader?.let { loader ->
                        Log.d(TAG, "Creating asset from buffer (${buffer.capacity()} bytes)")
                        asset = loader.createAsset(buffer)
                        asset?.let { loadedAsset ->
                            Log.d(TAG, "Asset created, loading resources and textures")
                            
                            // Load all resources including textures
                            resourceLoader?.let { resLoader ->
                                resLoader.asyncBeginLoad(loadedAsset)
                                
                                var resourcesReady = false
                                while (!resourcesReady) {
                                    withContext(Dispatchers.IO) {
                                        kotlinx.coroutines.delay(16)
                                    }
                                    withContext(Dispatchers.Main) {
                                        resLoader.asyncUpdateLoad()
                                        resourcesReady = resLoader.asyncGetLoadProgress() >= 1.0f
                                    }
                                }
                                Log.d(TAG, "All resources loaded (progress: 100%)")
                            }
                            
                            // Add entities to scene
                            scene?.addEntities(loadedAsset.entities)
                            Log.d(TAG, "Model loaded successfully: $assetPath, entities: ${loadedAsset.entities.size}")
                        } ?: Log.e(TAG, "Failed to create asset from buffer")
                    } ?: Log.e(TAG, "AssetLoader is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model from assets", e)
            }
        }
    }
    
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialDistance = getDistance(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    // Rotate
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    
                    cameraAngleX += dx * 0.01f
                    cameraAngleY = (cameraAngleY + dy * 0.01f).coerceIn(-PI.toFloat() / 2 + 0.1f, PI.toFloat() / 2 - 0.1f)
                    
                    updateCameraPosition()
                    
                    lastX = event.x
                    lastY = event.y
                } else if (event.pointerCount == 2) {
                    // Zoom
                    val distance = getDistance(event)
                    val scale = distance / initialDistance
                    
                    cameraDistance = (cameraDistance / scale).coerceIn(1.5f, 10.0f)
                    updateCameraPosition()
                    
                    initialDistance = distance
                }
            }
        }
        return true
    }
    
    private fun getDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun render(frameTimeNanos: Long) {
        if (isDestroyed || uiHelper?.isReadyToRender != true) return
        
        // Render frame
        renderer?.let { r ->
            swapChain?.let { sc ->
                view?.let { v ->
                    if (r.beginFrame(sc, frameTimeNanos)) {
                        r.render(v)
                        r.endFrame()
                    }
                }
            }
        }
    }
    
    fun onResume() {
        Log.d(TAG, "onResume - starting rendering")
        choreographer = Choreographer.getInstance()
        choreographer?.postFrameCallback(frameCallback)
    }
    
    fun onPause() {
        choreographer?.removeFrameCallback(frameCallback)
    }
    
    fun onDestroy() {
        if (isDestroyed) return
        isDestroyed = true
        
        Log.d(TAG, "onDestroy - cleaning up Filament resources")
        choreographer?.removeFrameCallback(frameCallback)
        
        // CRITICAL: Detach DisplayHelper BEFORE destroying renderer to prevent callbacks
        displayHelper?.detach()
        displayHelper = null
        
        // Detach UI helper
        uiHelper?.detach()
        uiHelper = null
        
        // Clean up in reverse order
        swapChain?.let { engine?.destroySwapChain(it) }
        swapChain = null
        
        asset?.let { assetLoader?.destroyAsset(it) }
        asset = null
        
        scene?.let { engine?.destroyScene(it) }
        scene = null
        view?.let { engine?.destroyView(it) }
        view = null
        camera?.let { engine?.destroyCameraComponent(cameraEntity) }
        EntityManager.get().destroy(cameraEntity)
        camera = null
        
        renderer?.let { engine?.destroyRenderer(it) }
        renderer = null
        
        materialProvider?.destroyMaterials()
        materialProvider?.destroy()
        materialProvider = null
        assetLoader?.destroy()
        assetLoader = null
        resourceLoader?.destroy()
        resourceLoader = null
        
        engine?.destroy()
        engine = null
        
        Log.d(TAG, "onDestroy - cleanup complete")
    }
}
