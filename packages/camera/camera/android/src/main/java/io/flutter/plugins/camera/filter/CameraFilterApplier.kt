package io.flutter.plugins.camera.filter

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.view.TextureRegistry
import java.lang.ref.WeakReference


class CameraFilterApplier(
  context: Context,
  outputSurfaceTextureEntry: TextureRegistry.SurfaceTextureEntry,
  cameraFeatures: CameraFeatures,
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

  private val glThread: GLThread

  private val inputTextureName = IntArray(1)
  private lateinit var inputSurfaceTexture: SurfaceTexture
  var inputSurface: Surface? = null

  private val cameraFilter: CameraFilter
  private val triangle: Triangle

  init {
    val resolutionFeature = cameraFeatures.resolution
    val outputSurfaceTexture = outputSurfaceTextureEntry.surfaceTexture()
    width = resolutionFeature.previewSize.width
    height = resolutionFeature.previewSize.height
    outputSurfaceTexture.setDefaultBufferSize(width, height)
    outputSurface = Surface(outputSurfaceTexture)

    glThread = GLThread(WeakReference(this), width, height)
    glThread.start()

    cameraFilter = CameraFilter(context, width, height)
    triangle = Triangle(width, height)
  }

  fun switchFilter() {
    if (cameraFilter.filterFlag == 0) {
      cameraFilter.filterFlag = 1
    } else {
      cameraFilter.filterFlag = 0
    }
  }

  // Called from GLThread
  fun onOutputEglSurfaceCreated() {
    GLES20.glGenTextures(inputTextureName.size, inputTextureName, 0)
    inputSurfaceTexture = SurfaceTexture(inputTextureName[0])
    inputSurfaceTexture.setDefaultBufferSize(width, height)
    inputSurfaceTexture.setOnFrameAvailableListener {
      glThread.requestRender()
    }
    inputSurface = Surface(inputSurfaceTexture)

    cameraFilter.onOutputEglSurfaceCreated(inputTextureName[0])
    triangle.onOutputEglSurfaceCreated()
  }

  // Called from GLThread
  fun onDrawFrame() {
    inputSurfaceTexture.updateTexImage()

    GLES20.glViewport(0, 0, width, height)

    cameraFilter.onDrawFrame()
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