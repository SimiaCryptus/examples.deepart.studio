package com.simiacryptus.mindseye.art.examples.zoomrotor

import scala.util.Random

trait FlowersArt[U <: FlowersArt[U]] extends ArtSource[U] {
  override val border: Double = 0.0
  override val magnification = Array(2.0)
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/26db2c3d-6f29-4895-8053-684646ee92b1.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/722a3c1e-0be9-4e2b-93c0-e74acbb1fc6e.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/54f22692-5df5-4f35-aa2a-d0a62956dc28.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/2d1d77df-c88b-4445-b737-4bd646392cee.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/7baf8cf5-60b6-4c43-b69b-0c70d93fe354.jpg"
  )
  override val keyframes = Random.shuffle(List(
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_f038534c5e7bbc9c.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_f2dc0ed871423a83.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_9fe6db04f7917fd0.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_ac03cb567c4536dd.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_e1a59272b4266e4e.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_8be7ecec6831f03f.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_20936532dd6565f0.jpg"
  )).take(3).toArray
}
