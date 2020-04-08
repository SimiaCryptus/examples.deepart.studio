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

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops.{ContentMatcher, GramMatrixEnhancer, GramMatrixMatcher, MomentMatcher}
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


object BackgroundStyle extends BackgroundStyle with LocalRunner[Object] with NotebookRunner[Object]

class BackgroundStyle extends SegmentingSetup {

  val s3bucket: String = ""
  val contentUrl = "upload:Content"
  val maskUrl = "mask:Content"
  val styleUrl = "upload:Style"

  override def indexStr = "401"

  override def description = <div>
    Demonstrates application of style transfer to a masked region identified by user scribble
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = log.eval { () => {
    implicit val implicitLog = log
    // First, basic configuration so we publish to our s3 site
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    // Fetch input images (user upload prompts) and display a rescaled copies
    val contentImage = log.eval(() => {
      ImageArtUtil.loadImage(log, contentUrl, 600)
    })
    val (foreground: Tensor, background: Tensor) = partition(contentImage)
    val styleImage = log.eval(() => {
      ImageArtUtil.loadImage(log, styleUrl, 600)
    })
    val canvas = new RefAtomicReference[Tensor](null)
    val registration = registerWithIndexJPG(() => canvas.get())
    try
      withMonitoredJpg(() => canvas.get().toImage) {
        val initFn: Tensor => Tensor = content => {
          val image = foreground.toImage
          val dims = content.getDimensions()
          val mask = MomentMatcher.toMask(Tensor.fromRGB(ImageUtil.resize(image, dims(0), dims(1))))
          smoothStyle(content = content,
            style = Tensor.fromRGB(styleImage),
            mask = mask)
        }

        paint(
          contentUrl = contentUrl,
          initFn = initFn,
          canvas = canvas,
          network = new VisualStyleContentNetwork(
            styleLayers = List(
              VGG16.VGG16_0b,
              VGG16.VGG16_1a,
              VGG16.VGG16_1b1,
              VGG16.VGG16_1b2,
              VGG16.VGG16_1c1,
              VGG16.VGG16_1c2,
              VGG16.VGG16_1c3,
              VGG16.VGG16_1d1,
              VGG16.VGG16_1d2,
              VGG16.VGG16_1d3
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-.5, 5),
              new MomentMatcher()
            ).map(_.withMask(foreground)),
            styleUrl = List(styleUrl),
            contentLayers = List(
              VGG16.VGG16_1a
            ),
            contentModifiers = List(
              new ContentMatcher().scale(1e2)
            ).map(_.withMask(background)),
            magnification = 2
          ),
          optimizer = new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, resolutions = new GeometricSequence {
            override val min: Double = 400
            override val max: Double = 800
            override val steps = 2
          }.toStream.map(_.round.toDouble))

        paint_single(
          contentUrl = contentUrl,
          initFn = tensor => tensor,
          canvas = canvas,
          network = new VisualStyleContentNetwork(
            styleLayers = List(
              VGG16.VGG16_0b,
              VGG16.VGG16_1a,
              VGG16.VGG16_1b1,
              VGG16.VGG16_1b2,
              VGG16.VGG16_1c1,
              VGG16.VGG16_1c2,
              VGG16.VGG16_1c3,
              VGG16.VGG16_1d1,
              VGG16.VGG16_1d2,
              VGG16.VGG16_1d3
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-.5, 5),
              new GramMatrixMatcher()
            ).map(_.withMask(foreground)),
            styleUrl = List(styleUrl),
            contentLayers = List(
              VGG16.VGG16_0b.prependAvgPool(2),
              VGG16.VGG16_1c3
            ),
            contentModifiers = List(
              new ContentMatcher().scale(1e1)
            ).map(_.withMask(background)),
            maxWidth = 2400,
            magnification = 1
          ),
          optimizer = new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, 1800)

        paint_single(
          contentUrl = contentUrl,
          initFn = tensor => tensor,
          canvas = canvas,
          network = new VisualStyleContentNetwork(
            styleLayers = List(
              VGG16.VGG16_0b,
              VGG16.VGG16_1a,
              VGG16.VGG16_1b1,
              VGG16.VGG16_1b2,
              VGG16.VGG16_1c1,
              VGG16.VGG16_1c2,
              VGG16.VGG16_1c3
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-.5, 5),
              new GramMatrixMatcher()
            ).map(_.withMask(foreground)),
            styleUrl = List(styleUrl),
            contentLayers = List(
              VGG16.VGG16_0b.prependAvgPool(4)
            ),
            contentModifiers = List(
              new ContentMatcher().scale(1e1)
            ).map(_.withMask(background)),
            magnification = 1,
            maxWidth = 4000,
            maxPixels = 1e8
          ),
          optimizer = new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 10
            override val maxRate = 1e9
          }, 3000)
        "Complete"
      } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }
  }

  def partition(contentImage: BufferedImage)(implicit log: NotebookOutput) = {
    maskUrl match {
      case maskUrl if maskUrl.startsWith("mask:") =>
        log.subreport("Partition_Input", (subreport: NotebookOutput) => {
          val Seq(foreground, background) = drawMask(contentImage, RED, GREEN)(subreport)
          subreport.p(subreport.jpg(foreground.toImage, "Selection mask"))
          (foreground, background)
        })
      case maskUrl if maskUrl.startsWith("upload:") =>
        val Seq(foreground, background) = uploadMask(contentImage, RED, GREEN)
        (foreground, background)
    }
  }
}
