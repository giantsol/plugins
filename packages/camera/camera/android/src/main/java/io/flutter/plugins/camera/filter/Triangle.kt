package io.flutter.plugins.camera.filter

import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.FloatBuffer


class Triangle(
  private val width: Int,
  private val height: Int,
) {
  companion object {
    // number of coordinates per vertex in this array
    const val COORDS_PER_VERTEX = 3
  }

  private val triangleCoords = floatArrayOf(     // in counterclockwise order:
    0.0f, 0.622008459f, 0.0f,      // top
    -0.5f, -0.311004243f, 0.0f,    // bottom left
    0.5f, -0.311004243f, 0.0f      // bottom right
  )

  // Set color with red, green, blue and alpha (opacity) values
  private val color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)

  private val vertexBuffer: FloatBuffer = GLUtil.getFloatBuffer(triangleCoords)

  private val vertexShaderCode =
  // This matrix member variable provides a hook to manipulate
    // the coordinates of the objects that use this vertex shader
    "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        // the matrix must be included as a modifier of gl_Position
        // Note that the uMVPMatrix factor *must be first* in order
        // for the matrix multiplication product to be correct.
        "  gl_Position = uMVPMatrix * vPosition;" +
        "}"

  // Use to access and set the view transformation
  private var vPMatrixHandle: Int = 0

  private val fragmentShaderCode =
    "precision mediump float;" +
        "uniform vec4 vColor;" +
        "void main() {" +
        "  gl_FragColor = vColor;" +
        "}"

  private var program: Int = 0

  private var positionHandle: Int = 0
  private var colorHandle: Int = 0

  private val vertexCount: Int = triangleCoords.size / COORDS_PER_VERTEX
  private val vertexStride: Int = COORDS_PER_VERTEX * 4 // 4 bytes per vertex

  // vPMatrix is an abbreviation for "Model View Projection Matrix"
  private val vPMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val rotationMatrix = FloatArray(16)

  fun onOutputEglSurfaceCreated() {
    val vertexShader: Int = GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
    val fragmentShader: Int = GLUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

    // create empty OpenGL ES Program
    program = GLES20.glCreateProgram().also {
      // add the vertex shader to program
      GLES20.glAttachShader(it, vertexShader)
      // add the fragment shader to program
      GLES20.glAttachShader(it, fragmentShader)
      // creates OpenGL ES program executables
      GLES20.glLinkProgram(it)
    }

    val ratio: Float = width.toFloat() / height.toFloat()
    // this projection matrix is applied to object coordinates
    // in the onDrawFrame() method
    Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
  }

  fun onDrawFrame() {
    // Add program to OpenGL ES environment
    GLES20.glUseProgram(program)

    GLES20.glViewport(0, 0, width, height)

    // get handle to vertex shader's vPosition member
    positionHandle = GLES20.glGetAttribLocation(program, "vPosition").also {
      // Enable a handle to the triangle vertices
      GLES20.glEnableVertexAttribArray(it)

      // Prepare the triangle coordinate data
      GLES20.glVertexAttribPointer(
        it,
        COORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        vertexStride,
        vertexBuffer
      )

      // get handle to fragment shader's vColor member
      colorHandle = GLES20.glGetUniformLocation(program, "vColor").also { colorHandle ->
        // Set color for drawing the triangle
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
      }

      // get handle to shape's transformation matrix
      vPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

      // Set the camera position (View matrix)
      Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

      // Calculate the projection and view transformation
      Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

      val scratch = FloatArray(16)
      // Create a rotation transformation for the triangle
      val time = SystemClock.uptimeMillis() % 4000L
      val angle = 0.090f * time.toInt()
      Matrix.setRotateM(rotationMatrix, 0, angle, 0f, 0f, -1.0f)

      // Combine the rotation matrix with the projection and camera view
      // Note that the vPMatrix factor *must be first* in order
      // for the matrix multiplication product to be correct.
      Matrix.multiplyMM(scratch, 0, vPMatrix, 0, rotationMatrix, 0)

      // Pass the projection and view transformation to the shader
      GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, scratch, 0)

      // Draw the triangle
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

      // Disable vertex array
      GLES20.glDisableVertexAttribArray(it)
    }
  }
}