package com.simiacryptus.mindseye.art.examples.symmetry.hyperbolic

import com.simiacryptus.mindseye.art.examples.styled.SegmentStyleEC2
import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.view.{HyperbolicTileView, ImageView, RotatedVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.aws.P2_XL

import java.net.URI
import java.util.UUID

object Hyperbolic68SimilarEC2 extends Hyperbolic68SimilarEC2; class Hyperbolic68SimilarEC2 extends Hyperbolic68Similar[Hyperbolic68SimilarEC2] with P2_XL[Object, Hyperbolic68SimilarEC2] with Jsonable[Hyperbolic68SimilarEC2]
//    with NotebookRunner[Object] with LocalRunner[Object]
{
  override def name: String = Hyperbolic68SimilarEC2.super.name
}

class Hyperbolic68Similar[T<:Hyperbolic68Similar[T]] extends SymmetricTexture[T] {

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

  override def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = List(Array[ImageView](
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
