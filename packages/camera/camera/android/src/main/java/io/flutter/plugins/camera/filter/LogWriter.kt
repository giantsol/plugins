package io.flutter.plugins.camera.filter

import android.util.Log
import java.io.Writer


class LogWriter : Writer() {
  companion object {
    private const val TAG = "LogWriter"
  }

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
      Log.v(TAG, builder.toString())
      builder.delete(0, builder.length)
    }
  }
}