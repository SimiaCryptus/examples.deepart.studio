package com.simiacryptus.mindseye.art.examples.symmetry.spherical

import java.awt.image.BufferedImage

import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.GeometricSequence
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotationalGroupView}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference

import scala.util.Random

abstract class PolyhedralTexture[T<:PolyhedralTexture[T]] extends SymmetricTexture[T] {

  def group: Int

  def animationFrames: Int

  def optimizationViewLimit: Int

  def optimizerViews(implicit log: NotebookOutput): Array[Array[ImageView]] = {
    log.out("Symmetry Spec:")
    log.code(() => {
      val steps = optimizationViewLimit * optimizationViewLimit
      Random.shuffle((for (x <- (0 until steps).map(_ * 2 * Math.PI / steps)) yield {
        for (y <- (0 until steps).map(_ * 2 * Math.PI / steps)) yield {
          new RotationalGroupView(x, y, group)
        }
      }).flatten.zipWithIndex).take(optimizationViewLimit).sortBy(_._2).map(_._1).map(Array[ImageView](_)).toArray
    })
  }

  override def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = {
    List(Array[ImageView](
      new RotationalGroupView(0, 0, group).tileExpansion
    ))
  }

  private lazy val animationSequence: List[RotationalGroupView] = {
    val animationFrames = this.animationFrames / 2
    (0 to animationFrames).map(_ * 2 * Math.PI / animationFrames).toList.map(theta => new RotationalGroupView(theta, 0, group)) ++
      (0 to animationFrames).map(_ * 2 * Math.PI / animationFrames).toList.map(theta => new RotationalGroupView(0, theta, group))
  }

  override def animate(canvas: RefAtomicReference[Tensor])(implicit log: NotebookOutput): List[() => BufferedImage] = {
    animationSequence.map(Array[ImageView](_)).map(view => () => {
      val tensor = canvas.get()
      val renderingNetwork = compileView(tensor.getDimensions, view)
      val image = getImage(getResult(renderingNetwork.eval(tensor)))
      renderingNetwork.freeRef()
      image
    })
  }

  override def resolutions = new GeometricSequence {
    override val min: Double = 128
    override val max: Double = 512
    override val steps = 3
  }.toStream.map(x => {
    x.round.toInt -> Array(2).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)

}
