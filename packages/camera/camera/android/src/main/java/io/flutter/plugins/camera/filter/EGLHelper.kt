package io.flutter.plugins.camera.filter

import android.opengl.GLDebugHelper
import android.util.Log
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.*
import javax.microedition.khronos.opengles.GL


class EGLHelper(private val cameraFilterWeakRef: WeakReference<CameraFilter>) {
  companion object {
    fun throwEglException(function: String, error: Int) {
      val message = formatEglError(function, error)
      throw RuntimeException(message)
    }

    fun formatEglError(function: String, error: Int): String {
      return "$function failed: ${getErrorString(error)}"
    }

    private fun getErrorString(error: Int): String {
      // error codes from EGL10.java
      return when (error) {
        0x3000 -> "EGL_SUCCESS"
        0x3001 -> "EGL_NOT_INITIALIZED"
        0x3002 -> "EGL_BAD_ACCESS"
        0x3003 -> "EGL_BAD_ALLOC"
        0x3004 -> "EGL_BAD_ATTRIBUTE"
        0x3005 -> "EGL_BAD_CONFIG"
        0x3006 -> "EGL_BAD_CONTEXT"
        0x3007 -> "EGL_BAD_CURRENT_SURFACE"
        0x3008 -> "EGL_BAD_DISPLAY"
        0x3009 -> "EGL_BAD_MATCH"
        0x300A -> "EGL_BAD_NATIVE_PIXMAP"
        0x300B -> "EGL_BAD_NATIVE_WINDOW"
        0x300C -> "EGL_BAD_PARAMETER"
        0x300D -> "EGL_BAD_SURFACE"
        0x300E -> "EGL_CONTEXT_LOST"
        else -> "0x${Integer.toHexString(error)}"
      }
    }
  }

  private var egl: EGL10? = null
  private var eglDisplay: EGLDisplay? = null
  private var eglSurface: EGLSurface? = null
  private var eglConfig: EGLConfig? = null
  private var eglContext: EGLContext? = null

  // Initialize EGL.
  fun start() {
    // Get an EGL instance.
    egl = EGLContext.getEGL() as EGL10
    val egl = egl ?: throw RuntimeException("egl not initialized")

    // Get to the default display.
    eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
    if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
      throw RuntimeException("eglGetDisplay failed")
    }

    // We can now initialize EGL for that display.
    val version = IntArray(2)
    if (!egl.eglInitialize(eglDisplay, version)) {
      throw java.lang.RuntimeException("eglInitialize failed")
    }
    val cameraFilter = cameraFilterWeakRef.get()
    if (cameraFilter == null) {
      eglConfig = null
      eglContext = null
    } else {
      eglConfig = cameraFilter.eglConfigChooser.chooseConfig(egl, eglDisplay)
      /*
      * Create an EGL context. We want to do this as rarely as we can, because an
      * EGL context is a somewhat heavy object.
      */
      eglContext = cameraFilter.eglContextFactory.createContext(egl, eglDisplay, eglConfig)
    }
    if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
      eglContext = null
      throwEglException("createContext", egl.eglGetError())
    }

    eglSurface = null
  }

  /**
   * Create an egl surface for the current output surface. If a surface
   * already exists, destroy it before creating the new surface.
   *
   * @return true if the surface was created successfully.
   */
  fun createSurface(): Boolean {
    // Check preconditions.
    val egl = egl ?: throw RuntimeException("egl not initialized")
    if (eglDisplay == null) {
      throw RuntimeException("eglDisplay not initialized")
    }
    if (eglConfig == null) {
      throw RuntimeException("eglConfig not initialized")
    }

    // Destroy existing surface before creating a new one.
    destroySurface()

    // Create an EGL surface we can render into.
    val cameraFilter = cameraFilterWeakRef.get()
    eglSurface =
      cameraFilter?.eglWindowSurfaceFactory?.createWindowSurface(egl, eglDisplay, eglConfig, cameraFilter.outputSurface)

    if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
      val error = egl.eglGetError()
      if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
        Log.e("EGLHelper", "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.")
      }
      return false
    }

    // Before we can issue GL commands, we need to make sure the context is current and bound to a surface.
    if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      // Could not make the context current, probably because the underlying SurfaceView surface has been destroyed.
      Log.w("EGLHelper", formatEglError("eglMakeCurrent", egl.eglGetError()))
      return false
    }

    return true
  }

  // Create a GL object for the current EGL context.
  fun createGl(): GL {
    return GLDebugHelper.wrap(eglContext?.gl, GLDebugHelper.CONFIG_CHECK_GL_ERROR, LogWriter())
  }

  /**
   * Display the current render surface.
   * @return the EGL error code from eglSwapBuffers.
   */
  fun swap(): Int {
    val egl = egl ?: throw RuntimeException("egl not initialized")
    if (!egl.eglSwapBuffers(eglDisplay, eglSurface)) {
      return egl.eglGetError()
    }
    return EGL10.EGL_SUCCESS
  }

  fun destroySurface() {
    val egl = egl ?: throw RuntimeException("egl not initialized")

    if (eglSurface != null && eglSurface != EGL10.EGL_NO_SURFACE) {
      egl.eglMakeCurrent(
        eglDisplay, EGL10.EGL_NO_SURFACE,
        EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT
      )
      cameraFilterWeakRef.get()?.also {
        it.eglWindowSurfaceFactory.destroySurface(egl, eglDisplay, eglSurface)
      }
      eglSurface = null
    }
  }

  fun finish() {
    val egl = egl ?: throw RuntimeException("egl not initialized")

    if (eglContext != null) {
      cameraFilterWeakRef.get()?.also {
        it.eglContextFactory.destroyContext(egl, eglDisplay, eglContext)
      }
      eglContext = null
    }

    if (eglDisplay != null) {
      egl.eglTerminate(eglDisplay)
      eglDisplay = null
    }
  }
}