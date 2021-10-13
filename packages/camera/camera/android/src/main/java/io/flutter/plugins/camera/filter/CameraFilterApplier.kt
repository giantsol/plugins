package io.flutter.plugins.camera.filter

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface
import io.flutter.plugins.camera.CameraProperties
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.view.TextureRegistry
import java.lang.ref.WeakReference


class CameraFilterApplier(
  context: Context,
  outputSurfaceTextureEntry: TextureRegistry.SurfaceTextureEntry,
  cameraFeatures: CameraFeatures,
  cameraProperties: CameraProperties,
) {
  // todo: declare OPENGL 3 in manifest
  companion object {
    private const val EGL_CONTEXT_CLIENT_VERSION = 3
  }

  val outputSurface: Surface

  private val width: Int
  private val height: Int

  val eglConfigChooser = EGLConfigChooserImpl(true, EGL_CONTEXT_CLIENT_VERSION)
  val eglContextFactory = EGLContextFactoryImpl(EGL_CONTEXT_CLIENT_VERSION)
  val eglWindowSurfaceFactory = EGLWindowSurfaceFactoryImpl()

  // Thread that will handle all EGL setups and drawings.
  private val glThread: GLThread

  private val inputTextureIdArray = IntArray(1)
  private lateinit var inputSurfaceTexture: SurfaceTexture
  var inputSurface: Surface? = null

  private val cameraToFrameBuffer: CameraToFrameBuffer
  private val colorFilter: ColorFilter
//  private val triangle: Triangle

  init {
    val resolutionFeature = cameraFeatures.resolution
    val outputSurfaceTexture = outputSurfaceTextureEntry.surfaceTexture()
    width = resolutionFeature.captureSize.width
    height = resolutionFeature.captureSize.height
    outputSurfaceTexture.setDefaultBufferSize(width, height)
    outputSurface = Surface(outputSurfaceTexture)

    glThread = GLThread(WeakReference(this), width, height)
    glThread.start()

    cameraToFrameBuffer = CameraToFrameBuffer(context, width, height, cameraProperties.lensFacing)
    colorFilter = ColorFilter(context)
//    triangle = Triangle(width, height)
  }

  fun switchFilter() {
    if (colorFilter.filterFlag == 0) {
      colorFilter.filterFlag = 1
    } else {
      colorFilter.filterFlag = 0
    }
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

    // Order matters.
    cameraToFrameBuffer.onOutputEglSurfaceCreated(inputTextureIdArray[0])
    colorFilter.onOutputEglSurfaceCreated(cameraToFrameBuffer.frameBufferTextureId)
//    triangle.onOutputEglSurfaceCreated()
  }

  // Called from GLThread
  fun onDrawFrame() {
    inputSurfaceTexture.updateTexImage()

    GLES20.glViewport(0, 0, width, height)

    // Order matters.
    cameraToFrameBuffer.onDrawFrame()
    colorFilter.onDrawFrame()
//    triangle.onDrawFrame()
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
   * CameraFilterApplier to continue rendering, such as in response to
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