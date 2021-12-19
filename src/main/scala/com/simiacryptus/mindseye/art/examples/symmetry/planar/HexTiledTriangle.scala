package com.simiacryptus.mindseye.art.examples.symmetry.planar

import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.Permutation
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector, TransformVector}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner


object HexTiledTriangle extends HexTiledTriangle
//  with P3_2XL
    with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"

  override def name: String = HexTiledTriangle.super.name
}

class HexTiledTriangle extends SymmetricTexture {

  override def name: String = "Hexagonally-tiled texture"

  override def indexStr = "202"

  override val rowsAndCols = 2

  override def description = <div>
    Creates a hexagonally-tiled texture with 3-fold color-permuted rotational symmetry.
  </div>.toString.trim

  def aspectRatio = 1.732

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(
        Array.empty[ImageView],
        Array(TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false))
      ).map(x => Array(RotatedVector(rotation = (1 until 3).map(i=>(i * 2 * Math.PI / 3) -> (Permutation(2,3,1) ^ i)).toMap)) ++ x)
    })
  }

  override def resolutions = Map(
    100 -> Array(16),
    200 -> Array(16),
    400 -> Array(16),
    800 -> Array(16)
  ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)).toSeq).toList.sortBy(_._1)

}
