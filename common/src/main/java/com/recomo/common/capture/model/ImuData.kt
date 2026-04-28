package com.recomo.common.capture.model

/**
 * Represents a single IMU sample from CSV file
 */
data class ImuSample(
    val timestampNs: Long,
    val wallTimeMs: Long,
    val accel: ImuVector,
    val accelAccuracy: Int,
    val gyro: ImuVector,
    val gyroAccuracy: Int
) {
    /**
     * Get timestamp in milliseconds (relative to first sample)
     */
    fun getRelativeTimeMs(startTimestampNs: Long): Long {
        return (timestampNs - startTimestampNs) / 1_000_000
    }
}

/**
 * Parsed IMU data from entire recording session
 */
data class ImuDataSet(
    val samples: List<ImuSample>,
    val startTimestampNs: Long,
    val endTimestampNs: Long,
    val durationMs: Long,
    val sampleRate: Float
) {
    companion object {
        /**
         * Parse IMU data from CSV file
         * CSV format: timestamp_ns,wall_time_ms,accel_x,accel_y,accel_z,accel_accuracy,gyro_x,gyro_y,gyro_z,gyro_accuracy
         */
        fun fromCsvFile(csvContent: String): ImuDataSet? {
            try {
                val lines = csvContent.lines().filter { it.isNotBlank() }
                if (lines.size < 2) return null // Need header + at least 1 sample
                
                val samples = lines.drop(1).mapNotNull { line ->
                    val parts = line.split(',')
                    if (parts.size < 10) return@mapNotNull null
                    
                    try {
                        ImuSample(
                            timestampNs = parts[0].toLong(),
                            wallTimeMs = parts[1].toLong(),
                            accel = ImuVector(
                                x = parts[2].toFloat(),
                                y = parts[3].toFloat(),
                                z = parts[4].toFloat()
                            ),
                            accelAccuracy = parts[5].toInt(),
                            gyro = ImuVector(
                                x = parts[6].toFloat(),
                                y = parts[7].toFloat(),
                                z = parts[8].toFloat()
                            ),
                            gyroAccuracy = parts[9].toInt()
                        )
                    } catch (e: NumberFormatException) {
                        null
                    }
                }
                
                if (samples.isEmpty()) return null
                
                val startTimestamp = samples.first().timestampNs
                val endTimestamp = samples.last().timestampNs
                val durationMs = (endTimestamp - startTimestamp) / 1_000_000
                val sampleRate = if (durationMs > 0) {
                    (samples.size * 1000f) / durationMs
                } else {
                    0f
                }
                
                return ImuDataSet(
                    samples = samples,
                    startTimestampNs = startTimestamp,
                    endTimestampNs = endTimestamp,
                    durationMs = durationMs,
                    sampleRate = sampleRate
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
    
    /**
     * Get IMU sample at specific time offset (in milliseconds)
     * Uses binary search for efficiency
     */
    fun getSampleAt(timeMs: Long): ImuSample? {
        if (samples.isEmpty()) return null
        if (timeMs < 0) return samples.first()
        
        val targetNs = startTimestampNs + (timeMs * 1_000_000)
        if (targetNs >= endTimestampNs) return samples.last()
        
        // Binary search for closest sample
        var left = 0
        var right = samples.size - 1
        
        while (left < right) {
            val mid = (left + right) / 2
            if (samples[mid].timestampNs < targetNs) {
                left = mid + 1
            } else {
                right = mid
            }
        }
        
        return samples[left]
    }
    
    /**
     * Get samples in a time range (for charting)
     */
    fun getSamplesInRange(startMs: Long, endMs: Long): List<ImuSample> {
        val startNs = startTimestampNs + (startMs * 1_000_000)
        val endNs = startTimestampNs + (endMs * 1_000_000)
        
        return samples.filter { it.timestampNs in startNs..endNs }
    }
}
