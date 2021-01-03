package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.util.view.{HyperbolicTileView, ImageView, RotatedVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner


object Hyperbolic320 extends Hyperbolic320
//  with P3_2XL
    with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"

  override def name: String = Hyperbolic320.super.name
}

class Hyperbolic320 extends SymmetricTexture {

  override def name: String = "3/20 Hyperbolic Tiling with Rotational Symmetry"

  override def indexStr = "202"

  def aspectRatio = 1

  override val rowsAndCols = 1

  override def description = <div>
    Creates a 3/20 hyperbolic tiling with perfect 3-fold rotational symmetry.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        RotatedVector(rotation = List(1, 2).map(_ * Math.PI * 2 / 3 -> Permutation.unity(3)).toMap),
        HyperbolicTileView(3, 20),
      ))
    })
  }

  override def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = List(Array[ImageView](
    RotatedVector(rotation = List(1, 2).map(_ * Math.PI * 2 / 3 -> Permutation.unity(3)).toMap),
    HyperbolicTileView(3, 20, maxRadius = 1),
  ))

  override def resolutions = new GeometricSequence {
    override val min: Double = 64
    override val max: Double = 1024
    override val steps = 5
  }.toStream.map(x => {
    x.round.toInt -> Array(6).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)

}
