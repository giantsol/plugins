package io.flutter.plugins.camera.filter

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay


class EGLContextFactoryImpl(private val eglContextClientVersion: Int) :
  GLSurfaceView.EGLContextFactory {
  override fun createContext(egl: EGL10, display: EGLDisplay?, config: EGLConfig?): EGLContext? {
    val attribList = intArrayOf(0x3098, eglContextClientVersion, EGL10.EGL_NONE)
    return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
  }

  override fun destroyContext(egl: EGL10, display: EGLDisplay?, context: EGLContext?) {
    if (!egl.eglDestroyContext(display, context)) {
      EGLHelper.throwEglException("eglDestroyContext", egl.eglGetError())
    }
  }
}