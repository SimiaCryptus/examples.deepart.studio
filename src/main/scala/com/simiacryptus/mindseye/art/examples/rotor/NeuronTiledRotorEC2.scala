package com.simiacryptus.mindseye.art.examples.rotor

import com.simiacryptus.sparkbook.aws.P3_2XL

object NeuronTiledRotorEC2 extends NeuronTiledRotor with P3_2XL {

  override val s3bucket: String = "test.deepartist.org"

  override def className: String = "NeuronTiledRotor"

}
