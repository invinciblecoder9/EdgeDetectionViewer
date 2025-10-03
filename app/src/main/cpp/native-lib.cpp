#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include "EdgeDetector.h"

// Global edge detector instance
EdgeDetector* g_edgeDetector = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_processors_NativeProcessor_initNative(
        JNIEnv* env, jobject /* this */) {
if (g_edgeDetector == nullptr) {
g_edgeDetector = new EdgeDetector();
LOGI("Native processor initialized");
}
}

JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_processors_NativeProcessor_releaseNative(
        JNIEnv* env, jobject /* this */) {
if (g_edgeDetector != nullptr) {
delete g_edgeDetector;
g_edgeDetector = nullptr;
LOGI("Native processor released");
}
}

JNIEXPORT jdouble JNICALL
        Java_com_example_edgedetectionviewer_processors_NativeProcessor_processFrame(
        JNIEnv* env, jobject /* this */, jobject srcBitmap, jobject dstBitmap) {

if (g_edgeDetector == nullptr) {
LOGE("EdgeDetector not initialized!");
return -1.0;
}

AndroidBitmapInfo srcInfo, dstInfo;
void* srcPixels = nullptr;
void* dstPixels = nullptr;

// Get bitmap info
if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0) {
LOGE("Failed to get source bitmap info");
return -1.0;
}

if (AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) < 0) {
LOGE("Failed to get destination bitmap info");
return -1.0;
}

// Lock bitmap pixels
if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0) {
LOGE("Failed to lock source pixels");
return -1.0;
}

if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) < 0) {
AndroidBitmap_unlockPixels(env, srcBitmap);
LOGE("Failed to lock destination pixels");
return -1.0;
}

// Process frame
double processingTime = g_edgeDetector->processFrame(
        static_cast<unsigned char*>(srcPixels),
        srcInfo.width,
        srcInfo.height,
        static_cast<unsigned char*>(dstPixels)
);

// Unlock pixels
AndroidBitmap_unlockPixels(env, srcBitmap);
AndroidBitmap_unlockPixels(env, dstBitmap);

return processingTime;
}

JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_processors_NativeProcessor_setProcessingEnabled(
        JNIEnv* env, jobject /* this */, jboolean enabled) {
if (g_edgeDetector != nullptr) {
g_edgeDetector->setProcessingEnabled(enabled);
}
}

JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_processors_NativeProcessor_setThresholds(
        JNIEnv* env, jobject /* this */, jdouble lowThreshold, jdouble highThreshold) {
if (g_edgeDetector != nullptr) {
g_edgeDetector->setThresholds(lowThreshold, highThreshold);
}
}

} // extern "C"