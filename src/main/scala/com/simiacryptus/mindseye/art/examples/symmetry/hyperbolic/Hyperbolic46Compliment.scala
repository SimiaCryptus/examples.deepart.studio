package com.simiacryptus.mindseye.art.examples.symmetry.hyperbolic

import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.view.{HyperbolicTileView, ImageView, RotatedVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.aws.P2_XL


object Hyperbolic46Compliment extends Hyperbolic46Compliment
  with P2_XL
  //  with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
}

class Hyperbolic46Compliment extends SymmetricTexture {

  override def name: String = "4/6 Hyperbolic Complimentary Colors"

  override def indexStr = "202"

  def aspectRatio = 1

  override val rowsAndCols = 1

  override def description = <div>
    Creates a basic 4/6 hyperbolic pattern with strict 90-degree radial symmetry, and a 180-degree color-negative symmetry.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        HyperbolicTileView(4, 6, mode = "square"),
        RotatedVector(rotation = Map(
          Math.PI / 2 -> Permutation(-1, -2, -3),
          Math.PI -> Permutation(1, 2, 3),
          3 * Math.PI / 2 -> Permutation(-1, -2, -3)
        )),
      ))
    })
  }

  override def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = List(Array(
    HyperbolicTileView(4, 6, maxRadius = 1, mode = "square"),
    RotatedVector(rotation = Map(
      Math.PI / 2 -> Permutation(-1, -2, -3),
      Math.PI -> Permutation(1, 2, 3),
      3 * Math.PI / 2 -> Permutation(-1, -2, -3)
    )),
  ))

  override def resolutions = new GeometricSequence {
    override val min: Double = 64
    override val max: Double = 1024
    override val steps = 5
  }.toStream.map(x => {
    x.round.toInt -> Array(6).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)


}
