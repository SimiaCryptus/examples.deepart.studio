package com.simiacryptus.mindseye.art.examples.symmetry.planar

import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.Permutation
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector}
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner


object UpsideDown extends UpsideDown
  //  with P3_2XL
  with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"

  override def name: String = UpsideDown.super.name
}

class UpsideDown extends SymmetricTexture {

  override def name: String = "Upside-Down Texture"

  override def indexStr = "202"

  override val rowsAndCols = 2

  override def description = <div>
    Creates a texture with 180-degree rotational symmetry.
  </div>.toString.trim

  def aspectRatio = 1.0

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](RotatedVector(rotation = Map(Math.PI -> Permutation.unity(3)))))
    })
  }

  override def resolutions = Map(
    100 -> Array(16),
    200 -> Array(16),
    400 -> Array(16),
    800 -> Array(16)
  ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)).toSeq).toList.sortBy(_._1)

}
