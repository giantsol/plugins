package io.flutter.plugins.camera.filter

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import io.flutter.plugins.camera.R


class CameraToFrameBuffer(
  private val context: Context,
  private val width: Int,
  private val height: Int,
) {
  companion object {
    private const val VERTEX_SHADER_POSITION_NAME = "a_Position"
    private const val VERTEX_SHADER_POSITION_SIZE = 3
    private const val VERTEX_SHADER_TEXTURE_COORD_NAME = "a_texCoord"
    private const val VERTEX_SHADER_TEXTURE_COORD_SIZE = 2
    private const val VERTEX_SHADER_MATRIX_NAME = "u_matrix"

    private const val FRAGMENT_SHADER_TEXTURE_NAME = "s_texture"
  }

  private val vertex = floatArrayOf(
    -1f, 1f, 0f, // upper left
    -1f, -1f, 0f, // lower left
    1f, -1f, 0f, // lower right
    1f, 1f, 0f, // upper right
  )

  private val textureCoord = floatArrayOf(
    1.0f, 1.0f,
    0.0f, 1.0f,
    0.0f, 0.0f,
    1.0f, 0.0f,
  )

  private val matrix: FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f,
  )

  private val vertexBuffer = GLUtil.getFloatBuffer(vertex)
  private val textureCoordBuffer = GLUtil.getFloatBuffer(textureCoord)

  private var program: Int = 0
  private var vertexPositionHandle: Int = 0
  private var vertexTexCoordHandle: Int = 0
  private var vertexMatrixHandle: Int = 0
  private var fragmentTextureHandle: Int = 0

  private val frameBuffer = IntArray(1)
  private val frameBufferTextureIdArray = IntArray(1)
  val frameBufferTextureId: Int
    get() = frameBufferTextureIdArray[0]

  private var inputTextureId: Int = 0

  fun onOutputEglSurfaceCreated(inputTextureId: Int) {
    this.inputTextureId = inputTextureId

    program = GLUtil.createAndLinkProgram(context, R.raw.vertex_shader, R.raw.camera_to_framebuffer_fragment_shader)
    vertexPositionHandle = GLES20.glGetAttribLocation(program, VERTEX_SHADER_POSITION_NAME)
    vertexTexCoordHandle =
      GLES20.glGetAttribLocation(program, VERTEX_SHADER_TEXTURE_COORD_NAME)
    vertexMatrixHandle = GLES20.glGetUniformLocation(program, VERTEX_SHADER_MATRIX_NAME)
    fragmentTextureHandle =
      GLES20.glGetUniformLocation(program, FRAGMENT_SHADER_TEXTURE_NAME)

    genFrameBufferAndTexture()
  }

  private fun genFrameBufferAndTexture() {
    GLES20.glGenFramebuffers(frameBuffer.size, frameBuffer, 0)
    GLES20.glGenTextures(frameBufferTextureIdArray.size, frameBufferTextureIdArray, 0)

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTextureIdArray[0])
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())

    // detach texture.
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
  }

  fun onDrawFrame() {
    bindFrameBuffer()
    GLES20.glUseProgram(program)
    GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, matrix, 0)
    bindTexture()
    enableVertexAttribs()
    GLES20.glClearColor(1f, 1f, 1f, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertex.size / 3)
    disableVertexAttribs()
    unbindFrameBuffer()
  }

  private fun bindFrameBuffer() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, frameBufferTextureIdArray[0], 0)
  }

  private fun bindTexture() {
    // Bind texture unit 0 texture
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    // Put the texture in the GL_TEXTURE_EXTERNAL_OES target object of the current unit
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
    // Set the texture attribute value (s_texture) of the fragment shader to unit 0
    GLES20.glUniform1i(fragmentTextureHandle, 0)
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

  private fun unbindFrameBuffer() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

}