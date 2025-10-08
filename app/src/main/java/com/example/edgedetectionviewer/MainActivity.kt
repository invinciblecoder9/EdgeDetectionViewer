package com.example.edgedetectionviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.opengl.GLSurfaceView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EdgeDetection"
        private const val CAMERA_PERMISSION_CODE = 100

        init {
            try {
                System.loadLibrary("native-lib")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native-lib.so", e)
            }
        }
    }

    external fun processFrameNativeDirect(
        inYuv: ByteBuffer,
        outArgb: ByteBuffer,
        width: Int,
        height: Int
    )

    private lateinit var container: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: EdgeDetectionRenderer
    private lateinit var toggleButton: FloatingActionButton
    private lateinit var fpsTextView: TextView
    private lateinit var cameraExecutor: ExecutorService

    private var yuvBuffer: ByteBuffer? = null
    private var outBuffer: ByteBuffer? = null
    private var outWidth = 0
    private var outHeight = 0

    private var processing = false
    private var isEdgeMode = false

    private var frameTimestamps = mutableListOf<Long>()
    private val fpsWindowSize = 10
    private var lastFpsUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV failed to initialize", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "OpenCV initialized successfully")

        // ✅ Main container
        container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ✅ Camera preview
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(previewView)

        // ✅ OpenGL Edge Renderer
        renderer = EdgeDetectionRenderer()
        glSurfaceView = GLSurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            visibility = FrameLayout.GONE
        }
        container.addView(glSurfaceView)

        // ✅ Toggle Button
        toggleButton = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setOnClickListener { toggleMode() }
            layoutParams = FrameLayout.LayoutParams(
                56.dpToPx(this@MainActivity),
                56.dpToPx(this@MainActivity),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
                rightMargin = 16.dpToPx(this@MainActivity)
            }
        }
        container.addView(toggleButton)

        // ✅ FPS Text
        fpsTextView = TextView(this).apply {
            text = "FPS: --"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0x80000000.toInt())
            setPadding(12, 8, 12, 8)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                topMargin = 16.dpToPx(this@MainActivity)
                leftMargin = 16.dpToPx(this@MainActivity)
            }
        }
        container.addView(fpsTextView)

        setContentView(container)

        // ✅ Camera Executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // ✅ Check Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun toggleMode() {
        isEdgeMode = !isEdgeMode
        previewView.visibility = if (isEdgeMode) FrameLayout.GONE else FrameLayout.VISIBLE
        glSurfaceView.visibility = if (isEdgeMode) FrameLayout.VISIBLE else FrameLayout.GONE
        toggleButton.setImageResource(
            if (isEdgeMode) android.R.drawable.ic_menu_gallery
            else android.R.drawable.ic_menu_camera
        )
        fpsTextView.text = "FPS: --"
        frameTimestamps.clear()
        Log.d(TAG, "Toggled to ${if (isEdgeMode) "EDGE" else "RAW"} mode")
        if (isEdgeMode) glSurfaceView.requestRender()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (!processing) {
                    processing = true
                    processImage(imageProxy)
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun ensureBuffers(w: Int, h: Int) {
        val nv21Size = w * h * 3 / 2
        val outSize = w * h * 4
        if (yuvBuffer == null || yuvBuffer!!.capacity() < nv21Size) {
            yuvBuffer = ByteBuffer.allocateDirect(nv21Size).order(ByteOrder.nativeOrder())
            Log.d(TAG, "Allocated YUV buffer: $nv21Size bytes")
        }
        if (outBuffer == null || outBuffer!!.capacity() < outSize) {
            outBuffer = ByteBuffer.allocateDirect(outSize).order(ByteOrder.nativeOrder())
            Log.d(TAG, "Allocated output buffer: $outSize bytes")
        }
        if (outWidth != w || outHeight != h) {
            outWidth = w
            outHeight = h
            renderer.setTextureSize(w, h)
        }
    }

    // ✅ FIXED: Proper NV21 conversion with stride handling
    private fun imageProxyToNV21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // Get stride information
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // NV21 size calculation
        val nv21Size = width * height + width * height / 2
        val nv21 = ByteArray(nv21Size)

        var idY = 0
        var idUV = width * height
        val uvWidth = width / 2
        val uvHeight = height / 2

        // Copy Y plane with stride handling
        yBuffer.rewind()
        for (y in 0 until height) {
            val yOffset = y * yRowStride
            for (x in 0 until width) {
                nv21[idY++] = yBuffer.get(yOffset + x * yPixelStride)
            }
        }

        // Copy UV planes with stride handling (interleaved as VU for NV21)
        uBuffer.rewind()
        vBuffer.rewind()
        for (y in 0 until uvHeight) {
            val uvOffset = y * uvRowStride
            for (x in 0 until uvWidth) {
                val bufferIndex = uvOffset + (x * uvPixelStride)
                // NV21 format: YYYYVUVUVU...
                try {
                    nv21[idUV++] = vBuffer.get(bufferIndex)  // V first
                    nv21[idUV++] = uBuffer.get(bufferIndex)  // U second
                } catch (e: Exception) {
                    // Handle edge case for buffer boundaries
                    Log.w(TAG, "Buffer index issue at UV: $bufferIndex")
                }
            }
        }

        return nv21
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val w = imageProxy.width
            val h = imageProxy.height

            // ✅ Debug: Log stride information (first time only)
            if (outWidth == 0) {
                val yPlane = imageProxy.planes[0]
                Log.d(TAG, "Image: ${w}x${h}, Y rowStride: ${yPlane.rowStride}, Y pixelStride: ${yPlane.pixelStride}")
                Log.d(TAG, "U rowStride: ${imageProxy.planes[1].rowStride}, U pixelStride: ${imageProxy.planes[1].pixelStride}")
            }

            ensureBuffers(w, h)

            // ✅ FIXED: Convert ImageProxy to NV21 with stride handling
            val nv21Data = imageProxyToNV21(imageProxy)

            // ✅ Debug: Verify buffer sizes
            if (nv21Data.size > yuvBuffer!!.capacity()) {
                Log.e(TAG, "NV21 size mismatch! nv21=${nv21Data.size}, buffer=${yuvBuffer!!.capacity()}")
                return
            }

            yuvBuffer!!.clear()
            yuvBuffer!!.put(nv21Data)
            yuvBuffer!!.rewind()

            // Process with native code
            processFrameNativeDirect(yuvBuffer!!, outBuffer!!, w, h)

            // Update display if in edge mode
            if (isEdgeMode) {
                renderer.updateTextureData(outBuffer!!, w, h)
                runOnUiThread { glSurfaceView.requestRender() }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Processing error: ${e.message}", e)
        } finally {
            // ✅ FIXED: Update FPS and close
            val currentTime = System.currentTimeMillis()
            updateFps(currentTime)
            imageProxy.close()
            processing = false
        }
    }

    // ✅ FIXED: Now actually called
    private fun updateFps(currentTime: Long) {
        frameTimestamps.add(currentTime)
        if (frameTimestamps.size > fpsWindowSize) frameTimestamps.removeAt(0)

        if (currentTime - lastFpsUpdate < 500) return
        lastFpsUpdate = currentTime

        if (frameTimestamps.size >= 2) {
            val elapsedMs = currentTime - frameTimestamps.first()
            val numFrames = frameTimestamps.size - 1
            val fps = (numFrames * 1000.0 / elapsedMs).toFloat()
            runOnUiThread { fpsTextView.text = "FPS: %.1f".format(fps) }
        }
    }

    override fun onRequestPermissionsResult(
        req: Int,
        perm: Array<String>,
        res: IntArray
    ) {
        super.onRequestPermissionsResult(req, perm, res)
        if (req == CAMERA_PERMISSION_CODE && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
