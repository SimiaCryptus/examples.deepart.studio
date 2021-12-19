package com.simiacryptus.mindseye.art.examples.rotor

import com.simiacryptus.sparkbook.aws.P2_XL

object TextureTiledRotorEC2 extends TextureTiledRotor with P2_XL {

  override val s3bucket: String = "test.deepartist.org"

  override def className: String = "TextureTiledRotor"

}
