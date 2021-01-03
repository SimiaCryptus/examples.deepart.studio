package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.util.view.RotationalGroupView.ICOSOHEDRON
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.concurrent.duration.{FiniteDuration, _}


object IcosohedronTexture extends IcosohedronTexture
  //  with P3_2XL
  with NotebookRunner[Object] with LocalRunner[Object] {
  override val s3bucket: String = "symmetry.deepartist.org"

  override def name: String = IcosohedronTexture.super.name

  override def inputTimeoutSeconds: Int = 1
}



class IcosohedronTexture extends PolyhedralTexture {

  override def name: String = "Spherical Texture Map"

  override def indexStr = "202"

  def aspectRatio = 1

  val animationFrames = 32
  val optimizationViewLimit = 2
  override val rowsAndCols = 1
  override val count: Int = 1
  override def animationDelay: FiniteDuration = 250 milliseconds
  def group = ICOSOHEDRON

  override def description = <div>
    Creates a texture map which can be wrapped around a sphere, by painting several rendered views of the sphere.
    This texture map is reduced in size by enforcing a 60-fold icosohedral rotational symmetry.
  </div>.toString.trim

}

