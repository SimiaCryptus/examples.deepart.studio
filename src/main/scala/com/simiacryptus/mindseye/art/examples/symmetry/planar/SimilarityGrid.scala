package com.simiacryptus.mindseye.art.examples.symmetry.planar

import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.Permutation
import com.simiacryptus.mindseye.art.util.view.{ImageView, TransformVector}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner

object SimilarityGrid extends SimilarityGrid
//  with P2_XL
  with NotebookRunner[Object] with LocalRunner[Object]
{

  override def name: String = SimilarityGrid.super.name
}

class SimilarityGrid extends SymmetricTexture
{

  override def name: String = "1/5 Square 1st-order degeneracy"
  override def indexStr = "202"
  override val rowsAndCols = 2
  val magnification = 16

  override def description = <div>
    Creates a square texture with a degeneracy which ensures repeating, similar but gradually evolving
    repeating patterns on both horizontal and vertical offset axes.
  </div>.toString.trim

  def aspectRatio = 1.0

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        TransformVector(offset = List(1).map(x => Array(0, x.toDouble / 5) -> Permutation.unity(3)).toMap),
        TransformVector(offset = List(1).map(x => Array(x.toDouble / 5, 0) -> Permutation.unity(3)).toMap)
      )).flatMap(x=>List(x,x++List(TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3))))))
    })
  }


  override def resolutions = Map(
    100 -> Array(magnification),
    200 -> Array(magnification),
    400 -> Array(magnification),
    800 -> Array(magnification)
  ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)).toSeq).toList.sortBy(_._1)

}
