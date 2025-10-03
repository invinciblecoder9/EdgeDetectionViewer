#ifndef EDGE_DETECTOR_H
#define EDGE_DETECTOR_H

#include <opencv2/opencv.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "EdgeDetector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class EdgeDetector {
public:
    EdgeDetector();
    ~EdgeDetector();

    /**
     * Process image using Canny edge detection
     * @param srcData: Input image data (RGBA)
     * @param width: Image width
     * @param height: Image height
     * @param dstData: Output image data (RGBA)
     * @return Processing time in milliseconds
     */
    double processFrame(unsigned char* srcData, int width, int height, unsigned char* dstData);

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
    cv::Mat tempGray;
    cv::Mat edges;
};

#endif // EDGE_DETECTOR_H
