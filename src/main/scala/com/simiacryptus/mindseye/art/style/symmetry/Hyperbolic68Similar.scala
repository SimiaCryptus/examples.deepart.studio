package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.style.SymmetricTexture
import com.simiacryptus.mindseye.art.util.view.{HyperbolicTileView, ImageView, RotatedVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.aws.{P2_XL, P3_2XL}
import com.simiacryptus.sparkbook.util.LocalRunner

object Hyperbolic68Similar extends Hyperbolic68Similar
  with P2_XL
//    with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
  override def name: String = Hyperbolic68Similar.super.name

}

class Hyperbolic68Similar extends SymmetricTexture {

  override def name: String = "6/8 Hyperbolic Rotationally Similar"

  override def indexStr = "202"

  def aspectRatio = 1

  override val rowsAndCols = 1

  override def description = <div>
    Creates a 6/8 hyperbolic tile with degenerate rotational symmetry.
    Produces self-similar repetition on 60-degree rotation.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        RotatedVector(rotation = List(1).map(_ * Math.PI * 2 / 6 -> Permutation.unity(3)).toMap),
        HyperbolicTileView(6, 8),
      ))
    })
  }

  override def displayViews(implicit log: NotebookOutput): Array[Array[ImageView]] = Array(Array[ImageView](
    RotatedVector(rotation = List(1).map(_ * Math.PI * 2 / 6 -> Permutation.unity(3)).toMap),
    HyperbolicTileView(6, 8, maxRadius = 1),
  ))

  override def resolutions = new GeometricSequence {
    override val min: Double = 64
    override val max: Double = 1024
    override val steps = 5
  }.toStream.map(x => {
    x.round.toInt -> Array(6).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)


}
