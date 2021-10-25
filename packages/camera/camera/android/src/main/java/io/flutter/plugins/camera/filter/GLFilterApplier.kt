package io.flutter.plugins.camera.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface
import java.lang.ref.WeakReference
import java.nio.ByteBuffer


class GLFilterApplier(
  context: Context,
  val outputSurface: Surface,
  private val width: Int,
  private val height: Int,
  private val type: Type,
  private val callback: Callback,
) {
  // todo: declare OPENGL 3 in manifest
  companion object {
    private const val EGL_CONTEXT_CLIENT_VERSION = 3

    const val FILTER_DIR_SUPPORT = 0
    const val FILTER_DIR_CACHE = 1
  }

  interface Callback {
    fun onInputSurfaceCreated(surface: Surface)
    fun onImageAvailable(bitmap: Bitmap)
  }

  enum class Type {
    CAMERA_BACK,
    CAMERA_FRONT,
    IMAGE,
  }

  val eglConfigChooser = EGLConfigChooserImpl(true, EGL_CONTEXT_CLIENT_VERSION)
  val eglContextFactory = EGLContextFactoryImpl(EGL_CONTEXT_CLIENT_VERSION)
  val eglWindowSurfaceFactory = EGLWindowSurfaceFactoryImpl()

  // Thread that will handle all EGL setups and drawings.
  private val glThread: GLThread = GLThread(WeakReference(this), width, height)

  private val inputTextureIdArray = IntArray(1)
  private lateinit var inputSurfaceTexture: SurfaceTexture
  var inputSurface: Surface? = null

  private val textureToFrameBuffer: TextureToFrameBuffer
  private val colorFilter: ColorFilter
//  private val triangle: Triangle

  @Volatile
  var isCapturing = false
  private val exportFrameBuffer = IntArray(1)
  private val exportTextureIdArray = IntArray(1)

  init {
    glThread.start()

    textureToFrameBuffer = TextureToFrameBuffer(context, width, height, type)
    colorFilter = ColorFilter(context, type)
//    triangle = Triangle(width, height)
  }

  @JvmOverloads
  fun setColorFilter(lutFilePath: String?, intensity: Double?, filterDir: Int = FILTER_DIR_SUPPORT) {
    glThread.queueEvent {
      colorFilter.updateLutTexture(lutFilePath, intensity, filterDir)
      glThread.requestRender()
    }
  }

  fun setColorFilterIntensity(intensity: Double) {
    colorFilter.filterIntensity = intensity.toFloat()
    glThread.requestRender()
  }

  // Called from GLThread
  fun onOutputEglSurfaceCreated() {
    GLES20.glGenTextures(inputTextureIdArray.size, inputTextureIdArray, 0)
    inputSurfaceTexture = SurfaceTexture(inputTextureIdArray[0])
    inputSurfaceTexture.setDefaultBufferSize(width, height)
    inputSurfaceTexture.setOnFrameAvailableListener {
      glThread.requestRender()
    }
    inputSurface = Surface(inputSurfaceTexture)
    callback.onInputSurfaceCreated(inputSurface!!)

    // Order matters.
    textureToFrameBuffer.onOutputEglSurfaceCreated(inputTextureIdArray[0])
    colorFilter.onOutputEglSurfaceCreated(textureToFrameBuffer.frameBufferTextureId)
//    triangle.onOutputEglSurfaceCreated()

    GLES20.glGenFramebuffers(exportFrameBuffer.size, exportFrameBuffer, 0)
    GLES20.glGenTextures(exportTextureIdArray.size, exportTextureIdArray, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, exportTextureIdArray[0])
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
  }

  // Called from GLThread
  fun onDrawFrame() {
    inputSurfaceTexture.updateTexImage()

    GLES20.glViewport(0, 0, width, height)

    // Order matters.
    textureToFrameBuffer.onDrawFrame()

    if (isCapturing) {
      isCapturing = false
      val exportBuffer = ByteBuffer.allocate(width * height * 4)

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, exportFrameBuffer[0])
      GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, exportTextureIdArray[0], 0)
      colorFilter.matrix = GLUtil.rotateMatrix(colorFilter.matrix, -90f, 0f, 0f, 1f)
      colorFilter.matrix = GLUtil.flipMatrix(colorFilter.matrix, true, false)
      colorFilter.onDrawFrame()
      colorFilter.matrix = GLUtil.flipMatrix(colorFilter.matrix, true, false)
      colorFilter.matrix = GLUtil.rotateMatrix(colorFilter.matrix, 90f, 0f, 0f, 1f)
      GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, exportBuffer)

      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.copyPixelsFromBuffer(exportBuffer)
      callback.onImageAvailable(bitmap)
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    } else {
      colorFilter.onDrawFrame()
    }
//    triangle.onDrawFrame()
  }

  // todo: we should call this when the outputSurface provided in the constructor becomes invalid
  // do we? isn't release enough?
  fun outputSurfaceDestroyed() {
    glThread.surfaceDestroyed()

    GLES20.glDeleteFramebuffers(exportFrameBuffer.size, exportFrameBuffer, 0)
    GLES20.glDeleteTextures(exportTextureIdArray.size, exportTextureIdArray, 0)
  }

  /**
   * Pause the rendering thread.
   *
   * This method should be called when it is no longer desirable for the
   * GLFilterApplier to continue rendering, such as in response to
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

    GLES20.glDeleteFramebuffers(exportFrameBuffer.size, exportFrameBuffer, 0)
    GLES20.glDeleteTextures(exportTextureIdArray.size, exportTextureIdArray, 0)
  }
}