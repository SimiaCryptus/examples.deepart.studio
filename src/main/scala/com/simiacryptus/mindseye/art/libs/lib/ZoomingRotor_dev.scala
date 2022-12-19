package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.util.ImageOptimizer
import com.simiacryptus.mindseye.lang.Layer
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class ZoomingRotor_dev extends ZoomingRotor_default {
  override val keyframes: Array[String] = Array(
    "http://test.deepartist.org/TextureTiledRotor/af3376e3-9f29-406a-b65e-02e7edb757f3/etc/image_a62e037dd00c6cb7.jpg",
    "http://test.deepartist.org/TextureTiledRotor/af3376e3-9f29-406a-b65e-02e7edb757f3/etc/image_4b876ae5e1a23dfe.jpg",
    "http://test.deepartist.org/TextureTiledRotor/af3376e3-9f29-406a-b65e-02e7edb757f3/etc/image_70599ba077bb900b.jpg"
  )
  override val styles: Array[String] = Array(
    "http://test.deepartist.org/TextureTiledRotor/af3376e3-9f29-406a-b65e-02e7edb757f3/etc/d4681471-5260-489c-826f-25a50d541101.jpg"
  )
  override val resolution: Int = 256
  override val zoomSteps: Int = 2

  override def getOptimizer()(implicit log: NotebookOutput): ImageOptimizer = {
    log.eval(() => {
      new ImageOptimizer {
        override val trainingMinutes: Int = 10
        override val trainingIterations: Int = 5
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray).head
      }
    })
  }

}
