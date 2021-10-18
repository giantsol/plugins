package io.flutter.plugins.camera.filter

import android.graphics.Bitmap


interface CaptureCallback {
  fun onImageAvailable(bitmap: Bitmap)
}