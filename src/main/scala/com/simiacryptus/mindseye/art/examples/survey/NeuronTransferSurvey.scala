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

package com.simiacryptus.mindseye.art.examples.survey

import java.awt.image.BufferedImage
import java.awt.{Font, Graphics2D}
import java.net.URI

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.registry.JobRegistration
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.layers.java.AffineImgViewLayer
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.mutable.ArrayBuffer


object NeuronTransferSurvey extends NeuronTransferSurvey with LocalRunner[Object] with NotebookRunner[Object]

class NeuronTransferSurvey extends ArtSetup[Object, NeuronTransferSurvey] {
  val initUrl: String = "upload:Content"
  val contentUrl: String = "upload:Content"
  val s3bucket: String = "test.deepartist.org"
  val minResolution = 512
  val maxResolution = 512
  val steps = 1

  override def indexStr = "202"

  override def description = <div>
    Creates a tiled and rotationally symmetric texture based on a style using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard VGG19 layers</li>
      <li>Operators constraining and enhancing style</li>
      <li>Progressive resolution increase</li>
      <li>Kaleidoscopic view layer in addition to tiling layer</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600


  override def postConfigure(log: NotebookOutput) = {
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    log.eval[() => Unit](() => {
      () => {
        implicit val implicitLog = log
        // First, basic configuration so we publish to our s3 site
        if (Option(s3bucket).filter(!_.isEmpty).isDefined)
          log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
        log.onComplete(() => upload(log): Unit)
        val srcImage = ImageArtUtil.loadImage(log, contentUrl, maxResolution)
        log.p(log.jpg(srcImage, "Input Content"))
        for ((layer, toDim) <- List(
          //(VGG19.VGG19_1b2, 128),
          //(VGG19.VGG19_1c2, 255),
          //(VGG19.VGG19_1c4, 255),
          (VGG19.VGG19_1d4, 64)
          //(VGG19.VGG19_1e4, 512)
        )) {
          val fromDim = Math.max(0, toDim - 64)
          val animationDelay = 1000
          val renderedCanvases = new ArrayBuffer[() => BufferedImage]
          // Execute the main process while registered with the site index
          val registration = registerWithIndexGIF(renderedCanvases.filter(_ != null).map(_ ()).toList, delay = animationDelay)
          withMonitoredGif(() => renderedCanvases.filter(_ != null).map(_ ()).toList, delay = animationDelay) {
            try {
              log.subreport("Neurons in " + layer.name(), (sub: NotebookOutput) => {
                for ((list, page) <- (fromDim until toDim).toStream.grouped(8).zipWithIndex.toStream) {
                  sub.subreport("Page " + page, (sub2: NotebookOutput) => {
                    for (dimensionSelected <- list) {
                      sub2.h2(layer.name() + " " + dimensionSelected)
                      val size = renderedCanvases.size
                      val image = test(layer, dimensionSelected, srcImage)(sub2)
                      if (renderedCanvases.size > size) {
                        renderedCanvases(size) = () => image
                      } else {
                        renderedCanvases += (() => image)
                      }
                    }
                  })
                }
              })
              null
            } finally {
              registration.foreach(_.stop()(s3client, ec2client))
            }
          }
        }
      }
    })()
    null
  }

  def test(layer: VGG19, dimensionSelected: Int, srcImage: BufferedImage)(implicit log: NotebookOutput): BufferedImage = {
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    val registration: Option[JobRegistration[Tensor]] = None
    try {
      val canvas = new RefAtomicReference[Tensor](null)

      def rotatedCanvas = {
        canvas.get()
      }

      // Kaleidoscope+Tiling layer used by the optimization engine.
      // Expands the canvas by a small amount, using tile wrap to draw in the expanded boundary.
      def viewLayer(dims: Seq[Int]) = {
        val min_padding = 8
        val max_padding = 32
        val border_factor = 0.125
        val paddingX = Math.min(max_padding, Math.max(min_padding, dims(0) * border_factor)).toInt
        val paddingY = Math.min(max_padding, Math.max(min_padding, dims(1) * border_factor)).toInt
        val tiling = new AffineImgViewLayer(dims(0) + paddingX, dims(1) + paddingY, true)
        tiling.setOffsetX(-paddingX / 2)
        tiling.setOffsetY(-paddingY / 2)
        List(tiling)
      }

      // Display a pre-tiled image inside the report itself
      withMonitoredJpg(() => Option(rotatedCanvas).map(tensor => {
        val image = tensor.toRgbImage
        tensor.freeRef()
        image
      }).orNull) {
        log.subreport("Painting", (sub: NotebookOutput) => {
          paint(
            contentUrl = contentUrl,
            initUrl = initUrl,
            canvas = canvas.addRef(),
            network = new VisualStyleNetwork(
              styleLayers = List(
                layer
              ),
              styleModifiers = List(
                new SingleChannelEnhancer(dimensionSelected, dimensionSelected + 1)
              ),
              styleUrls = Seq(""),
              viewLayer = viewLayer
            ).withContent(contentLayers = List(
              VGG19.VGG19_1c1
            ), contentModifiers = List(
              new ContentMatcher().scale(2e1)
            )),
            optimizer = new BasicOptimizer {
              override val trainingMinutes: Int = 90
              override val trainingIterations: Int = 30
              override val maxRate = 1e9
            },
            aspect = Option(srcImage.getHeight().doubleValue() / srcImage.getWidth()),
            resolutions = new GeometricSequence {
              override val min: Double = minResolution
              override val max: Double = maxResolution
              override val steps = NeuronTransferSurvey.this.steps
            }.toStream.map(_.round.toDouble))(sub)
          null
        })
        uploadAsync(log)
      }(log)
      val image = rotatedCanvas.toImage
      if (null == image) image else {
        val graphics = image.getGraphics.asInstanceOf[Graphics2D]
        graphics.setFont(new Font("Calibri", Font.BOLD, 24))
        graphics.drawString(layer.name() + " " + dimensionSelected, 10, 25)
        image
      }
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }
}
