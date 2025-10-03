package com.example.edgedetectionviewer.processors

import android.graphics.Bitmap

class NativeProcessor {

    init {
        System.loadLibrary("native-processor")
        initNative()
    }

    /**
     * Process frame using native OpenCV edge detection
     * @return Processing time in milliseconds
     */
    external fun processFrame(srcBitmap: Bitmap, dstBitmap: Bitmap): Double

    /**
     * Initialize native resources
     */
    private external fun initNative()

    /**
     * Release native resources
     */
    external fun releaseNative()

    /**
     * Toggle between raw and processed mode
     */
    external fun setProcessingEnabled(enabled: Boolean)

    /**
     * Set Canny edge detection thresholds
     */
    external fun setThresholds(lowThreshold: Double, highThreshold: Double)
}
