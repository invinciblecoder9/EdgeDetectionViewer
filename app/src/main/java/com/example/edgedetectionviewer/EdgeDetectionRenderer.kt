package com.example.edgedetectionviewer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

class EdgeDetectionRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "EdgeDetectionRenderer"
    }

    private var program: Int = 0
    private var vertexBuffer: FloatBuffer
    private var textureId: IntArray = intArrayOf(0)
    private var positionHandle: Int = 0
    private var textureHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private val mvpMatrix = FloatArray(16)
    private val orthoMatrix = FloatArray(16)

    // Volatile bitmap for thread-safe update from UI thread
    @Volatile private var newTextureBitmap: Bitmap? = null
    private val needsUpdate = AtomicBoolean(false)

    // Vertices for a full-screen quad
    private val vertices = floatArrayOf(
        // positions     // texture coords
        -1f, -1f, 0f,   0f, 1f,   // bottom left
        1f, -1f, 0f,    1f, 1f,   // bottom right
        -1f,  1f, 0f,   0f, 0f,   // top left
        1f,  1f, 0f,    1f, 0f    // top right
    )

    // Vertex shader code
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uMVPMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // Fragment shader code (flip Y for upright texture)
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            vec2 flippedCoord = vec2(vTexCoord.x, 1.0 - vTexCoord.y);  // Invert Y for upright
            gl_FragColor = texture2D(uTexture, flippedCoord);
        }
    """.trimIndent()

    init {
        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Create shader program with error checking
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Shader link failed: ${GLES20.glGetProgramInfoLog(program)}")
            return
        }

        // Get handles
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        // Generate texture
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        Log.d(TAG, "OpenGL surface created successfully")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        // Orthographic projection: Dynamic bounds to fill view exactly (aspect-aware, no clipping)
        val aspect = width.toFloat() / height.toFloat()
        val left = -aspect  // Scale left/right to match height-based aspect
        val right = aspect
        val bottom = -1f
        val top = 1f
        Matrix.orthoM(orthoMatrix, 0, left, right, bottom, top, -1f, 1f)
        Matrix.setIdentityM(mvpMatrix, 0)  // No additional transform

        Log.d(TAG, "Surface changed: ${width}x${height}, aspect: $aspect, ortho bounds: $left to $right")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Update texture if new bitmap is available (on GL thread)
        val bitmapToUse = newTextureBitmap
        if (bitmapToUse != null && needsUpdate.compareAndSet(true, false)) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmapToUse, 0)
            bitmapToUse.recycle() // Free after upload
            newTextureBitmap = null
            Log.d(TAG, "✓ Texture updated: ${bitmapToUse.width}x${bitmapToUse.height}")
        }

        GLES20.glUseProgram(program)

        // Set vertex attributes
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer)

        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer)

        // Set uniforms
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
        GLES20.glUniform1i(textureHandle, 0)

        // Draw full quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
    }

    // Called from UI thread: Queue bitmap for GL thread update
    fun updateTextureData(bitmap: Bitmap) {
        newTextureBitmap?.recycle() // Clean up previous if any
        newTextureBitmap = bitmap
        needsUpdate.set(true)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}