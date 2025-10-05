//#include <jni.h>
//#include <string>
//#include <android/bitmap.h>
//#include "EdgeDetector.h"
//
//// Global edge detector instance
//EdgeDetector* g_edgeDetector = nullptr;
//
//extern "C" {
//
//JNIEXPORT void JNICALL
//Java_com_example_edgedetectionviewer_processors_NativeProcessor_initNative(
//        JNIEnv* env, jobject /* this */) {
//if (g_edgeDetector == nullptr) {
//g_edgeDetector = new EdgeDetector();
//LOGI("Native processor initialized");
//}
//}
//
//JNIEXPORT void JNICALL
//Java_com_example_edgedetectionviewer_processors_NativeProcessor_releaseNative(
//        JNIEnv* env, jobject /* this */) {
//if (g_edgeDetector != nullptr) {
//delete g_edgeDetector;
//g_edgeDetector = nullptr;
//LOGI("Native processor released");
//}
//}
//
//JNIEXPORT jdouble JNICALL
//        Java_com_example_edgedetectionviewer_processors_NativeProcessor_processFrame(
//        JNIEnv* env, jobject /* this */, jobject srcBitmap, jobject dstBitmap) {
//
//if (g_edgeDetector == nullptr) {
//LOGE("EdgeDetector not initialized!");
//return -1.0;
//}
//
//AndroidBitmapInfo srcInfo, dstInfo;
//void* srcPixels = nullptr;
//void* dstPixels = nullptr;
//
//// Get bitmap info
//if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0) {
//LOGE("Failed to get source bitmap info");
//return -1.0;
//}
//
//if (AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) < 0) {
//LOGE("Failed to get destination bitmap info");
//return -1.0;
//}
//
//// Lock bitmap pixels
//if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0) {
//LOGE("Failed to lock source pixels");
//return -1.0;
//}
//
//if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) < 0) {
//AndroidBitmap_unlockPixels(env, srcBitmap);
//LOGE("Failed to lock destination pixels");
//return -1.0;
//}
//
//// Process frame
//double processingTime = g_edgeDetector->processFrame(
//        static_cast<unsigned char*>(srcPixels),
//        srcInfo.width,
//        srcInfo.height,
//        static_cast<unsigned char*>(dstPixels)
//);
//
//// Unlock pixels
//AndroidBitmap_unlockPixels(env, srcBitmap);
//AndroidBitmap_unlockPixels(env, dstBitmap);
//
//return processingTime;
//}
//
//JNIEXPORT void JNICALL
//Java_com_example_edgedetectionviewer_processors_NativeProcessor_setProcessingEnabled(
//        JNIEnv* env, jobject /* this */, jboolean enabled) {
//if (g_edgeDetector != nullptr) {
//g_edgeDetector->setProcessingEnabled(enabled);
//}
//}
//
//JNIEXPORT void JNICALL
//Java_com_example_edgedetectionviewer_processors_NativeProcessor_setThresholds(
//        JNIEnv* env, jobject /* this */, jdouble lowThreshold, jdouble highThreshold) {
//if (g_edgeDetector != nullptr) {
//g_edgeDetector->setThresholds(lowThreshold, highThreshold);
//}
//}
//
//} // extern "C"


#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "EdgeDetection"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

JNIEXPORT jintArray JNICALL
Java_com_example_edgedetectionviewer_MainActivity_processFrameNative(
        JNIEnv *env,
        jobject thiz,
        jintArray pixels,
        jint width,
        jint height) {

    // Validate inputs
    if (pixels == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid input: pixels=%p, width=%d, height=%d", pixels, width, height);
        return nullptr;
    }

    // Get input pixel array (ARGB)
    jint *inputPixels = env->GetIntArrayElements(pixels, nullptr);
    if (inputPixels == nullptr) {
        LOGE("Failed to get input pixels");
        return nullptr;
    }

    jintArray result = nullptr;
    try {
        // Create Mat from input pixels (treated as uchar* ARGB)
        Mat inputMat(height, width, CV_8UC4, (unsigned char *) inputPixels);

        // Fix channel order: ARGB to BGRA for OpenCV
        Mat bgraMat;
        cvtColor(inputMat, bgraMat, COLOR_RGBA2BGR);  // ARGB misread as RGBA -> BGR
        cvtColor(bgraMat, bgraMat, COLOR_BGR2BGRA);   // Add alpha=255

        Mat grayMat, edgesMat;

        // Convert to grayscale (enhanced contrast)
        cvtColor(bgraMat, grayMat, COLOR_BGRA2GRAY);

        // Apply Gaussian blur (reduced kernel for less noise in live feed)
        Mat blurMat;
        GaussianBlur(grayMat, blurMat, Size(3, 3), 0.0);  // Milder blur

        // Apply Canny edge detection (lower thresholds for visibility)
        Canny(blurMat, edgesMat, 30, 100);  // More sensitive: low=30, high=100

        // Post-process: Boost weak edges (threshold >50 to full white)
        Mat strongEdges;
        threshold(edgesMat, strongEdges, 50, 255, THRESH_BINARY);

        // Convert to BGRA (white edges on black)
        Mat edgesBgra;
        cvtColor(strongEdges, edgesBgra, COLOR_GRAY2BGRA);

        // Convert BGRA to ARGB for Android (BGR -> RGB + A=255)
        Mat outputMat;
        cvtColor(edgesBgra, outputMat, COLOR_BGRA2BGR);
        cvtColor(outputMat, outputMat, COLOR_BGR2RGBA);

        // Create output array (ARGB)
        const int totalPixels = width * height;
        result = env->NewIntArray(totalPixels);
        if (result == nullptr) {
            LOGE("Failed to allocate output array (%d elements)", totalPixels);
            throw cv::Exception(0, "JNI allocation failed", "native-lib", __FILE__, __LINE__);
        }

        // Copy output data
        env->SetIntArrayRegion(result, 0, totalPixels, (jint *) outputMat.data);

        // Debug: Log average intensity (should >0 for visible edges)
        double avgIntensity = mean(strongEdges).val[0];
        LOGD("Frame stats: avg edge intensity=%.1f, non-zero pixels=%d/%d", avgIntensity, countNonZero(strongEdges), totalPixels);

        LOGI("✓ Processed frame: %dx%d (intensity=%.1f)", width, height, avgIntensity);

    } catch (cv::Exception &e) {
        LOGE("OpenCV error: %s", e.what());
        result = nullptr;
    } catch (...) {
        LOGE("Unknown JNI error");
        result = nullptr;
    }

    // Always release input
    env->ReleaseIntArrayElements(pixels, inputPixels, 0);

    return result;
}

} // extern "C"




