package com.example.edgedetectionviewer
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EffectRenderer : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "EffectRenderer"
    }
    private var program: Int = 0
    private var vertexBuffer: FloatBuffer
    private var textureId: IntArray = intArrayOf(0)
    private var positionHandle: Int = 0
    private var textureHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var effectTypeHandle: Int = 0
    private var currentEffectType: Int = 0  // 0: none (edge only), 1: grayscale on edge, 2: invert on edge, 3: combined
    private val mvpMatrix = FloatArray(16)
    @Volatile
    private var newTextureBitmap: Bitmap? = null
    private val needsUpdate = AtomicBoolean(false)
    private val vertices = floatArrayOf(
        -1f, -1f, 0f, 0f, 1f,
        1f, -1f, 0f, 1f, 1f,
        -1f, 1f, 0f, 0f, 0f,
        1f, 1f, 0f, 1f, 0f
    )
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
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform int uEffectType;
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            if (uEffectType == 1 || uEffectType == 3) {
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                color = vec4(gray, gray, gray, color.a);
            }
            if (uEffectType == 2 || uEffectType == 3) {
                color = vec4(1.0 - color.rgb, color.a);
            }
            gl_FragColor = color;
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
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Shader link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return
        }
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        effectTypeHandle = GLES20.glGetUniformLocation(program, "uEffectType")
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        Log.d(TAG, "OpenGL surface created")
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.setIdentityM(mvpMatrix, 0)
        Log.d(TAG, "Viewport: ${width}x${height}")
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val bitmapToUse = newTextureBitmap
        if (bitmapToUse != null && needsUpdate.compareAndSet(true, false)) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmapToUse, 0)
            bitmapToUse.recycle()
            newTextureBitmap = null
            Log.d(TAG, "Texture updated")
        }
        GLES20.glUseProgram(program)
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
        vertexBuffer.position(0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1i(effectTypeHandle, currentEffectType)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
    }
    fun updateTextureData(bitmap: Bitmap) {
        newTextureBitmap?.recycle()
        newTextureBitmap = bitmap
        needsUpdate.set(true)
    }
    fun setEffectType(type: Int) {
        currentEffectType = type
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