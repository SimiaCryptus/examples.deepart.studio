/*
 * Copyright (c) 2019 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.concurrent.atomic.AtomicReference

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops.{ContentMatcher, GramMatrixEnhancer, GramMatrixMatcher, MomentMatcher}
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.test.NotebookReportBase
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

object SegmentStyle extends SegmentStyle with LocalRunner[Object] with NotebookRunner[Object]

class SegmentStyle extends SegmentingSetup {

  val s3bucket: String = ""
  val contentUrl = "upload:Content"
  val maskUrl = "mask:Content"
  val styleUrl = "upload:Style"

  override def indexStr = "401"

  override def description = <div>
    Demonstrates application of style transfer to a masked region identified by user scribble
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      NotebookReportBase.withRefLeakMonitor(log, cvtSerializableRunnable((log: NotebookOutput) => {
        implicit val _ = log
        // First, basic configuration so we publish to our s3 site
        log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
        log.onComplete(() => upload(log): Unit)
        // Fetch input images (user upload prompts) and display a rescaled copies
        val contentImage = log.eval(() => {
          ImageArtUtil.load(log, contentUrl, 600)
        })
        val (foreground: Tensor, background: Tensor) = partition(contentImage)
        val styleImage = log.eval(() => {
          ImageArtUtil.load(log, styleUrl, 600)
        })
        val canvas = new AtomicReference[Tensor](null)
        val registration = registerWithIndexJPG(canvas.get())
        try {
          withMonitoredJpg(() => canvas.get().toImage) {
            val initFn: Tensor => Tensor = content => {
              val image = foreground.toImage
              val dims = content.getDimensions()
              val mask = MomentMatcher.toMask(Tensor.fromRGB(ImageUtil.resize(image, dims(0), dims(1))))
              smoothStyle(content = content,
                style = Tensor.fromRGB(styleImage),
                contentMask = mask)
            }
            paint(contentUrl, initFn, canvas, new VisualStyleContentNetwork(
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
                new MomentMatcher(),
                new GramMatrixMatcher()
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
              new BasicOptimizer {
                override val trainingMinutes: Int = 30
                override val trainingIterations: Int = 10
                override val maxRate = 1e9
              }, new GeometricSequence {
                override val min: Double = 400
                override val max: Double = 800
                override val steps = 2
              }.toStream.map(_.round.toDouble))
            paint(contentUrl, tensor => tensor, canvas, new VisualStyleContentNetwork(
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
                VGG16.VGG16_1b1.prependAvgPool(2).appendMaxPool(1),
                VGG16.VGG16_1c1.prependAvgPool(2)
              ),
              contentModifiers = List(
                new ContentMatcher().scale(1e1)
              ).map(_.withMask(background)),
              magnification = 1
            ),
              new BasicOptimizer {
                override val trainingMinutes: Int = 180
                override val trainingIterations: Int = 2
                override val maxRate = 1e9
              }, new GeometricSequence {
                override val min: Double = 1200
                override val max: Double = 3000
                override val steps = 2
              }.toStream.map(_.round.toDouble))
          }
        } finally {
          registration.foreach(_.stop()(s3client, ec2client))
        }
      }))
      null
    }
  }()

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
