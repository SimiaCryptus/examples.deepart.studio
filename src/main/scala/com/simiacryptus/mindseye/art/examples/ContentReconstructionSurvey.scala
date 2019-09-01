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

import java.awt.image.BufferedImage
import java.awt.{Font, Graphics2D}
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.lang.cudnn.{CudaMemory, Precision}
import com.simiacryptus.mindseye.opt.Step
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.{AWSNotebookRunner, EC2Runner, NotebookRunner}
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object ContentReconstructionSurvey extends ContentReconstructionSurvey with LocalRunner[Object] with NotebookRunner[Object]

class ContentReconstructionSurvey extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val initUrl = "50 + noise * 0.05"
  val s3bucket: String = "examples.deepartist.org"
  val resolution = 800
  val animationDelay = 1000

  override def indexStr = "101"

  override def description = <div>
    Reconstructs an example image's content via each layer of the VGG19 network.
    Paints an image using a variety of layers but each having:
    <ol>
      <li>A single input image to define content via a single layer</li>
      <li>Random noise initialization</li>
      <li>Standard content matching operator</li>
      <li>A single resolution</li>
    </ol>
    It demonstrates the variety of effects that can be obtained using varied layers to constrain content.
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch image (user upload prompt) and display a rescaled copy
      log.out(log.jpg(ImageArtUtil.load(log, contentUrl, resolution.toInt), "Input Style"))
      val renderedCanvases = new ArrayBuffer[() => BufferedImage]
      // Execute the main process while registered with the site index
      val registration = registerWithIndexGIF(renderedCanvases.map(_ ()), delay = animationDelay)
      withMonitoredGif(() => renderedCanvases.map(_ ()), delay = animationDelay) {
        try {
          for (pipeline <- List(
            VGG19.getVisionPipeline
          )) {
            import scala.collection.JavaConverters._
            for (layer <- pipeline.getLayers.asScala.keys) {
              log.h1(layer.name())
              val canvas = new AtomicReference[Tensor](null)
              renderedCanvases += (() => {
                val image = canvas.get().toImage
                if (null == image) image else {
                  val graphics = image.getGraphics.asInstanceOf[Graphics2D]
                  graphics.setFont(new Font("Calibri", Font.BOLD, 24))
                  graphics.drawString(layer.name(), 10, 25)
                  image
                }
              })
              withMonitoredJpg(() => Option(canvas.get()).map(_.toRgbImage).orNull) {
                var steps = 0
                Try {
                  log.subreport(layer.name(), (sub: NotebookOutput) => {
                    paint(contentUrl, initUrl, canvas, new VisualStyleContentNetwork(
                      contentLayers = List(layer),
                      contentModifiers = List(
                        new ContentMatcher()
                      ),
                      precision = Precision.Double
                    ), new BasicOptimizer {
                      override val trainingMinutes: Int = 60
                      override val trainingIterations: Int = 50
                      override val maxRate = 1e9

                      override def onStepComplete(trainable: Trainable, currentPoint: Step): Boolean = {
                        steps = steps + 1
                        super.onStepComplete(trainable, currentPoint)
                      }
                    }, new GeometricSequence {
                      override val min: Double = resolution
                      override val max: Double = resolution
                      override val steps = 1
                    }.toStream.map(_.round.toDouble): _*)(sub)
                    null
                  })
                }
                if (steps < 3 && !renderedCanvases.isEmpty) {
                  renderedCanvases.remove(renderedCanvases.size - 1)
                }
                uploadAsync(log)
              }(log)
            }
          }
          null
        } finally {
          registration.foreach(_.stop()(s3client, ec2client))
        }
      }
    }
  }()
}
