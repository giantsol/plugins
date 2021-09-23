package io.flutter.plugins.camera.filter

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import io.flutter.plugins.camera.R
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.view.TextureRegistry
import java.lang.ref.WeakReference


class CameraFilter(
  private val context: Context,
  surfaceTextureEntry: TextureRegistry.SurfaceTextureEntry,
  cameraFeatures: CameraFeatures,
) {
  // todo: declare OPENGL 3 in manifest
  companion object {
    private const val EGL_CONTEXT_CLIENT_VERSION = 3

    private const val VERTEX_SHADER_ATTRIB_POSITION_NAME = "a_Position"
    private const val VERTEX_SHADER_ATTRIB_POSITION_SIZE = 3
    private const val VERTEX_SHADER_ATTRIB_TEXTURE_COORD_NAME = "a_texCoord"
    private const val VERTEX_SHADER_ATTRIB_TEXTURE_COORD_SIZE = 2

    private const val FRAGMENT_SHADER_UNIFORM_TEXTURE_NAME = "s_texture"
  }

  val outputSurface: Surface
  private val width: Int
  private val height: Int

  val eglConfigChooser = EGLConfigChooserImpl(true, EGL_CONTEXT_CLIENT_VERSION)
  val eglContextFactory = EGLContextFactoryImpl(EGL_CONTEXT_CLIENT_VERSION)
  val eglWindowSurfaceFactory = EGLWindowSurfaceFactoryImpl()

  private val glThread: GLThread

  private val inputSurfaceTextureName = IntArray(1)
  private lateinit var inputSurfaceTexture: SurfaceTexture
  var inputSurface: Surface? = null

  private val triangle: Triangle

  private val vertex = floatArrayOf(
    -1f, 1f, 0f, // upper left
    -1f, -1f, 0f, // lower left
    1f, -1f, 0f, // lower right
    1f, 1f, 0f, // upper right
  )

  // Texture coordinates, (s, t), t coordinate direction and vertex y coordinate are opposite
  private val textureCoord = floatArrayOf(
    0f, 1f,
    1f, 1f,
    1f, 0f,
    0f, 0f,
  )

  private val vertexBuffer = GLUtil.getFloatBuffer(vertex)
  private val textureCoordBuffer = GLUtil.getFloatBuffer(textureCoord)

  private var program: Int = 0

  init {
    val resolutionFeature = cameraFeatures.resolution
    val surfaceTexture = surfaceTextureEntry.surfaceTexture()
    width = resolutionFeature.previewSize.width
    height = resolutionFeature.previewSize.height
    surfaceTexture.setDefaultBufferSize(width, height)
    outputSurface = Surface(surfaceTexture)

    glThread = GLThread(WeakReference(this), width, height)
    glThread.start()

    triangle = Triangle(width, height)
  }

  // Called from GLThread
  fun onOutputEglSurfaceCreated() {
    GLES20.glGenTextures(inputSurfaceTextureName.size, inputSurfaceTextureName, 0)
    inputSurfaceTexture = SurfaceTexture(inputSurfaceTextureName[0])
    inputSurfaceTexture.setDefaultBufferSize(width, height)
    inputSurfaceTexture.setOnFrameAvailableListener {
      glThread.requestRender()
    }
    inputSurface = Surface(inputSurfaceTexture)

    triangle.onOutputEglSurfaceCreated()

    program = GLUtil.createAndLinkProgram(context, R.raw.vertex_shader, R.raw.fragment_shader)

    GLES20.glClearColor(1f, 1f, 1f, 0f)
  }

  // Called from GLThread
  fun onDrawFrame() {
    inputSurfaceTexture.updateTexImage()

    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    GLES20.glUseProgram(program)

    val vertexLoc = GLES20.glGetAttribLocation(program, VERTEX_SHADER_ATTRIB_POSITION_NAME)
    val textureLoc = GLES20.glGetAttribLocation(program, VERTEX_SHADER_ATTRIB_TEXTURE_COORD_NAME)

    GLES20.glEnableVertexAttribArray(vertexLoc)
    GLES20.glEnableVertexAttribArray(textureLoc)

    GLES20.glVertexAttribPointer(
      vertexLoc,
      VERTEX_SHADER_ATTRIB_POSITION_SIZE,
      GLES20.GL_FLOAT,
      false,
      0,
      vertexBuffer
    )
    GLES20.glVertexAttribPointer(
      textureLoc,
      VERTEX_SHADER_ATTRIB_TEXTURE_COORD_SIZE,
      GLES20.GL_FLOAT,
      false,
      0,
      textureCoordBuffer
    )

    // Bind texture unit 0 texture
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    // Put the texture in the GL_TEXTURE_EXTERNAL_OES target object of the current unit
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputSurfaceTextureName[0])
    // Set texture filter parameters
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    // Set the texture attribute value (s_texture) of the fragment shader to unit 0
    val uTextureLoc = GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_UNIFORM_TEXTURE_NAME)
    GLES20.glUniform1i(uTextureLoc, 0)

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertex.size / 3)

    GLES20.glDisableVertexAttribArray(vertexLoc)
    GLES20.glDisableVertexAttribArray(textureLoc)

    triangle.onDrawFrame()
  }

  // todo: we should call this when the outputSurface provided in the constructor becomes invalid
  // do we? isn't release enough?
  fun outputSurfaceDestroyed() {
    glThread.surfaceDestroyed()
  }

  /**
   * Pause the rendering thread.
   *
   * This method should be called when it is no longer desirable for the
   * CameraFilter to continue rendering, such as in response to
   * {@link android.app.Activity#onStop Activity.onStop}.
   */
  fun onPause() {
    glThread.onPause()
  }

  /**
   * Resumes the rendering thread, re-creating the OpenGL context if necessary. It
   * is the counterpart to {@link #onPause()}.
   *
   * This method should typically be called in
   * {@link android.app.Activity#onStart Activity.onStart}.
   */
  fun onResume() {
    glThread.onResume()
  }

  fun release() {
    glThread.requestExitAndWait()
  }
}