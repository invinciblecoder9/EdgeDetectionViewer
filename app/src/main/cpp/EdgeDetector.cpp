#include "EdgeDetector.h"
#include <chrono>

EdgeDetector::EdgeDetector()
        : lowThreshold(50.0), highThreshold(150.0), processingEnabled(true) {
    LOGI("EdgeDetector initialized");
}

EdgeDetector::~EdgeDetector() {
    LOGI("EdgeDetector destroyed");
}

double EdgeDetector::processFrame(unsigned char* srcData, int width, int height, unsigned char* dstData) {
    auto start = std::chrono::high_resolution_clock::now();

    try {
        // Create Mat from input data (RGBA format)
        cv::Mat srcMat(height, width, CV_8UC4, srcData);
        cv::Mat dstMat(height, width, CV_8UC4, dstData);

        if (!processingEnabled) {
            // Just copy the original frame
            srcMat.copyTo(dstMat);
        } else {
            // Convert to grayscale
            cv::cvtColor(srcMat, tempGray, cv::COLOR_RGBA2GRAY);

            // Apply Gaussian blur to reduce noise
            cv::GaussianBlur(tempGray, tempGray, cv::Size(5, 5), 1.5);

            // Apply Canny edge detection
            cv::Canny(tempGray, edges, lowThreshold, highThreshold);

            // Convert back to RGBA for display
            // White edges on black background
            cv::cvtColor(edges, dstMat, cv::COLOR_GRAY2RGBA);
        }

        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);

        return duration.count();

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception: %s", e.what());
        return -1.0;
    }
}

void EdgeDetector::setThresholds(double low, double high) {
    this->lowThreshold = low;
    this->highThreshold = high;
    LOGI("Thresholds updated: low=%f, high=%f", low, high);
}

void EdgeDetector::setProcessingEnabled(bool enabled) {
    this->processingEnabled = enabled;
    LOGI("Processing enabled: %d", enabled);
}
