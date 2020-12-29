package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.style.SymmetricTexture
import com.simiacryptus.mindseye.art.util.Permutation
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector, TransformVector}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.aws.P3_2XL


object HexTiledTriangle extends HexTiledTriangle
  with P3_2XL
  //  with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
}

class HexTiledTriangle extends SymmetricTexture {

  override def name: String = "Hexagonally-tiled texture"

  override def indexStr = "202"

  override val rowsAndCols = 2

  override def description = <div>
    Creates a hexagonally-tiled texture with 3-fold rotational symmetry.
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  def aspectRatio = 1.732

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(
        Array.empty[ImageView],
        Array(TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false))
      ).map(x => x ++ Array(RotatedVector(rotation = Map(Math.PI -> Permutation.unity(3)))))
    })
  }

  override def resolutions = Map(
    100 -> Array(16),
    200 -> Array(16),
    400 -> Array(16),
    800 -> Array(16)
  ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)).toSeq).toList.sortBy(_._1)

}
