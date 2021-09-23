package io.flutter.plugins.camera.filter

import android.util.Log
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL11
import javax.microedition.khronos.opengles.GL10


class GLThread(
  private val cameraFilterApplierWeakRef: WeakReference<CameraFilterApplier>,
  private val width: Int,
  private val height: Int,
) : Thread() {
  companion object {
    private val glThreadManager: GLThreadManager = GLThreadManager()
  }

  // Once the thread is started, all accesses to the following member
  // variables are protected by the sGLThreadManager monitor

  private var shouldExit: Boolean = false
  var exited: Boolean = false
  private var requestPaused: Boolean = false
  private var paused: Boolean = false

  // We always have output surface when GLThread is created.
  private var hasSurface: Boolean = true

  private var surfaceIsBad: Boolean = false
  private var waitingForSurface: Boolean = false
  private var haveEglContext: Boolean = false
  private var haveEglSurface: Boolean = false
  private var finishedCreatingEglSurface: Boolean = false
  private var shouldReleaseEglContext: Boolean = false
  private var requestRender: Boolean = true
  private var wantRenderNotification: Boolean = false
  private var renderComplete: Boolean = false

  // End of member variables protected by the sGLThreadManager monitor.

  private lateinit var eglHelper: EGLHelper

  override fun run() {
    name = "GLThread $id"

    try {
      guardedRun()
    } catch (e: InterruptedException) {
      // fall thru and exit normally
    } finally {
      glThreadManager.threadExiting(this)
    }
  }

  private fun guardedRun() {
    eglHelper = EGLHelper(cameraFilterApplierWeakRef)
    haveEglContext = false
    haveEglSurface = false
    wantRenderNotification = false

    try {
      var gl: GL10? = null
      var createEglContext = false
      var createEglSurface = false
      var createGlInterface = false
      var lostEglContext = false
      var wantRenderNotification = false
      var doRenderNotification = false
      var askedToReleaseEglContext = false

      while (true) {
        synchronized(glThreadManager.lock) {
          while (true) {
            if (shouldExit) {
              return
            }

            // Update the pause state.
            var pausing = false
            if (paused != requestPaused) {
              pausing = requestPaused
              paused = requestPaused
              glThreadManager.lock.notifyAll()
            }

            // Do we need to give up the EGL context?
            if (shouldReleaseEglContext) {
              stopEglSurfaceLocked()
              stopEglContextLocked()
              shouldReleaseEglContext = false
              askedToReleaseEglContext = true
            }

            // Have we lost the EGL context?
            if (lostEglContext) {
              stopEglSurfaceLocked()
              stopEglContextLocked()
              lostEglContext = false
            }

            // When pausing, release the EGL surface:
            if (pausing && haveEglSurface) {
              stopEglSurfaceLocked()
            }

            // When pausing, release the EGL Context:
            if (pausing && haveEglContext) {
              stopEglContextLocked()
            }

            // Have we lost the output surface?
            if (!hasSurface && !waitingForSurface) {
              if (haveEglSurface) {
                stopEglSurfaceLocked()
              }
              waitingForSurface = true
              surfaceIsBad = false
              glThreadManager.lock.notifyAll()
            }

            // Have we acquired the output surface?
            if (hasSurface && waitingForSurface) {
              waitingForSurface = false
              glThreadManager.lock.notifyAll()
            }

            if (doRenderNotification) {
              this.wantRenderNotification = false
              doRenderNotification = false
              renderComplete = true
              glThreadManager.lock.notifyAll()
            }

            // Ready to draw?
            if (readyToDraw()) {
              // If we don't have an EGL context, try to acquire one.
              if (!haveEglContext) {
                if (askedToReleaseEglContext) {
                  askedToReleaseEglContext = false
                } else {
                  try {
                    eglHelper.start()
                  } catch (e: RuntimeException) {
                    glThreadManager.releaseEglContextLocked()
                    throw e
                  }
                  haveEglContext = true
                  createEglContext = true

                  glThreadManager.lock.notifyAll()
                }
              }

              if (haveEglContext && !haveEglSurface) {
                haveEglSurface = true
                createEglSurface = true
                createGlInterface = true
              }

              if (haveEglSurface) {
                requestRender = false
                glThreadManager.lock.notifyAll()
                if (this.wantRenderNotification) {
                  wantRenderNotification = true
                }
                break
              }
            }

            // By design, this is the only place in a GLThread thread where we wait().
            glThreadManager.lock.wait()
          }
        } // end of synchronized

        if (createEglSurface) {
          if (eglHelper.createSurface()) {
            synchronized(glThreadManager.lock) {
              finishedCreatingEglSurface = true
              glThreadManager.lock.notifyAll()
            }
          } else {
            synchronized(glThreadManager.lock) {
              finishedCreatingEglSurface = true
              surfaceIsBad = true
              glThreadManager.lock.notifyAll()
            }
            continue
          }
          createEglSurface = false
        }

        if (createGlInterface) {
          gl = eglHelper.createGl() as GL10
          createGlInterface = false
        }

        if (createEglContext) {
          cameraFilterApplierWeakRef.get()?.onOutputEglSurfaceCreated()
          createEglContext = false
        }

        cameraFilterApplierWeakRef.get()?.onDrawFrame()

        when (val swapError = eglHelper.swap()) {
          EGL10.EGL_SUCCESS -> {
            // No problem. no-op.
          }
          EGL11.EGL_CONTEXT_LOST -> {
            lostEglContext = true
          }
          else -> {
            // Other errors typically mean that the current surface is bad,
            // probably because the SurfaceView surface has been destroyed,
            // but we haven't been notified yet.
            // Log the error to help developers understand why rendering stopped.
            Log.w("GLThread", EGLHelper.formatEglError("eglSwapBuffers", swapError))

            synchronized(glThreadManager.lock) {
              surfaceIsBad = true
              glThreadManager.lock.notifyAll()
            }
          }
        }

        if (wantRenderNotification) {
          doRenderNotification = true
          wantRenderNotification = false
        }
      }
    } finally {
      // clean-up everything.
      synchronized(glThreadManager.lock) {
        stopEglSurfaceLocked()
        stopEglContextLocked()
      }
    }
  }

  /*
   * This private method should only be called inside a
   * synchronized(glThreadManager.lock) block.
   */
  private fun stopEglSurfaceLocked() {
    if (haveEglSurface) {
      haveEglSurface = false
      eglHelper.destroySurface()
    }
  }

  /*
   * This private method should only be called inside a
   * synchronized(glThreadManager.lock) block.
   */
  private fun stopEglContextLocked() {
    if (haveEglContext) {
      eglHelper.finish()
      haveEglContext = false
      glThreadManager.releaseEglContextLocked()
    }
  }

  private fun readyToDraw(): Boolean = !paused && hasSurface && !surfaceIsBad
      && width > 0 && height > 0
      && requestRender

  fun requestRender() {
    synchronized(glThreadManager.lock) {
      requestRender = true
      glThreadManager.lock.notifyAll()
    }
  }

  fun surfaceDestroyed() {
    synchronized(glThreadManager.lock) {
      hasSurface = false
      glThreadManager.lock.notifyAll()
      while (!waitingForSurface && !exited) {
        try {
          glThreadManager.lock.wait()
        } catch (e: InterruptedException) {
          currentThread().interrupt()
        }
      }
    }
  }

  fun onPause() {
    synchronized(glThreadManager.lock) {
      requestPaused = true
      glThreadManager.lock.notifyAll()
      while (!exited && !paused) {
        try {
          glThreadManager.lock.wait()
        } catch (e: InterruptedException) {
          currentThread().interrupt()
        }
      }
    }
  }

  fun onResume() {
    synchronized(glThreadManager.lock) {
      requestPaused = false
      requestRender = true
      renderComplete = false
      glThreadManager.lock.notifyAll()
      while (!exited && paused && !renderComplete) {
        try {
          glThreadManager.lock.wait()
        } catch (e: InterruptedException) {
          currentThread().interrupt()
        }
      }
    }
  }

  fun requestExitAndWait() {
    // don't call this from GLThread thread or it is a guaranteed
    // deadlock!
    synchronized(glThreadManager.lock) {
      shouldExit = true
      glThreadManager.lock.notifyAll()
      while (!exited) {
        try {
          glThreadManager.lock.wait()
        } catch (e: InterruptedException) {
          currentThread().interrupt()
        }
      }
    }
  }
}