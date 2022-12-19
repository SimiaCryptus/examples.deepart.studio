package com.simiacryptus.mindseye.art.examples.symmetry

import com.simiacryptus.mindseye.art.examples.styled.SegmentStyleEC2
import com.simiacryptus.mindseye.art.util.Permutation
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector, TunnelView, WhirlpoolView}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.aws.P2_XL
import com.simiacryptus.sparkbook.util.LocalRunner

import java.net.URI
import java.util.UUID


object WhirlpoolEC2 extends WhirlpoolEC2; class WhirlpoolEC2 extends Whirlpool[WhirlpoolEC2] with P2_XL[Object, WhirlpoolEC2] with Jsonable[WhirlpoolEC2]
  //  with P3_2XL
//  with NotebookRunner[Object] with LocalRunner[Object]
{

  override def name: String = WhirlpoolEC2.super.name
}

class Whirlpool[T<:Whirlpool[T]] extends SymmetricTexture[T] {

  override def name: String = "Tunnel Texture with 3-way symmetry (single degeneracy)"

  override def indexStr = "202"

  override val rowsAndCols = 1

  override def description = <div>
    Creates a texture with 180-degree rotational symmetry.
  </div>.toString.trim

  def aspectRatio = 1.0

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        //RotatedVector(rotation = (1 until 2).map(i=>(i * 2 * Math.PI / 4) -> (Permutation.unity(3) ^ i)).toMap),
        WhirlpoolView()
      ))
    })
  }

  override def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = {
    List(Array[ImageView](
      //RotatedVector(rotation = (1 until 2).map(i=>(i * 2 * Math.PI / 4) -> (Permutation.unity(3) ^ i)).toMap),
      WhirlpoolView(trim = 0.0)
    ))
  }

  override def resolutions = Map(
    100 -> Array(16),
    200 -> Array(16),
    400 -> Array(16),
    800 -> Array(16)
  ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)).toSeq).toList.sortBy(_._1)

}
