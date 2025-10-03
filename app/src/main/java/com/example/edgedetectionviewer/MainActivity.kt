//package com.example.edgedetectionviewer
//
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.tooling.preview.Preview
//import com.example.edgedetectionviewer.ui.theme.EdgeDetectionViewerTheme
//
//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContent {
//            EdgeDetectionViewerTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    EdgeDetectionViewerTheme {
//        Greeting("Android")
//    }
//}

package com.example.edgedetectionviewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.edgedetectionviewer.gl.CameraRenderer
import com.example.edgedetectionviewer.processors.NativeProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val CAMERA_PERMISSION_CODE = 100

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: CameraRenderer
    private lateinit var btnToggle: Button
    private lateinit var tvFps: TextView
    private lateinit var tvResolution: TextView

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var nativeProcessor: NativeProcessor

    private var isProcessingEnabled = true
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        glSurfaceView = findViewById(R.id.glSurfaceView)
        btnToggle = findViewById(R.id.btnToggle)
        tvFps = findViewById(R.id.tvFps)
        tvResolution = findViewById(R.id.tvResolution)

        // Setup OpenGL
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = CameraRenderer()
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // Initialize native processor
        nativeProcessor = NativeProcessor()

        // Setup camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup toggle button
        btnToggle.setOnClickListener {
            isProcessingEnabled = !isProcessingEnabled
            nativeProcessor.setProcessingEnabled(isProcessingEnabled)
            btnToggle.text = if (isProcessingEnabled) "Show Raw" else "Show Edges"
        }

        // Check permissions
        if (checkPermissions()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Setup image analysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )

                Log.i(TAG, "Camera started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            // Convert ImageProxy to Bitmap
            val srcBitmap = imageProxy.toBitmap()
            val dstBitmap = Bitmap.createBitmap(
                srcBitmap.width,
                srcBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            // Process with native code
            val processingTime = nativeProcessor.processFrame(srcBitmap, dstBitmap)

            // Update renderer
            renderer.currentBitmap = dstBitmap
            glSurfaceView.requestRender()

            // Calculate FPS
            frameCount++
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastFpsTime

            if (elapsed >= 1000) {
                currentFps = (frameCount * 1000.0) / elapsed
                frameCount = 0
                lastFpsTime = currentTime

                runOnUiThread {
                    tvFps.text = String.format("FPS: %.1f | Processing: %.1f ms", currentFps, processingTime)
                    tvResolution.text = "Resolution: ${srcBitmap.width}x${srcBitmap.height}"
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            imageProxy.close()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        nativeProcessor.releaseNative()
    }
}
