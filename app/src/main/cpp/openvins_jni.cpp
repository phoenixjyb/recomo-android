#include <jni.h>
#include <android/log.h>
#include <memory>
#include <fstream>
#include <iomanip>
#include <map>

#if defined(OPENVINS_DEPS_AVAILABLE)
#include <Eigen/Core>
#include <boost/filesystem.hpp>
#include <opencv2/core.hpp>
#endif

#if defined(OPENVINS_ENABLE)
#include "core/VioManager.h"
#include "core/VioManagerOptions.h"
#include "state/State.h"
#include "utils/sensor_data.h"
#include "utils/dataset_reader.h"
#include "utils/opencv_yaml_parse.h"
#endif

#define LOG_TAG "openvins_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if defined(OPENVINS_ENABLE)
struct OpenVinsSession {
    std::shared_ptr<ov_msckf::VioManager> vioManager;
    std::string outputDir;
    std::ofstream trajFile;
    double t0 = -1.0;
    int frameCount = 0;
    int imuCount = 0;
    bool initialized = false;
};

static std::map<jlong, std::shared_ptr<OpenVinsSession>> g_sessions;
static jlong g_nextHandle = 1;
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeVersion(
    JNIEnv* env,
    jobject /*thiz*/) {
#if defined(OPENVINS_ENABLE)
    return env->NewStringUTF("openvins-0.1.0");
#else
    return env->NewStringUTF("stub-0.1");
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeInit(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring configPath,
    jstring outputDir) {
#if defined(OPENVINS_ENABLE)
    const char* config_path = env->GetStringUTFChars(configPath, nullptr);
    const char* output_dir = env->GetStringUTFChars(outputDir, nullptr);
    
    try {
        LOGI("OpenVINS init: config=%s, output=%s", config_path, output_dir);
        
        // Parse config
        auto parser = std::make_shared<ov_core::YamlParser>(config_path);
        ov_msckf::VioManagerOptions params;
        params.print_and_load(parser);
        
        // Create session
        auto session = std::make_shared<OpenVinsSession>();
        session->outputDir = output_dir;
        session->vioManager = std::make_shared<ov_msckf::VioManager>(params);
        
        // Open trajectory file
        std::string trajPath = std::string(output_dir) + "/trajectory_tum.txt";
        session->trajFile.open(trajPath);
        if (!session->trajFile.is_open()) {
            LOGE("Failed to open trajectory file: %s", trajPath.c_str());
            env->ReleaseStringUTFChars(configPath, config_path);
            env->ReleaseStringUTFChars(outputDir, output_dir);
            return 0;
        }
        
        session->trajFile << std::fixed << std::setprecision(6);
        session->trajFile << "# timestamp tx ty tz qx qy qz qw\n";
        
        // Store session
        jlong handle = g_nextHandle++;
        g_sessions[handle] = session;
        
        LOGI("OpenVINS initialized: handle=%lld", (long long)handle);
        env->ReleaseStringUTFChars(configPath, config_path);
        env->ReleaseStringUTFChars(outputDir, output_dir);
        return handle;
        
    } catch (const std::exception& e) {
        LOGE("OpenVINS init failed: %s", e.what());
        env->ReleaseStringUTFChars(configPath, config_path);
        env->ReleaseStringUTFChars(outputDir, output_dir);
        return 0;
    }
#else
    (void)env; (void)configPath; (void)outputDir;
    LOGW("OpenVINS not enabled");
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeFeedImu(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jlong timestampNs,
    jfloat gx, jfloat gy, jfloat gz,
    jfloat ax, jfloat ay, jfloat az) {
#if defined(OPENVINS_ENABLE)
    (void)env;
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        LOGE("Invalid handle: %lld", (long long)handle);
        return 1;
    }
    
    auto session = it->second;
    
    try {
        // Set t0 from first IMU sample
        if (session->t0 < 0) {
            session->t0 = timestampNs / 1e9;
            LOGI("Set t0 = %.6f", session->t0);
        }
        
        double timestamp = (timestampNs / 1e9) - session->t0;
        
        // Create IMU data
        ov_core::ImuData imu;
        imu.timestamp = timestamp;
        imu.wm << gx, gy, gz;
        imu.am << ax, ay, az;
        
        // Feed to VioManager
        session->vioManager->feed_measurement_imu(imu);
        session->imuCount++;
        
        return 0;
        
    } catch (const std::exception& e) {
        LOGE("Failed to feed IMU: %s", e.what());
        return 2;
    }
#else
    (void)env; (void)handle; (void)timestampNs;
    (void)gx; (void)gy; (void)gz; (void)ax; (void)ay; (void)az;
    return 3;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeFeedFrame(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jlong timestampNs,
    jint width, jint height,
    jbyteArray grayBytes) {
#if defined(OPENVINS_ENABLE)
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        LOGE("Invalid handle: %lld", (long long)handle);
        return 1;
    }
    
    auto session = it->second;
    
    try {
        if (session->t0 < 0) {
            LOGW("Feed IMU before frames");
            return 4;
        }
        
        double timestamp = (timestampNs / 1e9) - session->t0;
        
        // Get grayscale bytes
        jsize len = env->GetArrayLength(grayBytes);
        if (len != width * height) {
            LOGE("Invalid frame data size: expected %d, got %d", width * height, len);
            return 2;
        }
        
        jbyte* bytes = env->GetByteArrayElements(grayBytes, nullptr);
        if (bytes == nullptr) {
            LOGE("Failed to get byte array elements");
            return 2;
        }
        
        // Create OpenCV Mat and clone immediately
        cv::Mat gray(height, width, CV_8UC1, (void*)bytes);
        cv::Mat grayClone = gray.clone();
        
        // Release JNI array before calling OpenVINS (in case of exceptions)
        env->ReleaseByteArrayElements(grayBytes, bytes, JNI_ABORT);
        bytes = nullptr;
        
        // Verify the image is valid
        if (grayClone.empty() || grayClone.rows != height || grayClone.cols != width) {
            LOGE("Invalid image after clone: empty=%d, rows=%d, cols=%d", 
                 grayClone.empty(), grayClone.rows, grayClone.cols);
            return 2;
        }
        
        // Create CameraData
        ov_core::CameraData camData;
        camData.timestamp = timestamp;
        camData.sensor_ids.push_back(0); // camera id 0
        camData.images.push_back(grayClone);
        camData.masks.push_back(cv::Mat::zeros(height, width, CV_8UC1)); // Use zero mask instead of empty
        
        LOGI("Feeding frame %d: ts=%.3f, size=%dx%d", 
             session->frameCount, timestamp, width, height);
        
        // Feed to VioManager
        session->vioManager->feed_measurement_camera(camData);
        session->frameCount++;
        
        // Check if initialized and write pose
        if (session->vioManager->initialized()) {
            if (!session->initialized) {
                LOGI("VioManager initialized after %d frames", session->frameCount);
                session->initialized = true;
            }
            
            // Get current state
            auto state = session->vioManager->get_state();
            if (state == nullptr) {
                LOGW("State is null after initialization");
                return 0;
            }
            auto imu = state->_imu;
            if (imu == nullptr) {
                LOGW("IMU state is null");
                return 0;
            }
            
            // Get pose (IMU in global frame)
            Eigen::Matrix<double,3,1> p_IinG = imu->pos();
            Eigen::Matrix<double,4,1> q_GtoI = imu->quat();
            
            // Write TUM format: timestamp tx ty tz qx qy qz qw
            session->trajFile << timestamp << " "
                            << p_IinG(0) << " " << p_IinG(1) << " " << p_IinG(2) << " "
                            << q_GtoI(0) << " " << q_GtoI(1) << " " << q_GtoI(2) << " " << q_GtoI(3) << "\n";
        }
        
        return 0;
        
    } catch (const std::exception& e) {
        LOGE("Failed to feed frame: %s", e.what());
        return 3;
    } catch (...) {
        LOGE("Failed to feed frame: unknown exception");
        return 3;
    }
#else
    (void)env; (void)handle; (void)timestampNs; (void)width; (void)height; (void)grayBytes;
    return 5;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeFinalize(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle) {
#if defined(OPENVINS_ENABLE)
    (void)env;
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        LOGE("Invalid handle: %lld", (long long)handle);
        return 1;
    }
    
    auto session = it->second;
    
    try {
        // Flush and close trajectory file
        if (session->trajFile.is_open()) {
            session->trajFile.flush();
            session->trajFile.close();
        }
        
        LOGI("OpenVINS finalized: %d frames, %d IMU samples, initialized=%d",
             session->frameCount, session->imuCount, session->initialized ? 1 : 0);
        
        return 0;
        
    } catch (const std::exception& e) {
        LOGE("Failed to finalize: %s", e.what());
        return 2;
    }
#else
    (void)env; (void)handle;
    return 3;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeRelease(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle) {
#if defined(OPENVINS_ENABLE)
    (void)env;
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        LOGW("Invalid handle for release: %lld", (long long)handle);
        return;
    }
    
    g_sessions.erase(it);
    LOGI("OpenVINS session released: handle=%lld", (long long)handle);
#else
    (void)env; (void)handle;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_run(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring sessionDir,
    jstring videoPath,
    jstring imuPath,
    jstring frameTimestampsPath,
    jstring calibPath,
    jstring outputDir) {
    const char* session_dir = env->GetStringUTFChars(sessionDir, nullptr);
    const char* video_path = env->GetStringUTFChars(videoPath, nullptr);
    const char* imu_path = env->GetStringUTFChars(imuPath, nullptr);
    const char* frame_path = env->GetStringUTFChars(frameTimestampsPath, nullptr);
    const char* calib_path = env->GetStringUTFChars(calibPath, nullptr);
    const char* output_dir = env->GetStringUTFChars(outputDir, nullptr);

    LOGI("OpenVINS stub called");
    LOGI("sessionDir=%s", session_dir ? session_dir : "");
    LOGI("video=%s", video_path ? video_path : "");
    LOGI("imu=%s", imu_path ? imu_path : "");
    LOGI("frames=%s", frame_path ? frame_path : "");
    LOGI("calib=%s", calib_path ? calib_path : "");
    LOGI("outputDir=%s", output_dir ? output_dir : "");

    if (session_dir) env->ReleaseStringUTFChars(sessionDir, session_dir);
    if (video_path) env->ReleaseStringUTFChars(videoPath, video_path);
    if (imu_path) env->ReleaseStringUTFChars(imuPath, imu_path);
    if (frame_path) env->ReleaseStringUTFChars(frameTimestampsPath, frame_path);
    if (calib_path) env->ReleaseStringUTFChars(calibPath, calib_path);
    if (output_dir) env->ReleaseStringUTFChars(outputDir, output_dir);

    // Return 1 to signal not implemented for now.
    return 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_recomo_remotecontrol_v3dr_vio_openvins_OpenVinsNative_nativeSmokeTest(
    JNIEnv* env,
    jobject /*thiz*/) {
#if defined(OPENVINS_DEPS_AVAILABLE)
    (void)env;
    try {
        Eigen::Matrix3f eigen_mat = Eigen::Matrix3f::Identity();
        boost::filesystem::path p("/data/local/tmp");
        cv::Mat mat(2, 2, CV_8UC1);
        mat.setTo(1);
        const double sum_val = cv::sum(mat)[0];
        const std::string build_info = cv::getBuildInformation();
        (void)p;
        (void)eigen_mat;
        (void)sum_val;
        (void)build_info;
        return 0;
    } catch (...) {
        LOGW("OpenVINS deps smoke test failed");
        return 3;
    }
#else
    (void)env;
    LOGW("OpenVINS deps smoke test unavailable (missing OpenCV/Boost/Eigen)");
    return 2;
#endif
}
