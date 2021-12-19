package com.simiacryptus.mindseye.art.examples.rotor

import com.simiacryptus.sparkbook.aws.P2_XL

object NeuronDeconstructionRotorsEC2 extends NeuronDeconstructionRotors with P2_XL {

  override val s3bucket: String = "test.deepartist.org"

  override def className: String = "NeuronDeconstructionRotors"

}
