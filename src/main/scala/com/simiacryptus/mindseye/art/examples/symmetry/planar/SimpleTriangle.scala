package com.simiacryptus.mindseye.art.examples.symmetry.planar

import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.Permutation
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector, TransformVector}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.aws.P3_2XL

import java.net.URI
import java.util.UUID


object SimpleTriangleEC2 extends SimpleTriangleEC2; class SimpleTriangleEC2 extends SimpleTriangle[SimpleTriangleEC2] with P3_2XL[Object, SimpleTriangleEC2] with Jsonable[SimpleTriangleEC2]
  //  with NotebookRunner[Object] with LocalRunner[Object]
{

}

class SimpleTriangle[T<:SimpleTriangle[T]] extends SymmetricTexture[T] {

  override def name: String = "Simple 3-fold rotational symmetry"

  override def indexStr = "202"

  override val rowsAndCols = 1

  override def description = <div>
    Creates a simple rotationally-symmetric texture.
  </div>.toString.trim

  def aspectRatio = 1.0

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap),
        TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)))
      ))
    })
  }

  override def resolutions = Map(
    100 -> Array(16),
    200 -> Array(16),
    400 -> Array(16),
    800 -> Array(16)
  ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)).toSeq).toList.sortBy(_._1)

}
