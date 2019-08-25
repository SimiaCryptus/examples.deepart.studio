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

import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.{Coordinate, Tensor}
import com.simiacryptus.mindseye.layers.java.ImgViewLayer
import com.simiacryptus.mindseye.opt.Step
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.util.Try

object TextureStereogram extends TextureStereogram with LocalRunner[Object] with NotebookRunner[Object]

class TextureStereogram extends ArtSetup[Object] {

  val styleUrl = "upload:Style"
  val text =
    """
      |deepart
      |.studio
    """.stripMargin.trim
  val initUrl: String = "50 + plasma * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  val minWidth = 64
  val maxWidth = 128
  val maxHeight = 1200
  val magnification = 4
  val depthFactor = 8
  val maxAspect = 3.5
  val text_padding = 256
  override def indexStr = "203"

  override def description =
    """
      |Creates a very tall tiled texture based on a style, then uses it to render a simple stereogram.
      |""".stripMargin.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
    implicit val _ = log
    log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/"))
    log.onComplete(() => upload(log): Unit)
    log.out(log.jpg(ImageArtUtil.load(log, styleUrl, (maxWidth * Math.sqrt(magnification)).toInt), "Input Style"))
    val depthImage = depthMap((maxHeight * maxAspect).toInt, maxHeight, text)
    log.out(log.jpg(depthImage.toImage, "Depth Map"))
    val canvas = new AtomicReference[Tensor](null)

    def rendered = {
      val input = canvas.get()
      if (null == input) input else {
        Tensor.fromRGB(stereoImage(depthImage, input, depthFactor))
      }
    }

    def tiled(dims: Seq[Int]) = {
      val padding = Math.min(256, Math.max(16, dims(0) / 2))
      new ImgViewLayer(dims(0) + padding, dims(1) + padding, true)
        .setOffsetX(-padding / 2).setOffsetY(-padding / 2)
    }

    val registration = registerWithIndexJPG(rendered)
    NotebookRunner.withMonitoredJpg(() => rendered.toImage) {
      try {
        withMonitoredJpg(() => Option(canvas.get()).map(_.toRgbImage).orNull) {
          var steps = 0
          Try {
            log.subreport("Painting", (sub: NotebookOutput) => {
              texture(maxHeight.toDouble / maxWidth, initUrl, canvas, new VisualStyleNetwork(
                styleLayers = List(
                  VGG16.VGG16_0,
                  VGG16.VGG16_1a,
                  VGG16.VGG16_1b1,
                  VGG16.VGG16_1b2,
                  VGG16.VGG16_1c1,
                  VGG16.VGG16_1c2,
                  VGG16.VGG16_1c3
                ),
                styleModifiers = List(
                  new GramMatrixEnhancer(),
                  new MomentMatcher()
                ),
                styleUrl = List(styleUrl),
                magnification = magnification,
                viewLayer = tiled
              ), new BasicOptimizer {
                override val trainingMinutes: Int = 60
                override val trainingIterations: Int = 30
                override val maxRate = 1e9

                override def onStepComplete(trainable: Trainable, currentPoint: Step): Boolean = {
                  steps = steps + 1
                  super.onStepComplete(trainable, currentPoint)
                }
              }, new GeometricSequence {
                override val min: Double = minWidth
                override val max: Double = maxWidth
                override val steps = 2
              }.toStream.map(_.round.toDouble))(sub)
              null
            })
          }
          uploadAsync(log)
        }(log)
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }}()

  def depthMap(width: Int, height: Int, text: String, fontName: String = "Calibri")(implicit log: NotebookOutput): Tensor = {
    val font = log.eval(() => {
      TextUtil.fit(text, width, height, text_padding, fontName, Font.BOLD | Font.CENTER_BASELINE)

    })
    val bounds: Rectangle2D = log.eval(() => {
      TextUtil.measure(font, text)
    })
    Tensor.fromRGB(TextUtil.draw(text,
      (bounds.getWidth + 2.0 * text_padding).toInt,
      Math.max((bounds.getHeight + 2.0 * text_padding).toInt, height),
      text_padding,
      font,
      bounds
    ))
  }

  def stereoImage(depthMap: Tensor, canvas: Tensor, depthFactor: Int): BufferedImage = {
    val dimensions = canvas.getDimensions
    val canvasWidth = dimensions(0)
    val depthScale = canvasWidth / (depthFactor * depthMap.getData.max)

    def getPixel(x: Int, y: Int, c: Int): Double = {
      if (x < 0) getPixel(x + canvasWidth, y, c)
      else if (x < canvasWidth) canvas.get(x, y % dimensions(1), c)
      else {
        val depth = depthMap.get(x, y, c)
        if (0 == depth) canvas.get(x % canvasWidth, y % dimensions(1), c)
        else getPixel(x - canvasWidth + (depthScale * depth).toInt, y, c)
      }
    }

    depthMap.copy().setByCoord((c: Coordinate) => {
      val ints = c.getCoords()
      getPixel(ints(0), ints(1), ints(2))
    }, true).toRgbImage
  }

}
