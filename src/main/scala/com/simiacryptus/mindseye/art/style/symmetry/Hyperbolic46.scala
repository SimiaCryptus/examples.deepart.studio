package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.style.SymmetricTexture
import com.simiacryptus.mindseye.art.util.view.{HyperbolicTileView, ImageView, RotatedVector, TransformVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.aws.P2_XL

object Hyperbolic46 extends Hyperbolic46
  with P2_XL
  //  with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
}

class Hyperbolic46 extends SymmetricTexture {

  override def name: String = "4/6 Hyperbolic"
  override def indexStr = "202"
  override def inputTimeoutSeconds = 1
  def aspectRatio = 1
  override val rowsAndCols = 1

  override def description = <div>
    Creates a basic 4/6 hyperbolic pattern with strict 90-degree radial symmetry.
    Any patterns or symbols produced are purely random and do not imply a political philosophy.
  </div>.toString.trim

  def views(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        HyperbolicTileView(4, 6, mode = "square"),
        RotatedVector(rotation = Map(
          Math.PI / 2 -> Permutation(1,2,3),
          Math.PI -> Permutation(1,2,3),
          3 * Math.PI / 2 -> Permutation(1,2,3)
        )),
      ))
    })
  }

  override def auxViews(implicit log: NotebookOutput): Array[Array[ImageView]] = Array(Array(
    HyperbolicTileView(4, 6, maxRadius = 1, mode = "square"),
    RotatedVector(rotation = Map(
      Math.PI / 2 -> Permutation(1,2,3),
      Math.PI -> Permutation(1,2,3),
      3 * Math.PI / 2 -> Permutation(1,2,3)
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
