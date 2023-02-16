package graphics

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.{
  GL_BLEND,
  GL_COLOR_BUFFER_BIT,
  GL_DEPTH_BUFFER_BIT,
  GL_DEPTH_TEST,
  GL_FLOAT,
  GL_LEQUAL,
  GL_ONE_MINUS_SRC_ALPHA,
  GL_SRC_ALPHA,
  GL_TRIANGLES,
  GL_TRIANGLE_STRIP,
  glBlendFunc,
  glClear,
  glClearColor,
  glDepthFunc,
  glDrawArrays,
  glEnable,
}
import org.lwjgl.opengl.GL15.{
  GL_ARRAY_BUFFER,
  GL_STATIC_DRAW,
  glBindBuffer,
  glBufferData,
  glDeleteBuffers,
  glGenBuffers,
}
import org.lwjgl.opengl.GL20.{
  glDisableVertexAttribArray,
  glEnableVertexAttribArray,
  glUniform4fv,
  glUniformMatrix4fv,
  glVertexAttribPointer,
}
import org.lwjgl.opengl.GL30.{glBindVertexArray, glDeleteVertexArrays, glGenVertexArrays}
import org.lwjgl.system.MemoryUtil
import graphics.Utils.glCheck
import org.joml.{Matrix4f, Vector2f, Vector3f}
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE

/**
 * RenderingHelper handles most low-level OpenGL code
 */
class RenderingHelper(val window: Window) {
  // x, y and z coordinate
  private val coordsPerVertex = 3

  // 4 bytes per coordinate
  private val vertexStride = coordsPerVertex * 4

  private val quadrilateralVertices = Array[Float](
    -1f, 1f, 0f, // Top left
    1f, 1f, 0f, // Top right
    -1f, -1f, 0f, // Bottom left
    1f, -1f, 0f, // Bottom right
  )

  this.init()

  private val quadrilateralShaderProgram =
    new ShaderProgram("quadrilateral", Array("mvpMatrix", "color"))

  private val (quadrilateralVaoHandle, quadrilateralVboHandle) =
    this.createQuadrilateralVertices()

  private def init(): Unit = {
    // Create GL capabilities
    glCheck { GL.createCapabilities() }

    glCheck { glEnable(GL_BLEND) }
    glCheck { glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA) }
    glCheck { glEnable(GL_DEPTH_TEST) }
    glCheck { glDepthFunc(GL_LEQUAL) }
    glCheck { glEnable(GL_MULTISAMPLE) }

    // Set clear color to black
    glCheck { glClearColor(0f, 0f, 0f, 0f) }
  }

  private def createQuadrilateralVertices(): (Int, Int) = {
    // Allocate memory for quadrilateral vertices
    val vertexBuffer = glCheck { MemoryUtil.memAllocFloat(quadrilateralVertices.length) }
    glCheck { vertexBuffer.put(quadrilateralVertices).flip() }
    // Create and bind VAO
    val vaoHandle = glCheck { glGenVertexArrays() }
    glCheck { glBindVertexArray(vaoHandle) }
    // Create and bind VBO
    val vboHandle = glCheck { glGenBuffers() }
    glCheck { glBindBuffer(GL_ARRAY_BUFFER, vboHandle) }
    // Load data to VBO
    glCheck { glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW) }
    glCheck { MemoryUtil.memFree(vertexBuffer) }
    // Define the structure of the data and store it in one of the attribute lists of the VAO
    glCheck { glVertexAttribPointer(0, coordsPerVertex, GL_FLOAT, false, vertexStride, 0) }
    // Unbind VBO and IBO
    glCheck { glBindBuffer(GL_ARRAY_BUFFER, 0) }
    // Unbind VAO
    glCheck { glBindVertexArray(0) }
    (vaoHandle, vboHandle)
  }

  def drawQuadrilateral(
      modelMatrix: Matrix4f = Matrix4f(),
      viewDirection: (Float, Float) = (0f, 0f),
      color: Array[Float] = Array(1f, 1f, 1f, 1f),
  ): Unit = {
    // Bind correct VAO and shader program
    glCheck { quadrilateralShaderProgram.bind() }
    glCheck { glBindVertexArray(quadrilateralVaoHandle) }
    glCheck { glEnableVertexAttribArray(0) }

    // Construct MVP matrix
    val mvpMatrix = new Matrix4f()
    // Perspective
    mvpMatrix.setPerspective(
      math.Pi.toFloat / 3f,
      window.getAspectRatio,
      0.1f,
      Float.PositiveInfinity,
    )
    // View
    mvpMatrix.rotateX(viewDirection(1))
    mvpMatrix.rotateY(viewDirection(0))
    // Model
    mvpMatrix.mul(modelMatrix)

    // Transfer matrix into array
    val mvpMatrixArray = Array.fill[Float](16)(0)
    mvpMatrix.get(mvpMatrixArray)

    // Set uniforms
    glCheck {
      glUniformMatrix4fv(
        quadrilateralShaderProgram.uniform("mvpMatrix"),
        false,
        mvpMatrixArray,
      )
    }
    glCheck {
      glUniform4fv(quadrilateralShaderProgram.uniform("color"), color)
    }

    // Draw vertices
    glCheck { glDrawArrays(GL_TRIANGLE_STRIP, 0, 4) }

    // Unbind everything and restore state
    glCheck { glDisableVertexAttribArray(0) }
    glCheck { glBindVertexArray(0) }
    glCheck { quadrilateralShaderProgram.unbind() }
  }

  def clear(): Unit = {
    // Clear the frame buffer
    glCheck { glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT) }
  }

  def destroy(): Unit = {
    quadrilateralShaderProgram.destroy()
    glCheck { glDeleteVertexArrays(Array(quadrilateralVaoHandle)) }
    glCheck { glDeleteBuffers(Array(quadrilateralVboHandle)) }
  }
}
