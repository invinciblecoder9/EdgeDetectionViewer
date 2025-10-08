#ifndef EDGE_DETECTOR_H
#define EDGE_DETECTOR_H

#include <opencv2/opencv.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>
#include <chrono>

#define LOG_TAG "EdgeDetector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class EdgeDetector {
public:
    EdgeDetector();
    ~EdgeDetector();

    /**
     * Process image using Canny edge detection from NV21 data
     * @param nv21Data: Input NV21 data
     * @param width: Image width
     * @param height: Image height
     * @param dstData: Output image data (RGBA)
     * @return Processing time in milliseconds
     */
    double processFrame(unsigned char* nv21Data, int width, int height, unsigned char* dstData);

    /**
     * Set Canny edge detection thresholds
     */
    void setThresholds(double lowThreshold, double highThreshold);

    /**
     * Toggle between raw and processed mode
     */
    void setProcessingEnabled(bool enabled);

private:
    double lowThreshold;
    double highThreshold;
    bool processingEnabled;
    cv::Mat yuvMat;
    cv::Mat gray;
    cv::Mat blur;
    cv::Mat edges;
    cv::Mat strongEdges;
};

#endif // EDGE_DETECTOR_H