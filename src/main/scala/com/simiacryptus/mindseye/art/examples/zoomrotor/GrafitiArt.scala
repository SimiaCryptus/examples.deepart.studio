package com.simiacryptus.mindseye.art.examples.zoomrotor

trait GrafitiArt[U <: ArtSource[U]] extends ArtSource[U] {
  override val border: Double = 0.0
  override val magnification: Array[Double] = Array(2)
  override val rotationalSegments = 5
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/b3b548e5-15b3-4668-8ff8-a0a5052be912.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/38e6440a-9f99-4a4e-936e-337f1f93aba5.jpg"
  )
  override val keyframes = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/image_79e6507dcbc0e5a6.jpg"
  )
}
