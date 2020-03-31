/*
 * Copyright (c) 2020 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.art.examples

import java.awt.Color._
import java.awt.image.BufferedImage
import java.net.URI

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

object SegmentStyle extends SegmentStyle with LocalRunner[Object] with NotebookRunner[Object]

class SegmentStyle extends SegmentingSetup {

  val s3bucket: String = ""
  val contentUrl = "upload:Content"
  val maskUrl = "upload"
  val styleUrl_background = "upload:BackgroundStyle"
  val styleUrl_foreground = "upload:ForegroundStyle"

  override def indexStr = "402"

  override def description = <div>
    Demonstrates application of style transfer to a masked region identified by user scribble
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = log.eval { () => {
    require(null != VGG19.VGG19_1a.getLayer)
    implicit val implicitLog: NotebookOutput = log
    // First, basic configuration so we publish to our s3 site
    if(Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    var magnification = 8
    val contentCoeff = 5e0
    val displayRes = 500
    // Fetch input images (user upload prompts) and display a rescaled copies
    val contentImage = log.eval(() => {
      ImageArtUtil.loadImage(log, contentUrl, displayRes)
    })
    val (foreground: Tensor, background: Tensor) = partition(contentImage)
    val styleImage_background = Tensor.fromRGB(log.eval(() => {
      ImageArtUtil.loadImage(log, styleUrl_background, (displayRes * Math.sqrt(magnification)).toInt)
    }))
    val styleImage_foreground = Tensor.fromRGB(log.eval(() => {
      ImageArtUtil.loadImage(log, styleUrl_foreground, (displayRes * Math.sqrt(magnification)).toInt)
    }))
    magnification = 32
    val canvas = new RefAtomicReference[Tensor](null)
    val registration = registerWithIndexJPG(() => canvas.get())
    try {
      withMonitoredJpg(() => canvas.get().toImage) {
        val initFn: Tensor => Tensor = content => {
          val dimensions = content.getDimensions
          val aspect = dimensions(1).toDouble / dimensions(0)
          val wctSize = (Math.sqrt(320000 / (dimensions(0) * dimensions(1))) * dimensions(0)).toInt
          val resizedDims = Array(wctSize, (wctSize * aspect).toInt)
          val resizedContent = resize(content, resizedDims)
          resize(smoother(resizedContent)(wct(
            content = wct(
              content = resizedContent,
              style = styleImage_foreground,
              mask = MomentMatcher.toMask(resize(foreground, resizedDims))),
            style = styleImage_background,
            mask = MomentMatcher.toMask(resize(background, resizedDims)))
          ), dimensions)
            .map((x: Double) => if (java.lang.Double.isFinite(x)) x else 0)
        }
        paint(contentUrl, initFn, canvas, new VisualStyleContentNetwork(
          styleLayers = List(
            VGG19.VGG19_1a,
            VGG19.VGG19_1b1,
            //VGG19.VGG19_1b2,
            VGG19.VGG19_1c1,
            VGG19.VGG19_1c2,
            VGG19.VGG19_1c3,
            VGG19.VGG19_1d1,
            VGG19.VGG19_1d2,
            VGG19.VGG19_1d3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer().setMinMax(-.125, .125),
            new MomentMatcher()
          ).map(_.withMask(foreground)),
          styleUrl = List(styleUrl_foreground),
          magnification = magnification
        ) + new VisualStyleContentNetwork(
          styleLayers = List(
            VGG19.VGG19_1a,
            VGG19.VGG19_1b1,
            //VGG19.VGG19_1b2,
            VGG19.VGG19_1c1,
            VGG19.VGG19_1c2,
            VGG19.VGG19_1c3,
            VGG19.VGG19_1d1,
            VGG19.VGG19_1d2,
            VGG19.VGG19_1d3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer().setMinMax(-.5, 5),
            new MomentMatcher()
          ).map(_.withMask(background)),
          styleUrl = List(styleUrl_background),
          contentLayers = List(
            VGG19.VGG19_1b2
          ),
          contentModifiers = List(
            new ContentMatcher().scale(contentCoeff)
          ).map(_.withMask(foreground)),
          magnification = magnification
        ),
          new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 400
            override val max: Double = 600
            override val steps = 2
          }.toStream.map(_.round.toDouble))

        magnification = 8
        paint(contentUrl, tensor => tensor, canvas, new VisualStyleContentNetwork(
          styleLayers = List(
            VGG19.VGG19_1a,
            VGG19.VGG19_1b1,
            VGG19.VGG19_1b2,
            VGG19.VGG19_1c1,
            VGG19.VGG19_1c2,
            //VGG19.VGG19_1c3,
            VGG19.VGG19_1d1,
            VGG19.VGG19_1d2,
            VGG19.VGG19_1d3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer().setMinMax(-.125, .125),
            new MomentMatcher()
          ).map(_.withMask(foreground)),
          styleUrl = List(styleUrl_foreground),
          maxWidth = 2400,
          magnification = magnification
        ) + new VisualStyleContentNetwork(
          styleLayers = List(
            VGG19.VGG19_1a,
            VGG19.VGG19_1b1,
            VGG19.VGG19_1b2,
            VGG19.VGG19_1c1,
            VGG19.VGG19_1c2,
            //VGG19.VGG19_1c3,
            VGG19.VGG19_1d1,
            VGG19.VGG19_1d2,
            VGG19.VGG19_1d3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer().setMinMax(-.5, 5),
            new MomentMatcher()
          ).map(_.withMask(background)),
          styleUrl = List(styleUrl_background),
          contentLayers = List(
            VGG19.VGG19_1c3
          ),
          contentModifiers = List(
            new ContentMatcher().scale(contentCoeff)
          ).map(_.withMask(foreground)),
          maxWidth = 2400,
          magnification = magnification
        ),
          new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 800
            override val max: Double = 1200
            override val steps = 1
          }.toStream.map(_.round.toDouble))

        magnification = 1
        paint(contentUrl, (tensor: Tensor) => tensor, canvas, new VisualStyleContentNetwork(
          styleLayers = List(
            VGG19.VGG19_1a,
            VGG19.VGG19_1b1,
            VGG19.VGG19_1b2,
            VGG19.VGG19_1c1,
            VGG19.VGG19_1c2,
            VGG19.VGG19_1c3
          ),
          styleModifiers = List(
            //new GramMatrixEnhancer().setMinMax(-.125, .125),
            //                new MomentMatcher()
            //                new GramMatrixMatcher(),
            new MomentMatcher()
            //                new ChannelMeanMatcher()
          ).map(_.withMask(foreground)),
          styleUrl = List(styleUrl_foreground),
          magnification = magnification,
          maxWidth = 4000,
          tileSize = 800
        ) + new VisualStyleContentNetwork(
          styleLayers = List(
            VGG19.VGG19_1a,
            VGG19.VGG19_1b1,
            VGG19.VGG19_1b2,
            VGG19.VGG19_1c1,
            VGG19.VGG19_1c2,
            VGG19.VGG19_1c3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer().setMinMax(-.5, 5),
            //                                new GramMatrixMatcher(),
            new MomentMatcher()
            //                new ChannelMeanMatcher()
          ).map(_.withMask(background)),
          styleUrl = List(styleUrl_background),
          contentLayers = List(
            //VGG19.VGG19_1b2.prependAvgPool(4),
            VGG19.VGG19_1d1
          ),
          contentModifiers = List(
            new ContentMatcher().scale(contentCoeff)
          ).map(_.withMask(foreground)),
          magnification = magnification,
          maxWidth = 4000,
          maxPixels = 1e8,
          tileSize = 800
        ),
          new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 10
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 1600
            override val max: Double = 4000
            override val steps = 3
          }.toStream.map(_.round.toDouble))
      }
      "Complete"
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }
  }

  private def resize(foreground: Tensor, dims: Array[Int]) = {
    val resized = Tensor.fromRGB(ImageUtil.resize(foreground.toImage, dims(0), dims(1)))
    resized
  }

  def partition(contentImage: BufferedImage)(implicit log: NotebookOutput) = {
    maskUrl match {
      case maskUrl if maskUrl.startsWith("mask") =>
        log.subreport("Partition_Input", (subreport: NotebookOutput) => {
          val Seq(foreground, background) = drawMask(contentImage, RED, GREEN)(subreport)
          subreport.p(subreport.jpg(foreground.toImage, "Selection mask"))
          (foreground, background)
        })
      case maskUrl if maskUrl.startsWith("upload") =>
        val Seq(foreground, background) = uploadMask(contentImage, RED, GREEN)
        (foreground, background)
    }
  }
}
