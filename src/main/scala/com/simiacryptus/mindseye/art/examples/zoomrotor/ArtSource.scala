package com.simiacryptus.mindseye.art.examples.zoomrotor

import com.simiacryptus.mindseye.art.util.RotorArt
import com.simiacryptus.sparkbook.InteractiveSetup

trait ArtSource[U <: InteractiveSetup[Object, U]] extends RotorArt[U] {

  def border: Double

  def magnification: Array[Double]

  def rotationalSegments: Int

  def rotationalChannelPermutation: Array[Int]

  def styles: Array[String]

  def keyframes: Array[String]

}
