package com.recomo.remotecontrol.v3dr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.recomo.remotecontrol.v3dr.data.model.ImuDataSet
import com.recomo.remotecontrol.v3dr.data.model.ImuSample
import kotlin.math.sqrt

/**
 * Overlay showing real-time IMU data synchronized with video playback
 */
@Composable
fun MetadataOverlay(
    imuDataSet: ImuDataSet?,
    currentSample: ImuSample?,
    modifier: Modifier = Modifier
) {
    if (imuDataSet == null || currentSample == null) {
        // No IMU data available
        Box(
            modifier = modifier
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = "No IMU data available",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "IMU Data",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )

        // Dataset info
        Text(
            text = "Sample Rate: ${imuDataSet.sampleRate.toInt()} Hz  |  " +
                    "Duration: ${imuDataSet.durationMs / 1000f}s  |  " +
                    "${imuDataSet.samples.size} samples",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Accelerometer data
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Accelerometer (m/s²)",
                color = Color.Cyan,
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "X: ${String.format("%.3f", currentSample.accel.x)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Y: ${String.format("%.3f", currentSample.accel.y)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Z: ${String.format("%.3f", currentSample.accel.z)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            val accelMagnitude = sqrt(
                currentSample.accel.x * currentSample.accel.x +
                currentSample.accel.y * currentSample.accel.y +
                currentSample.accel.z * currentSample.accel.z
            )
            Text(
                text = "Magnitude: ${String.format("%.3f", accelMagnitude)} m/s²",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Gyroscope data
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Gyroscope (rad/s)",
                color = Color.Green,
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "X: ${String.format("%.3f", currentSample.gyro.x)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Y: ${String.format("%.3f", currentSample.gyro.y)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Z: ${String.format("%.3f", currentSample.gyro.z)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            val gyroMagnitude = sqrt(
                currentSample.gyro.x * currentSample.gyro.x +
                currentSample.gyro.y * currentSample.gyro.y +
                currentSample.gyro.z * currentSample.gyro.z
            )
            Text(
                text = "Magnitude: ${String.format("%.3f", gyroMagnitude)} rad/s",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Timestamp info
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Time: ${currentSample.getRelativeTimeMs(imuDataSet.startTimestampNs)}ms",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
