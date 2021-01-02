package com.simiacryptus.mindseye.art.style.symmetry

import java.awt.image.BufferedImage

import com.simiacryptus.mindseye.art.style.SymmetricTexture
import com.simiacryptus.mindseye.art.util.view.{HyperbolicTileView, ImageView, RotatedVector, SphericalView}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.aws.P3_2XL
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration


object SphericalTexture extends SphericalTexture
//  with P3_2XL
    with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"

  override def name: String = SphericalTexture.super.name
}

class SphericalTexture extends SymmetricTexture {

  override def name: String = "Spherical Texture Map"

  override def indexStr = "202"

  def aspectRatio = 1

  override val rowsAndCols = 1
  override val count: Int = 1

  override def description = <div>
    Creates a texture map which can be wrapped around a sphere, by painting several rendered views of the sphere.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      val steps = 5
      (List(SphericalView(0, 0)) ++ (1 until steps).map(_ * 2 * Math.PI / steps).flatMap(theta => Array(
        SphericalView(theta, theta),
        SphericalView(theta, -theta)
      ))).map(Array[ImageView](_)).toArray
    })
  }

  override def displayViews(implicit log: NotebookOutput): Array[Array[ImageView]] = {
    val steps = 16
    Array(Array.empty[ImageView]) ++ (0 to steps).map(_*2*Math.PI/steps).map(theta=>SphericalView(theta, theta)).toArray.map(Array[ImageView](_))
  }

  override def animationDelay: FiniteDuration = 1 seconds

  override def animate(canvas: RefAtomicReference[Tensor], views: Array[Array[ImageView]]): List[() => BufferedImage] = {
    views.drop(1).map(view=>() => {
      val tensor = canvas.get()
      val renderingNetwork = compileView(tensor.getDimensions, view)
      val image = getImage(getResult(renderingNetwork.eval(tensor)))
      renderingNetwork.freeRef()
      image
    }).toList
  }

  override def resolutions = new GeometricSequence {
    override val min: Double = 128
    override val max: Double = 1024
    override val steps = 5
  }.toStream.map(x => {
    x.round.toInt -> Array(2).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)


}
