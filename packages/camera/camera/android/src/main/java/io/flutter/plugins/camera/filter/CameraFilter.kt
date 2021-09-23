package io.flutter.plugins.camera.filter

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import io.flutter.plugins.camera.R


class CameraFilter(private val context: Context) {
  companion object {
    private const val VERTEX_SHADER_ATTRIB_POSITION_NAME = "a_Position"
    private const val VERTEX_SHADER_ATTRIB_POSITION_SIZE = 3
    private const val VERTEX_SHADER_ATTRIB_TEXTURE_COORD_NAME = "a_texCoord"
    private const val VERTEX_SHADER_ATTRIB_TEXTURE_COORD_SIZE = 2
    private const val VERTEX_SHADER_UNIFORM_MATRIX_NAME = "u_matrix"

    private const val FRAGMENT_SHADER_UNIFORM_TEXTURE_NAME = "s_texture"
    private const val FRAGMENT_SHADER_UNIFORM_LUT_TEXTURE_NAME = "textureLUT"
    private const val FRAGMENT_SHADER_UNIFORM_FILTER_FLAG_NAME = "filterFlag"

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

  private val vertex = floatArrayOf(
    -1f, 1f, 0f, // upper left
    -1f, -1f, 0f, // lower left
    1f, -1f, 0f, // lower right
    1f, 1f, 0f, // upper right
  )

  // Texture coordinates, (s, t), t coordinate direction and vertex y coordinate are opposite
  private val textureCoord = floatArrayOf(
    0f, 1f,
    1f, 1f,
    1f, 0f,
    0f, 0f,
  )

  private val vertexBuffer = GLUtil.getFloatBuffer(vertex)
  private val textureCoordBuffer = GLUtil.getFloatBuffer(textureCoord)

  private val matrix: FloatArray = flipMatrix(
    floatArrayOf(
      1f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f,
      0f, 0f, 1f, 0f,
      0f, 0f, 0f, 1f
    ),
    x = false,
    y = false,
  )

  private var program: Int = 0
  private var vertexPositionHandle: Int = 0
  private var vertexTexCoordHandle: Int = 0
  private var vertexMatrixHandle: Int = 0
  private var fragmentTextureHandle: Int = 0
  private var fragmentLUTTextureHandle: Int = 0
  private var fragmentFilterFlagHandle: Int = 0

  private var lutTextureName: Int = 0
  private var inputTextureName: Int = 0

  fun onOutputEglSurfaceCreated(inputTextureName: Int) {
    program = GLUtil.createAndLinkProgram(context, R.raw.vertex_shader, R.raw.fragment_shader)

    vertexPositionHandle = GLES20.glGetAttribLocation(program, VERTEX_SHADER_ATTRIB_POSITION_NAME)
    vertexTexCoordHandle =
      GLES20.glGetAttribLocation(program, VERTEX_SHADER_ATTRIB_TEXTURE_COORD_NAME)
    vertexMatrixHandle = GLES20.glGetUniformLocation(program, VERTEX_SHADER_UNIFORM_MATRIX_NAME)
    fragmentTextureHandle =
      GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_UNIFORM_TEXTURE_NAME)
    fragmentLUTTextureHandle =
      GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_UNIFORM_LUT_TEXTURE_NAME)
    fragmentFilterFlagHandle =
      GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_UNIFORM_FILTER_FLAG_NAME)

    lutTextureName = GLUtil.loadLUTDrawableAsTexture(context, R.drawable.amatorka)
    this.inputTextureName = inputTextureName
  }

  fun onDrawFrame() {
    GLES20.glUseProgram(program)

    GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, matrix, 0)

    enableVertexAttribs()

    bindTexture()

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertex.size / 3)

    disableVertexAttribs()
  }

  private fun enableVertexAttribs() {
    GLES20.glEnableVertexAttribArray(vertexPositionHandle)
    GLES20.glEnableVertexAttribArray(vertexTexCoordHandle)

    GLES20.glVertexAttribPointer(
      vertexPositionHandle,
      VERTEX_SHADER_ATTRIB_POSITION_SIZE,
      GLES20.GL_FLOAT,
      false,
      0,
      vertexBuffer
    )
    GLES20.glVertexAttribPointer(
      vertexTexCoordHandle,
      VERTEX_SHADER_ATTRIB_TEXTURE_COORD_SIZE,
      GLES20.GL_FLOAT,
      false,
      0,
      textureCoordBuffer
    )
  }

  private fun bindTexture() {
    // Bind texture unit 0 texture
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    // Put the texture in the GL_TEXTURE_EXTERNAL_OES target object of the current unit
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureName)
    // Set texture filter parameters
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    // Set the texture attribute value (s_texture) of the fragment shader to unit 0
    GLES20.glUniform1i(fragmentTextureHandle, 0)

    if (lutTextureName > 0) {
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + 1)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureName)
      GLES20.glUniform1i(fragmentLUTTextureHandle, 1)
      GLES20.glUniform1i(fragmentFilterFlagHandle, 1)
    } else {
      GLES20.glUniform1i(fragmentFilterFlagHandle, 0)
    }
  }

  private fun disableVertexAttribs() {
    GLES20.glDisableVertexAttribArray(vertexPositionHandle)
    GLES20.glDisableVertexAttribArray(vertexTexCoordHandle)
  }
}