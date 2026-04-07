package com.recomo.remotecontrol.v3dr.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.recomo.remotecontrol.v3dr.data.model.ImuTelemetry
import com.recomo.remotecontrol.v3dr.data.model.ImuVector
import java.io.File
import java.io.FileWriter
import java.io.IOException
import kotlin.math.max

/**
 * IMU logger that writes accelerometer and gyroscope data to CSV file.
 * Samples at target rate (default 100Hz) and synchronizes with video frames.
 */
class ImuLogger(
    context: Context,
    targetHz: Int = 100
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val minPeriodNs = max(1_000_000_000L / targetHz.coerceAtLeast(1), 1L)

    private val latestAccel = FloatArray(3)
    private val latestGyro = FloatArray(3)
    @Volatile private var accelAccuracy: Int = SensorManager.SENSOR_STATUS_NO_CONTACT
    @Volatile private var gyroAccuracy: Int = SensorManager.SENSOR_STATUS_NO_CONTACT
    @Volatile private var haveAccel = false
    @Volatile private var haveGyro = false
    @Volatile private var lastEmitNs = 0L
    @Volatile private var emittedThisWindow = 0
    private var lastLogNs = 0L
    
    private var csvWriter: FileWriter? = null
    private var outputFile: File? = null
    private var sampleCount = 0L

    companion object {
        private const val TAG = "ImuLogger"
        private const val CSV_HEADER = "timestamp_ns,wall_time_ms,accel_x,accel_y,accel_z,accel_accuracy,gyro_x,gyro_y,gyro_z,gyro_accuracy\n"
    }

    /**
     * Start logging IMU data to CSV file.
     * @param file Output CSV file
     */
    fun start(file: File) {
        if (accelSensor == null || gyroSensor == null) {
            Log.w(TAG, "IMU sensors missing (accel=${accelSensor != null}, gyro=${gyroSensor != null})")
            return
        }
        
        try {
            outputFile = file
            csvWriter = FileWriter(file).apply {
                write(CSV_HEADER)
            }
            sampleCount = 0L
            
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST)
            lastLogNs = SystemClock.elapsedRealtimeNanos()
            emittedThisWindow = 0
            
            Log.i(TAG, "IMU logger started: ${file.absolutePath} (target ${1_000_000_000L / minPeriodNs} Hz)")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start IMU logger", e)
            stop()
        }
    }

    /**
     * Stop logging and close CSV file.
     * @return The output file that was created, or null if logging wasn't started
     */
    fun stop(): File? {
        sensorManager.unregisterListener(this)
        haveAccel = false
        haveGyro = false
        emittedThisWindow = 0
        
        try {
            csvWriter?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing CSV file", e)
        }
        
        val file = outputFile
        csvWriter = null
        outputFile = null
        
        if (file != null) {
            Log.i(TAG, "IMU logger stopped: ${file.absolutePath} ($sampleCount samples)")
        }
        
        return file
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, latestAccel, 0, latestAccel.size)
                accelAccuracy = event.accuracy
                haveAccel = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, latestGyro, 0, latestGyro.size)
                gyroAccuracy = event.accuracy
                haveGyro = true
            }
            else -> return
        }
        
        if (!haveAccel || !haveGyro) return
        
        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (nowNs - lastEmitNs < minPeriodNs) return
        lastEmitNs = nowNs

        val timestampNs = event.timestamp
        val wallTimeMs = System.currentTimeMillis()
        
        try {
            // Write CSV row: timestamp_ns,wall_time_ms,accel_x,accel_y,accel_z,accel_accuracy,gyro_x,gyro_y,gyro_z,gyro_accuracy
            csvWriter?.write(
                "$timestampNs,$wallTimeMs," +
                "${latestAccel[0]},${latestAccel[1]},${latestAccel[2]},$accelAccuracy," +
                "${latestGyro[0]},${latestGyro[1]},${latestGyro[2]},$gyroAccuracy\n"
            )
            sampleCount++
            emittedThisWindow++
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write IMU sample", e)
        }

        if (nowNs - lastLogNs >= 1_000_000_000L) {
            Log.i(TAG, "IMU logging rate: ${emittedThisWindow} Hz (target >= 100 Hz, total $sampleCount samples)")
            emittedThisWindow = 0
            lastLogNs = nowNs
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        when (sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> accelAccuracy = accuracy
            Sensor.TYPE_GYROSCOPE -> gyroAccuracy = accuracy
        }
    }
    
    /**
     * Get current IMU telemetry snapshot (for display/monitoring).
     * Does not write to file.
     */
    fun getCurrentTelemetry(): ImuTelemetry? {
        if (!haveAccel || !haveGyro) return null
        
        return ImuTelemetry(
            timestampNs = SystemClock.elapsedRealtimeNanos(),
            wallTimeMs = System.currentTimeMillis(),
            accel = ImuVector(latestAccel[0], latestAccel[1], latestAccel[2]),
            accelAccuracy = accelAccuracy,
            gyro = ImuVector(latestGyro[0], latestGyro[1], latestGyro[2]),
            gyroAccuracy = gyroAccuracy,
            frame = "tablet_imu"
        )
    }
}
