package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.util.view.RotationalGroupView.OCTOHEDRON
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.concurrent.duration.{FiniteDuration, _}

object OctohedronTexture extends OctohedronTexture
  //  with P3_2XL
  with NotebookRunner[Object] with LocalRunner[Object] {
  override val s3bucket: String = "symmetry.deepartist.org"

  override def name: String = OctohedronTexture.super.name

}
class OctohedronTexture extends PolyhedralTexture {

  override def name: String = "Spherical Texture Map"
  override def indexStr = "202"
  def aspectRatio = 1
  val animationFrames = 32
  val optimizationViewLimit = 2
  override val rowsAndCols = 1
  override val count: Int = 1
  override def animationDelay: FiniteDuration = 250 milliseconds
  def group = OCTOHEDRON

  override def description = <div>
    Creates a texture map which can be wrapped around a sphere, by painting several rendered views of the sphere.
    This texture map is reduced in size by enforcing a 22-fold octohedral rotational symmetry.
  </div>.toString.trim

}

