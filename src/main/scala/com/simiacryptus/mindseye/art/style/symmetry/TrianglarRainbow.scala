package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector, TransformVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.aws.P2_XL

object TrianglarRainbow extends TrianglarRainbow
  with P2_XL
  //with LocalRunner[Object] with NotebookRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
}

class TrianglarRainbow extends SymmetricTexture {

  def aspectRatio = 1.732
  override def name: String = "Triangle Rainbow"
  override def indexStr = "202"

  override def description = <div>
    Creates a tiled pattern with a triangular, color-permuted symmetry.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(
        Array.empty[ImageView],
        Array(TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false))
      ).map(_ ++ Array(RotatedVector(rotation = (1 to 2).map(x => (x * 2) * Math.PI / 3 -> (Permutation(3, 1, 2) ^ x)).toMap)))
    })
  }

  override def resolutions = new GeometricSequence {
    override val min: Double = 320
    override val max: Double = 640
    override val steps = 2
  }.toStream.map(x => {
    x.round.toInt -> Array(8.0)
  }: (Int, Seq[Double])).toList.sortBy(_._1)


}
