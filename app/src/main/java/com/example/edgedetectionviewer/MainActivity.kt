//package com.example.edgedetectionviewer
//
//import android.Manifest
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.ImageFormat
//import android.graphics.Matrix
//import android.graphics.Rect
//import android.graphics.YuvImage
//import android.opengl.GLSurfaceView
//import android.os.Bundle
//import android.util.Log
//import android.widget.LinearLayout
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import org.opencv.android.OpenCVLoader
//import java.io.ByteArrayOutputStream
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//
//class MainActivity : AppCompatActivity() {
//
//    private lateinit var previewView: PreviewView
//    private lateinit var glSurfaceView: GLSurfaceView
//    private lateinit var renderer: EdgeDetectionRenderer
//    private lateinit var cameraExecutor: ExecutorService
//    private var processing = false
//
//    companion object {
//        private const val TAG = "EdgeDetection"
//        private const val CAMERA_PERMISSION_CODE = 100
//
//        init {
//            System.loadLibrary("native-lib")
//        }
//    }
//
//    external fun processFrameNative(pixels: IntArray, width: Int, height: Int): IntArray?
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        if (!OpenCVLoader.initDebug()) {
//            Toast.makeText(this, "OpenCV failed", Toast.LENGTH_SHORT).show()
//            finish()
//            return
//        }
//
//        val layout = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//        }
//
//        previewView = PreviewView(this).apply {
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
//            )
//        }
//
//        // OpenGL ES Surface View
//        renderer = EdgeDetectionRenderer()
//        glSurfaceView = GLSurfaceView(this).apply {
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
//            )
//            setEGLContextClientVersion(2)
//            setRenderer(renderer)
//            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
//        }
//
//        layout.addView(previewView)
//        layout.addView(glSurfaceView)
//        setContentView(layout)
//
//        cameraExecutor = Executors.newSingleThreadExecutor()
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            == PackageManager.PERMISSION_GRANTED) {
//            startCamera()
//        } else {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
//        }
//    }
//
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//
//            val preview = Preview.Builder().build().also {
//                it.setSurfaceProvider(previewView.surfaceProvider)
//            }
//
//            val imageAnalysis = ImageAnalysis.Builder()
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)  // Match common camera aspect
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//
//            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
//                if (!processing) {
//                    processing = true
//                    processImage(imageProxy)
//                } else {
//                    imageProxy.close()
//                }
//            }
//
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
//                )
//            } catch (e: Exception) {
//                Log.e(TAG, "Camera binding error: ${e.message}")
//            }
//
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    private fun processImage(imageProxy: ImageProxy) {
//        try {
//            val bitmap = imageProxyToBitmap(imageProxy)
//            val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
//            bitmap.recycle() // Recycle input to free memory
//
//            val w = rotated.width
//            val h = rotated.height
//            val pixels = IntArray(w * h)
//            rotated.getPixels(pixels, 0, w, 0, 0, w, h)
//            rotated.recycle() // Recycle rotated
//
//            val result = processFrameNative(pixels, w, h)
//
//            if (result != null && result.size == w * h) {
//                val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
//                output.setPixels(result, 0, w, 0, 0, w, h)
//
//                // Pass to renderer (no GL calls here)
//                runOnUiThread {
//                    renderer.updateTextureData(output)
//                    glSurfaceView.requestRender()
//                }
//                Log.d(TAG, "✓ Processed and queued ${w}x${h} for OpenGL render")
//            } else {
//                Log.w(TAG, "Native processing returned invalid result (null or wrong size)")
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Processing error: ${e.message}", e)
//        } finally {
//            imageProxy.close()
//            processing = false
//        }
//    }
//
//    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
//        val planes = image.planes
//        val yBuffer = planes[0].buffer
//        val uBuffer = planes[1].buffer
//        val vBuffer = planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//        yBuffer.get(nv21, 0, ySize)
//
//        val uBytes = ByteArray(uSize)
//        val vBytes = ByteArray(vSize)
//        uBuffer.get(uBytes)
//        vBuffer.get(vBytes)
//
//        var uvIndex = ySize
//        for (i in 0 until vSize) {
//            nv21[uvIndex++] = vBytes[i]
//            nv21[uvIndex++] = uBytes[i]
//        }
//
//        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
//
//        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
//    }
//
//    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
//        val matrix = Matrix().apply { postRotate(angle) }
//        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
//    }
//
//    override fun onRequestPermissionsResult(req: Int, perm: Array<String>, res: IntArray) {
//        super.onRequestPermissionsResult(req, perm, res)
//        if (req == CAMERA_PERMISSION_CODE && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
//            startCamera()
//        } else {
//            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        glSurfaceView.onResume()
//    }
//
//    override fun onPause() {
//        super.onPause()
//        glSurfaceView.onPause()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//    }
//}
//

package com.example.edgedetectionviewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: EdgeDetectionRenderer
    private lateinit var toggleButton: FloatingActionButton
    private lateinit var cameraExecutor: ExecutorService
    private var processing = false
    private var isEdgeMode = false  // Toggle state: false=RAW, true=EDGE

    companion object {
        private const val TAG = "EdgeDetection"
        private const val CAMERA_PERMISSION_CODE = 100

        init {
            System.loadLibrary("native-lib")
        }
    }

    external fun processFrameNative(pixels: IntArray, width: Int, height: Int): IntArray?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV failed", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Main container for full-screen views
        container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Camera preview (full-screen)
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(previewView)

        // OpenGL edge view (full-screen, initially hidden)
        renderer = EdgeDetectionRenderer()
        glSurfaceView = GLSurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            visibility = FrameLayout.GONE  // Start hidden (use visibility for better performance)
        }
        container.addView(glSurfaceView)

        // Toggle button (bottom-right)
        toggleButton = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)  // Icon: camera for RAW
            setOnClickListener { toggleMode() }
            layoutParams = FrameLayout.LayoutParams(
                56.dpToPx(this@MainActivity),  // Standard normal FAB size (56dp)
                56.dpToPx(this@MainActivity),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
                rightMargin = 16.dpToPx(this@MainActivity)  // Use rightMargin for compatibility
            }
        }
        container.addView(toggleButton)

        setContentView(container)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    private fun toggleMode() {
        isEdgeMode = !isEdgeMode
        previewView.visibility = if (isEdgeMode) FrameLayout.GONE else FrameLayout.VISIBLE
        glSurfaceView.visibility = if (isEdgeMode) FrameLayout.VISIBLE else FrameLayout.GONE
        toggleButton.setImageResource(
            if (isEdgeMode) android.R.drawable.ic_menu_gallery  // Edges icon
            else android.R.drawable.ic_menu_camera  // Camera icon
        )
        Log.d(TAG, "Toggled to ${if (isEdgeMode) "EDGE" else "RAW"} mode")
        if (isEdgeMode) {
            glSurfaceView.requestRender()  // Refresh GL if needed
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
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
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding error: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
            bitmap.recycle()

            val w = rotated.width
            val h = rotated.height
            val pixels = IntArray(w * h)
            rotated.getPixels(pixels, 0, w, 0, 0, w, h)
            rotated.recycle()

            val result = processFrameNative(pixels, w, h)

            if (result != null && result.size == w * h && isEdgeMode) {  // Only update GL in EDGE mode
                val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                output.setPixels(result, 0, w, 0, 0, w, h)

                runOnUiThread {
                    renderer.updateTextureData(output)
                    glSurfaceView.requestRender()
                }
                Log.d(TAG, "✓ Processed and rendered ${w}x${h} (EDGE mode)")
            } else if (result == null) {
                Log.w(TAG, "Native processing invalid")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Processing error: ${e.message}", e)
        } finally {
            imageProxy.close()
            processing = false
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)

        val uBytes = ByteArray(uSize)
        val vBytes = ByteArray(vSize)
        uBuffer.get(uBytes)
        vBuffer.get(vBytes)

        var uvIndex = ySize
        for (i in 0 until vSize) {
            nv21[uvIndex++] = vBytes[i]
            nv21[uvIndex++] = uBytes[i]
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)

        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun onRequestPermissionsResult(req: Int, perm: Array<String>, res: IntArray) {
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

// Top-level extension function for dp to px conversion
fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}