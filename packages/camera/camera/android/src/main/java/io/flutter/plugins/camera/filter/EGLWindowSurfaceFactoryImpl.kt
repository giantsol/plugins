package io.flutter.plugins.camera.filter

import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface


class EGLWindowSurfaceFactoryImpl : GLSurfaceView.EGLWindowSurfaceFactory {
  override fun createWindowSurface(egl: EGL10, display: EGLDisplay?, config: EGLConfig?, nativeWindow: Any?): EGLSurface? {
    var result: EGLSurface? = null
    try {
      result = egl.eglCreateWindowSurface(display, config, nativeWindow, null)
    } catch (e: IllegalArgumentException) {
      Log.e("WindowSurfaceFactory", "eglCreateWindowSurface", e)
    }
    return result
  }

  override fun destroySurface(egl: EGL10, display: EGLDisplay?, surface: EGLSurface?) {
    egl.eglDestroySurface(display, surface)
  }
}