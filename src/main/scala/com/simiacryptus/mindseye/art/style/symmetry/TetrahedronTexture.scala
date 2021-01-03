package com.simiacryptus.mindseye.art.style.symmetry

import java.awt.image.BufferedImage

import com.simiacryptus.mindseye.art.style.SymmetricTexture
import com.simiacryptus.mindseye.art.style.symmetry.RotationalGroupView.TETRAHEDRON
import com.simiacryptus.mindseye.art.util.GeometricSequence
import com.simiacryptus.mindseye.art.util.view.ImageView
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.aws.P2_XL

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Random


object TetrahedronTexture extends TetrahedronTexture
  with P2_XL
  //  with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
}

class TetrahedronTexture extends SymmetricTexture {

  override def name: String = "Spherical Texture Map"
  override def indexStr = "202"
  def aspectRatio = 1
  val animationFrames = 32
  val optimizationViewLimit = 8
  override val rowsAndCols = 1
  override val count: Int = 1

  override def description = <div>
    Creates a texture map which can be wrapped around a sphere, by painting several rendered views of the sphere.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput): Array[Array[ImageView]] = {
    log.out("Symmetry Spec:")
    log.code(() => {
      val steps = optimizationViewLimit * optimizationViewLimit
      Random.shuffle((for (x <- (0 until steps).map(_ * 2 * Math.PI / steps)) yield {
        for (y <- (0 until steps).map(_ * 2 * Math.PI / steps)) yield {
          new RotationalGroupView(x, y, TETRAHEDRON)
        }
      }).flatten.zipWithIndex).take(optimizationViewLimit).sortBy(_._2).map(_._1).map(Array[ImageView](_)).toArray
    })
  }

  override def displayViews(implicit log: NotebookOutput): Array[Array[ImageView]] = {
    Array(Array.empty[ImageView]) ++ (0 to animationFrames).map(_ * 2 * Math.PI / animationFrames).map(theta => new RotationalGroupView(theta, theta, TETRAHEDRON)).toArray.map(Array[ImageView](_))
  }

  override def animationDelay: FiniteDuration = 250 milliseconds

  override def animate(canvas: RefAtomicReference[Tensor], views: Array[Array[ImageView]]): List[() => BufferedImage] = {
    views.drop(1).map(view => () => {
      val tensor = canvas.get()
      val renderingNetwork = compileView(tensor.getDimensions, view)
      val image = getImage(getResult(renderingNetwork.eval(tensor)))
      renderingNetwork.freeRef()
      image
    }).toList
  }

  override def resolutions = new GeometricSequence {
    override val min: Double = 128
    override val max: Double = 512
    override val steps = 3
  }.toStream.map(x => {
    x.round.toInt -> Array(2).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)


}

