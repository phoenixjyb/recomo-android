package com.recomo.user.data.video

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.recomo.user.data.media.UserMediaManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Instrumented test: feeds synthetic JPEG frames into UserVideoRecorder
 * in mjpeg mode and verifies a valid MP4 is produced.
 */
@RunWith(AndroidJUnit4::class)
class MjpegRecordingTest {

    private lateinit var recorder: UserVideoRecorder
    private lateinit var mediaManager: UserMediaManager
    private var outputPath: String? = null

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mediaManager = UserMediaManager(context)
        recorder = UserVideoRecorder(context, mediaManager)
    }

    @After
    fun cleanup() {
        outputPath?.let { File(it).delete() }
    }

    @Test
    fun mjpegRecording_produceValidMp4() = runBlocking {
        val width = 320
        val height = 240
        val fps = 10
        val frameCount = 30 // 3 seconds at 10fps

        // Start recording in mjpeg mode
        val path = recorder.startRecording(
            UserVideoRecorder.StartConfig(
                codec = "mjpeg",
                width = width,
                height = height,
                fps = fps
            )
        )
        assertNotNull("startRecording should return a path", path)
        outputPath = path

        // Feed synthetic JPEG frames with varying colors
        for (i in 0 until frameCount) {
            val jpeg = generateColorJpeg(width, height, frameIndex = i)
            recorder.writeJpegFrame(jpeg)
            // ~100ms between frames for 10fps
            kotlinx.coroutines.delay(50)
        }

        // Stop and verify
        val result = recorder.stopRecording()
        assertNotNull("stopRecording should return file info", result)

        val (filePath, fileSize) = result!!
        assertTrue("File should exist", File(filePath).exists())
        assertTrue("File should have data (got $fileSize bytes)", fileSize > 1000)

        // Verify it's a valid MP4 with MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            assertNotNull("MP4 should have duration metadata", durationStr)
            val durationMs = durationStr!!.toLong()
            assertTrue("Duration should be > 500ms (got ${durationMs}ms)", durationMs > 500)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            assertNotNull("MP4 should have width", widthStr)
            assertNotNull("MP4 should have height", heightStr)

            // Extract a frame to verify video has content
            val frame = retriever.getFrameAtTime(0)
            assertNotNull("Should be able to extract a frame", frame)
        } finally {
            retriever.release()
        }
    }

    /**
     * Generate a JPEG with a solid color that changes per frame
     * so the encoder produces distinct frames.
     */
    private fun generateColorJpeg(width: Int, height: Int, frameIndex: Int): ByteArray {
        val hue = (frameIndex * 12f) % 360f
        val color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        // Add a moving stripe for visual variation
        val stripeY = (frameIndex * 8) % height
        for (x in 0 until width) {
            for (dy in 0 until 4.coerceAtMost(height - stripeY)) {
                bitmap.setPixel(x, stripeY + dy, Color.WHITE)
            }
        }
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }
}
