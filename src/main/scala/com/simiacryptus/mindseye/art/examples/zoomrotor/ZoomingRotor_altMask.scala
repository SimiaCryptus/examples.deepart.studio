package com.simiacryptus.mindseye.art.examples.zoomrotor

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops.{ContentMatcher, GramMatrixEnhancer, MomentMatcher}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, VisualNetwork, VisualStyleNetwork}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.opt.line.LineSearchStrategy
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput

class ZoomingRotor_altMask extends ZoomingRotor[ZoomingRotor_altMask] with GrafitiArt[ZoomingRotor_altMask] {
  override val s3bucket: String = "test.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val stepZoom: Double = 0.5
  override val border: Double = 0.0
  override val innerCoeff = 0
  override val enhancementCoeff: Double = 0

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def lineSearchFactory: LineSearchStrategy = {
          super.lineSearchFactory
        }

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray).head
      }
    })
  }

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    log.eval(() => {
      val outerMask = innerMask.map(x => 1 - x)
      var style: VisualNetwork = new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1b2,
          VGG19.VGG19_1c1,
          VGG19.VGG19_1c2,
          VGG19.VGG19_1c3,
          VGG19.VGG19_1c4,
          VGG19.VGG19_1d1,
          VGG19.VGG19_1d2,
          VGG19.VGG19_1d3,
          VGG19.VGG19_1d4
        ),
        styleModifiers = List(
          new MomentMatcher().scale(Math.pow(2, -enhancementCoeff * 2)),
          new GramMatrixEnhancer().setMinMax(-5, 5).scale(Math.pow(2, enhancementCoeff * 2))
        ),
        styleUrls = styles,
        magnification = magnification,
        viewLayer = dims => getKaleidoscopeMask(dims.toArray, outerMask.addRef())
      )
      if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
        contentLayers = List(
          VGG19.VGG19_0a
        ), contentModifiers = List(
          new ContentMatcher().withMask(innerMask.addRef()).scale(innerCoeff)
        ))
      style
    })
  }

}
