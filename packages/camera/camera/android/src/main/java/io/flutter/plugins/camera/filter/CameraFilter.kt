package io.flutter.plugins.camera.filter

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.view.TextureRegistry
import java.lang.ref.WeakReference


class CameraFilter(surfaceTextureEntry: TextureRegistry.SurfaceTextureEntry, cameraFeatures: CameraFeatures) {
  // todo: declare OPENGL 3 in manifest
  companion object {
    private const val eglContextClientVersion = 3
  }

  val outputSurface: Surface
  private val width: Int
  private val height: Int

  val eglConfigChooser = EGLConfigChooserImpl(true, eglContextClientVersion)
  val eglContextFactory = EGLContextFactoryImpl(eglContextClientVersion)
  val eglWindowSurfaceFactory = EGLWindowSurfaceFactoryImpl()

  private val glThread: GLThread

  private val inputSurfaceTextureName = IntArray(1)
  private lateinit var inputSurfaceTexture: SurfaceTexture
  var inputSurface: Surface? = null

  private val triangle: Triangle

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
    inputSurfaceTexture.setOnFrameAvailableListener {
      glThread.requestRender()
    }
    inputSurface = Surface(inputSurfaceTexture)

    triangle.onOutputEglSurfaceCreated()
  }

  // Called from GLThread
  fun onDrawFrame() {
    inputSurfaceTexture.updateTexImage()

    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

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