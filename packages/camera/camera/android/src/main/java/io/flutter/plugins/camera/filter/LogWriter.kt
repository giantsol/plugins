package io.flutter.plugins.camera.filter

import android.util.Log
import java.io.Writer


class LogWriter : Writer() {
  private val builder = StringBuilder()

  override fun close() {
    flushBuilder()
  }

  override fun flush() {
    flushBuilder()
  }

  override fun write(buf: CharArray, offset: Int, count: Int) {
    for (i in 0 until count) {
      val c = buf[offset + i]
      if (c == '\n') {
        flushBuilder()
      } else {
        builder.append(c)
      }
    }
  }

  private fun flushBuilder() {
    if (builder.isNotEmpty()) {
      Log.v("CameraFilter", builder.toString())
      builder.delete(0, builder.length)
    }
  }
}