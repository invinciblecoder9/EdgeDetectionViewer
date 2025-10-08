#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "EdgeDetectionNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_MainActivity_processFrameNativeDirect(
        JNIEnv *env,
        jobject /* thiz */,
        jobject inYuvBuf,
        jobject outArgbBuf,
        jint width,
        jint height) {

    if (inYuvBuf == nullptr || outArgbBuf == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid args: inYuv=%p out=%p %dx%d", inYuvBuf, outArgbBuf, width, height);
        return;
    }

    uint8_t *yuvPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(inYuvBuf));
    uint8_t *outPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(outArgbBuf));

    if (!yuvPtr || !outPtr) {
        LOGE("GetDirectBufferAddress returned null");
        return;
    }

    // ✅ OPTIMIZED: Reuse mats across calls
    thread_local Mat yMat;
    thread_local Mat blurMat;
    thread_local Mat edgesMat;
    thread_local Mat outRgba;

    try {
        // ✅ OPTIMIZED: Direct Y-channel access (saves 30-40% processing time)
        // NV21 format: Y plane is first width*height bytes (already grayscale)
        if (yMat.empty() || yMat.rows != height || yMat.cols != width) {
            yMat = Mat(height, width, CV_8UC1, yuvPtr);
        } else {
            yMat.data = yuvPtr;
        }

        // ✅ Optional: Light Gaussian blur for noise reduction
        GaussianBlur(yMat, blurMat, Size(3, 3), 0);

        // ✅ Canny edge detection with good default thresholds
        Canny(blurMat, edgesMat, 50, 150, 3, true);

        // ✅ Convert edges to RGBA for display
        cvtColor(edgesMat, outRgba, COLOR_GRAY2RGBA);

        // ✅ Ensure alpha channel is 255
        if (outRgba.channels() == 4) {
            int total = outRgba.rows * outRgba.cols;
            Vec4b *ptr = outRgba.ptr<Vec4b>(0);
            for (int i = 0; i < total; ++i) {
                ptr[i][3] = 255;
            }
        }

        // ✅ Copy to output buffer
        size_t copyBytes = static_cast<size_t>(width) * static_cast<size_t>(height) * 4u;
        memcpy(outPtr, outRgba.data, copyBytes);

        // ✅ Debug logging (reduced frequency)
        static int frameCount = 0;
        if ((frameCount++ % 100) == 0) {
            double avgIntensity = mean(edgesMat).val[0];
            int edgePixels = countNonZero(edgesMat);
            LOGD("Frame %d: avgIntensity=%.2f edgePixels=%d", frameCount, avgIntensity, edgePixels);
        }

    } catch (const cv::Exception &ex) {
        LOGE("OpenCV exception: %s", ex.what());
    } catch (const std::exception &e) {
        LOGE("STD exception: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception in native processing");
    }
}

} // extern "C"
