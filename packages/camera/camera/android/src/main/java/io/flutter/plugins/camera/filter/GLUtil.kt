package io.flutter.plugins.camera.filter

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


object GLUtil {
  private const val TAG = "GLUtil"
  private const val BYTE_PER_FLOAT = 4

  fun getFloatBuffer(arr: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(arr.size * BYTE_PER_FLOAT).run {
      // use the device hardware's native byte order
      order(ByteOrder.nativeOrder())

      // create a floating point buffer from the ByteBuffer
      asFloatBuffer().apply {
        // add the coordinates to the FloatBuffer
        put(arr)
        // set the buffer to read the first coordinate
        position(0)
      }
    }

  fun createAndLinkProgram(
    context: Context,
    @RawRes vertexShaderResId: Int,
    @RawRes fragmentShaderResId: Int,
  ): Int {
    val vertexShader =
      loadShader(GLES20.GL_VERTEX_SHADER, loadResAsString(context, vertexShaderResId))
    if (vertexShader == 0) {
      Log.e(TAG, "failed to load vertexShader")
      return 0
    }

    val fragmentShader =
      loadShader(GLES20.GL_FRAGMENT_SHADER, loadResAsString(context, fragmentShaderResId))
    if (fragmentShader == 0) {
      Log.e(TAG, "failed to load fragmentShader")
      return 0
    }

    val program = GLES20.glCreateProgram().also {
      GLES20.glAttachShader(it, vertexShader)
      GLES20.glAttachShader(it, fragmentShader)
      GLES20.glLinkProgram(it)
    }

    // check program link status
    val linked = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
    if (linked[0] == 0) {
      Log.e(TAG, "failed to link program")
      GLES20.glDeleteProgram(program)
      return 0
    }

    return program
  }

  /**
   * @param type Must be either GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
   */
  fun loadShader(type: Int, shaderCode: String): Int {
    val shader = GLES20.glCreateShader(type).also {
      // add the source code to the shader and compile it
      GLES20.glShaderSource(it, shaderCode)
      GLES20.glCompileShader(it)
    }

    // check compilation status
    val compiled = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled[0] == 0) {
      Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
      GLES20.glDeleteShader(shader)
      return 0
    }

    return shader
  }

  private fun loadResAsString(context: Context, @RawRes resId: Int): String {
    val builder = StringBuilder()

    val inputStream = context.resources.openRawResource(resId)
    val reader = InputStreamReader(inputStream)
    val br = BufferedReader(reader)

    var nextLine: String? = br.readLine()
    while (nextLine != null) {
      builder.append(nextLine)
      builder.append('\n')
      nextLine = br.readLine()
    }

    return builder.toString()
  }

  fun loadLUTDrawableAsTexture(context: Context, @DrawableRes resId: Int): Int {
    val textureName = IntArray(1)
    GLES20.glGenTextures(textureName.size, textureName, 0)

    val options = BitmapFactory.Options()
    options.inScaled = false
    val lutBitmap = BitmapFactory.decodeResource(context.resources, resId, options)
    if (lutBitmap == null) {
      GLES20.glDeleteTextures(textureName.size, textureName, 0)
      Log.e(TAG, "bitmap cannot be decoded from resource id: $resId")
      return 0
    }

    // Bind texture to OpenGL
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName[0])

    // Set the texture filtering method when zooming in and zooming out.
    // It must be set, otherwise the texture will be all black.
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST.toFloat())

    // Load the bitmap into OpenGL and copy it to the currently bound texture object
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, lutBitmap, 0)
    // Release the bitmap resource (the bitmap data has been copied to the texture above)
    lutBitmap.recycle()

    // Unbind the current texture to prevent the texture from being changed in other places
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

    return textureName[0]
  }

  fun flipMatrix(matrix: FloatArray, x: Boolean, y: Boolean): FloatArray {
    if (x || y) {
      Matrix.scaleM(
        matrix,
        0,
        if (x) -1f else 1f,
        if (y) -1f else 1f,
        1f
      )
    }
    return matrix
  }
}