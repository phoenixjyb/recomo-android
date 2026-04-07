package com.recomo.remotecontrol.v3dr.vio.openvins

import android.util.Log
import com.recomo.remotecontrol.v3dr.recording.CalibWriter
import com.recomo.remotecontrol.v3dr.vio.SyncPacket
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Generates OpenVINS YAML configuration files from session data.
 */
class OpenVinsConfigWriter {
    companion object {
        private const val TAG = "OpenVinsConfigWriter"
        
        fun writeConfigs(
            outputDir: File,
            calibFile: File?,
            videoWidth: Int,
            videoHeight: Int,
            syncPackets: List<SyncPacket>
        ): File? {
            return try {
                outputDir.mkdirs()
                
                val calib = parseCalib(calibFile)
                val imuRate = computeImuRate(syncPackets)
                
                val estimatorConfig = File(outputDir, "estimator_config.yaml")
                val imuConfig = File(outputDir, "kalibr_imu_chain.yaml")
                val imucamConfig = File(outputDir, "kalibr_imucam_chain.yaml")
                
                writeEstimatorConfig(estimatorConfig, imuConfig, imucamConfig)
                writeImuConfig(imuConfig, calib, imuRate)
                writeImucamConfig(imucamConfig, calib, videoWidth, videoHeight)
                
                Log.i(TAG, "OpenVINS configs written to $outputDir")
                estimatorConfig
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write OpenVINS configs", e)
                null
            }
        }
        
        private fun parseCalib(calibFile: File?): CalibWriter.VioCalib? {
            if (calibFile == null || !calibFile.exists()) return null
            return try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<CalibWriter.VioCalib>(calibFile.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse calib.json", e)
                null
            }
        }
        
        private fun computeImuRate(syncPackets: List<SyncPacket>): Int {
            if (syncPackets.size < 2) return 200 // default
            
            val totalSamples = syncPackets.sumOf { it.imuSamples.size }
            if (totalSamples < 2) return 200
            
            val firstTs = syncPackets.first().imuSamples.firstOrNull()?.timestampNs ?: return 200
            val lastTs = syncPackets.last().imuSamples.lastOrNull()?.timestampNs ?: return 200
            val durationS = (lastTs - firstTs) / 1e9
            
            return if (durationS > 0) {
                (totalSamples / durationS).toInt().coerceIn(50, 1000)
            } else {
                200
            }
        }
        
        private fun writeEstimatorConfig(
            file: File,
            imuConfigPath: File,
            imucamConfigPath: File
        ) {
            val content = """
%YAML:1.0

verbosity: "INFO"
use_fej: true
use_imuavg: true
use_rk4int: true
use_stereo: false
max_cameras: 1

calib_cam_extrinsics: false
calib_cam_intrinsics: false
calib_cam_timeoffset: false
calib_imu_intrinsics: false
calib_imu_g_sensitivity: false

max_clones: 11
max_slam: 50
max_slam_in_update: 25
max_msckf_in_update: 50
dt_slam_delay: 1

gravity_mag: 9.81

feat_rep_msckf: "GLOBAL_3D"
feat_rep_slam: "GLOBAL_3D"
feat_rep_aruco: "GLOBAL_3D"

init_window_time: 2.0
init_imu_thresh: 1.5

init_max_disparity: 1.5
init_max_features: 50

record_timing_information: false
record_timing_filepath: "/tmp/traj_timing.txt"

save_total_state: false
filepath_est: "/tmp/ov_estimate.txt"
filepath_std: "/tmp/ov_std.txt"
filepath_gt: "/tmp/ov_groundtruth.txt"

use_aruco: false

num_pts: 200
fast_threshold: 20
grid_x: 5
grid_y: 5
min_px_dist: 15
knn_ratio: 0.70
track_frequency: 21.0
downsample_cameras: false
multi_threading: false
histogram_method: "HISTOGRAM"

fi_max_dist: 200
fi_max_baseline: 200
fi_max_cond_number: 10000

fi_triangulate_1d: false
fi_refine_features: true
fi_max_runs: 5
fi_init_lamda: 1e-3
fi_max_lamda: 1e10
fi_min_dx: 1e-6
fi_min_dcost: 1e-6
fi_lam_mult: 10
fi_min_dist: 0.25
fi_max_dist_ratio: 2.0

# Paths
relative_config_imu: "${imuConfigPath.name}"
relative_config_imucam: "${imucamConfigPath.name}"

try_zupt: false
zupt_chi2_multipler: 0
zupt_max_velocity: 1.0
zupt_noise_multiplier: 10
zupt_max_disparity: 0.5

# IMU noises (default values, will be overridden by imu config)
gyroscope_noise_density: 1.6968e-04
gyroscope_random_walk: 1.9393e-05
accelerometer_noise_density: 2.0000e-03
accelerometer_random_walk: 3.0000e-03

up_msckf_sigma_px: 1.0
up_msckf_chi2_multipler: 1.0
up_slam_sigma_px: 1.0
up_slam_chi2_multipler: 1.0
up_aruco_sigma_px: 1.0
up_aruco_chi2_multipler: 1.0
            """.trimIndent()
            file.writeText(content)
        }
        
        private fun writeImuConfig(
            file: File,
            calib: CalibWriter.VioCalib?,
            imuRate: Int
        ) {
            val noiseGyr = calib?.imu?.noiseGyro?.get(0) ?: 1.6968e-04f
            val noiseAcc = calib?.imu?.noiseAccel?.get(0) ?: 2.0e-03f
            val biasGyr = calib?.imu?.biasGyro?.get(0) ?: 1.9393e-05f
            val biasAcc = calib?.imu?.biasAccel?.get(0) ?: 3.0e-03f
            
            val content = """
%YAML:1.0

imu0:
  T_i_b:
    - [1.0, 0.0, 0.0, 0.0]
    - [0.0, 1.0, 0.0, 0.0]
    - [0.0, 0.0, 1.0, 0.0]
    - [0.0, 0.0, 0.0, 1.0]
  accelerometer_noise_density: $noiseAcc
  accelerometer_random_walk: $biasAcc
  gyroscope_noise_density: $noiseGyr
  gyroscope_random_walk: $biasGyr
  model: calibrated
  rostopic: /imu0
  time_offset: 0.0
  update_rate: $imuRate
            """.trimIndent()
            file.writeText(content)
        }
        
        private fun writeImucamConfig(
            file: File,
            calib: CalibWriter.VioCalib?,
            width: Int,
            height: Int
        ) {
            val camera = calib?.camera
            val fx = camera?.fx ?: (width * 0.8f)
            val fy = camera?.fy ?: (width * 0.8f)
            val cx = camera?.cx ?: (width / 2.0f)
            val cy = camera?.cy ?: (height / 2.0f)
            
            val content = """
%YAML:1.0

cam0:
  T_cam_imu:
    - [1.0, 0.0, 0.0, 0.0]
    - [0.0, 1.0, 0.0, 0.0]
    - [0.0, 0.0, 1.0, 0.0]
    - [0.0, 0.0, 0.0, 1.0]
  cam_overlaps: []
  camera_model: pinhole
  distortion_coeffs: [0.0, 0.0, 0.0, 0.0]
  distortion_model: radtan
  intrinsics: [$fx, $fy, $cx, $cy]
  resolution: [$width, $height]
  rostopic: /cam0/image_raw
  timeshift_cam_imu: 0.0
            """.trimIndent()
            file.writeText(content)
        }
    }
}
