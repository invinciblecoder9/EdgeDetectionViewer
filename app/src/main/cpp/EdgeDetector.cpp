#include "EdgeDetector.h"

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
            cv::Mat gray;
            cv::cvtColor(srcMat, gray, cv::COLOR_RGBA2GRAY);

            // Apply Gaussian blur to reduce noise
            cv::GaussianBlur(gray, gray, cv::Size(5, 5), 1.5);

            // Apply Canny edge detection
            cv::Mat edges;
            cv::Canny(gray, edges, lowThreshold, highThreshold);

            // Convert back to RGBA for display
            cv::cvtColor(edges, dstMat, cv::COLOR_GRAY2RGBA);
        }

        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);

        return static_cast<double>(duration.count());

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception: %s", e.what());
        return -1.0;
    } catch (...) {
        LOGE("Unknown exception in processFrame");
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
