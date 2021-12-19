package com.simiacryptus.mindseye.art.examples.texture

import com.simiacryptus.sparkbook.aws.P3_2XL

object BigTextureEC2 extends BigTexture with P3_2XL {
  override val s3bucket: String = "test.deepartist.org"

  override def className: String = "BigTexture"

}
