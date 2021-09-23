package io.flutter.plugins.camera.filter

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay


class EGLConfigChooserImpl(
  withDepthBuffer: Boolean,
  eglContextClientVersion: Int,
) : GLSurfaceView.EGLConfigChooser {
  private val tmpValue: IntArray = IntArray(1)

  private val redSize: Int = 8
  private val greenSize: Int = 8
  private val blueSize: Int = 8
  private val alphaSize: Int = 8
  private val depthSize: Int = if (withDepthBuffer) 16 else 0
  private val stencilSize: Int = 0

  private val configSpec: IntArray

  init {
    configSpec = filterConfigSpec(
      intArrayOf(
        EGL10.EGL_RED_SIZE, redSize,
        EGL10.EGL_GREEN_SIZE, greenSize,
        EGL10.EGL_BLUE_SIZE, blueSize,
        EGL10.EGL_ALPHA_SIZE, alphaSize,
        EGL10.EGL_DEPTH_SIZE, depthSize,
        EGL10.EGL_STENCIL_SIZE, stencilSize,
        EGL10.EGL_NONE
      ), eglContextClientVersion
    )
  }

  private fun filterConfigSpec(configSpec: IntArray, eglContextClientVersion: Int): IntArray {
    if (eglContextClientVersion != 2 && eglContextClientVersion != 3) {
      return configSpec
    }

    val len = configSpec.size
    val newConfigSpec = IntArray(len + 2)
    System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1)
    newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE
    if (eglContextClientVersion == 2) {
      newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT
    } else {
      newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR
    }
    newConfigSpec[len + 1] = EGL10.EGL_NONE
    return newConfigSpec
  }

  override fun chooseConfig(egl: EGL10, display: EGLDisplay?): EGLConfig {
    val numConfig = IntArray(1)
    if (!egl.eglChooseConfig(display, configSpec, null, 0, numConfig)) {
      throw IllegalArgumentException("eglChooseConfig failed")
    }

    val numConfigs = numConfig[0]
    if (numConfigs <= 0) {
      throw IllegalArgumentException("No configs match configSpec")
    }

    val configs = arrayOfNulls<EGLConfig>(numConfigs)
    if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs, numConfig)) {
      throw IllegalArgumentException("eglChooseConfig#2 failed")
    }

    return chooseConfig(egl, display, configs)
      ?: throw IllegalArgumentException("No config chosen")
  }

  private fun chooseConfig(egl: EGL10, display: EGLDisplay?, configs: Array<EGLConfig?>): EGLConfig? {
    for (config in configs) {
      val d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE)
      val s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE)
      if ((d >= depthSize) && (s >= stencilSize)) {
        val r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE)
        val g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE)
        val b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE)
        val a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE)
        if ((r == redSize) && (g == greenSize) && (b == blueSize) && (a == alphaSize)) {
          return config
        }
      }
    }

    return null
  }

  private fun findConfigAttrib(egl: EGL10, display: EGLDisplay?, config: EGLConfig?, attribute: Int): Int {
    if (egl.eglGetConfigAttrib(display, config, attribute, tmpValue)) {
      return tmpValue[0]
    }

    // 0 is the default value.
    return 0
  }
}