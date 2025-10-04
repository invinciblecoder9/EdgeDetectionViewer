package com.example.edgedetectionviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.LinearLayout
import org.opencv.core.Size

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var edgeImageView: ImageView
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Create UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        edgeImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        layout.addView(previewView)
        layout.addView(edgeImageView)
        setContentView(layout)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check camera permission
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, EdgeDetectionAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private inner class EdgeDetectionAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            val bitmap = image.toBitmap()
            val edgeBitmap = detectEdges(bitmap)

            runOnUiThread {
                edgeImageView.setImageBitmap(edgeBitmap)
            }

            image.close()
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Simple conversion - you might want to improve this
        return bitmap
    }

    private fun detectEdges(bitmap: Bitmap): Bitmap {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        val grayMat = Mat()
        val edgesMat = Mat()

        // Convert bitmap to Mat
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        // Apply Gaussian blur
        Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)

        // Apply Canny edge detection
        Imgproc.Canny(grayMat, edgesMat, 50.0, 150.0)

        // Convert back to bitmap
        val resultBitmap = Bitmap.createBitmap(
            edgesMat.cols(),
            edgesMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        org.opencv.android.Utils.matToBitmap(edgesMat, resultBitmap)

        // Release Mats
        mat.release()
        grayMat.release()
        edgesMat.release()

        return resultBitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

