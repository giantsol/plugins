package io.flutter.plugins.camera.filter


class GLThreadManager {
  val lock = Object()

  fun threadExiting(thread: GLThread) {
    synchronized(lock) {
      thread.exited = true
      lock.notifyAll()
    }
  }

  /*
   * Releases the EGL context. Requires that we are already in the
   * sGLThreadManager monitor when this is called.
   */
  fun releaseEglContextLocked() {
    lock.notifyAll()
  }
}