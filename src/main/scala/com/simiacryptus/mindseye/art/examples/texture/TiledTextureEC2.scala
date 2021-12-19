package com.simiacryptus.mindseye.art.examples.texture

import com.simiacryptus.sparkbook.aws.P2_XL

object TiledTextureEC2 extends TiledTexture with P2_XL {

  override val s3bucket: String = "test.deepartist.org"

  override def className: String = "TiledTexture"

}
