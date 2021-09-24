package io.flutter.plugins.camera.filter

import android.content.Context
import android.opengl.GLES20
import io.flutter.plugins.camera.R


class ColorFilter(
  private val context: Context,
) {
  companion object {
    private const val VERTEX_SHADER_POSITION_NAME = "a_Position"
    private const val VERTEX_SHADER_POSITION_SIZE = 3
    private const val VERTEX_SHADER_TEXTURE_COORD_NAME = "a_texCoord"
    private const val VERTEX_SHADER_TEXTURE_COORD_SIZE = 2
    private const val VERTEX_SHADER_MATRIX_NAME = "u_matrix"

    private const val FRAGMENT_SHADER_TEXTURE_NAME = "s_texture"
    private const val FRAGMENT_SHADER_LUT_TEXTURE_NAME = "textureLUT"
    private const val FRAGMENT_SHADER_FILTER_FLAG_NAME = "filterFlag"
  }

  private val vertex = floatArrayOf(
    -1f, 1f, 0f, // upper left
    -1f, -1f, 0f, // lower left
    1f, -1f, 0f, // lower right
    1f, 1f, 0f, // upper right
  )

  private val textureCoord = floatArrayOf(
    0.0f, 1.0f,
    0.0f, 0.0f,
    1.0f, 0.0f,
    1.0f, 1.0f
  )

  private val matrix: FloatArray = GLUtil.flipMatrix(
    floatArrayOf(
      1f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f,
      0f, 0f, 1f, 0f,
      0f, 0f, 0f, 1f
    ),
    x = false,
    y = true,
  )

  private val vertexBuffer = GLUtil.getFloatBuffer(vertex)
  private val textureCoordBuffer = GLUtil.getFloatBuffer(textureCoord)

  private var program: Int = 0
  private var vertexPositionHandle: Int = 0
  private var vertexTexCoordHandle: Int = 0
  private var vertexMatrixHandle: Int = 0
  private var fragmentTextureHandle: Int = 0
  private var fragmentLUTTextureHandle: Int = 0
  private var fragmentFilterFlagHandle: Int = 0

  private var inputTextureId: Int = 0

  private var lutTextureId: Int = 0
  var filterFlag = 0

  fun onOutputEglSurfaceCreated(inputTextureId: Int) {
    this.inputTextureId = inputTextureId

    program =
      GLUtil.createAndLinkProgram(context, R.raw.vertex_shader, R.raw.color_filter_fragment_shader)
    vertexPositionHandle = GLES20.glGetAttribLocation(program, VERTEX_SHADER_POSITION_NAME)
    vertexTexCoordHandle = GLES20.glGetAttribLocation(program, VERTEX_SHADER_TEXTURE_COORD_NAME)
    vertexMatrixHandle = GLES20.glGetUniformLocation(program, VERTEX_SHADER_MATRIX_NAME)
    fragmentTextureHandle = GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_TEXTURE_NAME)
    fragmentLUTTextureHandle =
      GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_LUT_TEXTURE_NAME)
    fragmentFilterFlagHandle =
      GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_FILTER_FLAG_NAME)

    lutTextureId = GLUtil.loadLUTDrawableAsTexture(context, R.drawable.lut_aloha01)
  }

  fun onDrawFrame() {
    GLES20.glUseProgram(program)
    GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, matrix, 0)
    GLES20.glUniform1i(fragmentFilterFlagHandle, filterFlag)
    bindTexture()
    enableVertexAttribs()
    GLES20.glClearColor(1f, 1f, 1f, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertex.size / 3)
    disableVertexAttribs()
  }

  private fun bindTexture() {
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
    GLES20.glUniform1i(fragmentTextureHandle, 0)

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + 1)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
    GLES20.glUniform1i(fragmentLUTTextureHandle, 1)
  }

  private fun enableVertexAttribs() {
    GLES20.glEnableVertexAttribArray(vertexPositionHandle)
    GLES20.glEnableVertexAttribArray(vertexTexCoordHandle)

    GLES20.glVertexAttribPointer(
      vertexPositionHandle,
      VERTEX_SHADER_POSITION_SIZE,
      GLES20.GL_FLOAT,
      false,
      0,
      vertexBuffer
    )
    GLES20.glVertexAttribPointer(
      vertexTexCoordHandle,
      VERTEX_SHADER_TEXTURE_COORD_SIZE,
      GLES20.GL_FLOAT,
      false,
      0,
      textureCoordBuffer
    )
  }

  private fun disableVertexAttribs() {
    GLES20.glDisableVertexAttribArray(vertexPositionHandle)
    GLES20.glDisableVertexAttribArray(vertexTexCoordHandle)
  }

}