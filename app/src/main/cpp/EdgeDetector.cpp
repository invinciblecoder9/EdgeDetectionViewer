#include "EdgeDetector.h"

EdgeDetector::EdgeDetector()
        : lowThreshold(50.0), highThreshold(150.0), processingEnabled(true) {
    LOGI("EdgeDetector initialized");
}

EdgeDetector::~EdgeDetector() {
    LOGI("EdgeDetector destroyed");
}

double EdgeDetector::processFrame(unsigned char* nv21Data, int width, int height, unsigned char* dstData) {
    auto start = std::chrono::high_resolution_clock::now();

    try {
        // Reusable Mats
        if (yuvMat.size() != cv::Size(width, height * 3 / 2)) {
            yuvMat = cv::::Mat(height * 3 / 2, width, CV_8UC1);
            gray = cv::Mat(height, width, CV_8UC1);
            blur = cv::Mat(height, width, CV_8UC1);
            edges = cv::Mat(height, width, CV_8UC1);
            strongEdges = cv::Mat(height, width, CV_8UC1);
        }

        // Assign NV21 data
        yuvMat.data = nv21Data;

        if (!processingEnabled) {
            // Copy original (but from NV21, convert to RGBA)
            cv::cvtColor(yuvMat, cv::Mat(height, width, CV_8UC4, dstData), cv::COLOR_YUV2RGBA_NV21);
        } else {
            // Convert to grayscale
            cv::cvtColor(yuvMat, gray, cv::COLOR_YUV2GRAY_NV21);

            // Equalize histogram
            cv::equalizeHist(gray, gray);

            // Gaussian blur
            cv::GaussianBlur(gray, blur, cv::Size(5, 5), 1.5);

            // Canny
            cv::Canny(blur, edges, lowThreshold, highThreshold, 3);

            // Threshold and dilate
            cv::threshold(edges, strongEdges, 50, 255, cv::THRESH_BINARY);
            cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3));
            cv::dilate(strongEdges, strongEdges, kernel, cv::Point(-1, -1), 1);

            // To RGBA
            cv::cvtColor(strongEdges, cv::Mat(height, width, CV_8UC4, dstData), cv::COLOR_GRAY2RGBA);
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