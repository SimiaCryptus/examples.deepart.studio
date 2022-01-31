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

package com.simiacryptus.mindseye.art.examples.texture

import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.net.URI

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.{ImageOptimizer, _}
import com.simiacryptus.mindseye.lang.{Coordinate, Tensor}
import com.simiacryptus.mindseye.layers.java.AffineImgViewLayer
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.LocalRunner

object TextureStereogram extends TextureStereogram with LocalRunner[Object] with NotebookRunner[Object]

class TextureStereogram extends ArtSetup[Object, TextureStereogram] {

  val styleUrl = "upload:Style"
  val text =
    """
      |DeepArtist
    """.stripMargin.trim
  val initUrl: String = "50 + plasma * 0.5"
  val s3bucket: String = "test.deepartist.org"
  val minWidth = 50
  val maxWidth = 100
  val maxHeight = 1200
  val magnification = Array(4.0)
  val depthFactor = 10
  val maxAspect = 2
  val text_padding = 256

  override def indexStr = "203"

  override def description = <div>
    Creates simple stereogram based on a very tall tiled texture rendered using:
    <ol>
      <li>Random plasma initialization</li>
      <li>Standard VGG16 layers</li>
      <li>Operators constraining and enhancing style</li>
      <li>Progressive resolution increase</li>
      <li>View layer to enforce tiling</li>
      <li>Final rendering process combining the texture with a depth map to produce a stereogram</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    () => {
      implicit val implicitLog = log
      // First, basic configuration so we publish to our s3 site
      if (Option(s3bucket).filter(!_.isEmpty).isDefined)
        log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch image (user upload prompt) and display a rescaled copy
      log.out(log.jpg(ImageArtUtil.loadImage(log, styleUrl, (maxWidth * Math.sqrt(magnification.head)).toInt), "Input Style"))
      // Render and display the depth map
      val depthImage = depthMap((maxHeight * maxAspect).toInt, maxHeight, text)
      log.out(log.jpg(depthImage.toImage, "Depth Map"))
      val canvas = new RefAtomicReference[Tensor](null)

      // Renders the sterogram
      def rendered = {
        val input = canvas.get()
        if (null == input) input else {
          Tensor.fromRGB(stereoImage(depthImage, input, depthFactor))
        }
      }

      // Tiling layer used by the optimization engine.
      // Expands the canvas by a small amount, using tile wrap to draw in the expanded boundary.
      def tiled(dims: Seq[Int]) = {
        val padding = Math.min(256, Math.max(16, dims(0) / 2))
        val layer = new AffineImgViewLayer(dims(0) + padding, dims(1) + padding, true)
        layer.setOffsetX(-padding / 2)
        layer.setOffsetY(-padding / 2)
        List(layer)
      }

      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(() => rendered)
      try {
        // Display the stereogram
        withMonitoredJpg(() => rendered.toImage) {
          // Display an additional single tile of the texture canvas
          withMonitoredJpg(() => Option(canvas.get()).map(_.toRgbImage).orNull) {
            log.subreport("Painting", (sub: NotebookOutput) => {
              texture(maxHeight.toDouble / maxWidth, initUrl, canvas, new VisualStyleNetwork(
                styleLayers = List(
                  // We select all the lower-level layers to achieve a good balance between speed and accuracy.
                  VGG16.VGG16_0b,
                  VGG16.VGG16_1a,
                  VGG16.VGG16_1b1,
                  VGG16.VGG16_1b2,
                  VGG16.VGG16_1c1,
                  VGG16.VGG16_1c2,
                  VGG16.VGG16_1c3
                ),
                styleModifiers = List(
                  // These two operators are a good combination for a vivid yet accurate style
                  new GramMatrixEnhancer(),
                  new MomentMatcher()
                ),
                styleUrls = List(styleUrl),
                magnification = magnification,
                viewLayer = tiled
              ), new ImageOptimizer {
                override val trainingMinutes: Int = 60
                override val trainingIterations: Int = 30
                override val maxRate = 1e9
              }, new GeometricSequence {
                override val min: Double = minWidth
                override val max: Double = maxWidth
                override val steps = 2
              }.toStream.map(_.round.toDouble))(sub)
              null
            })
            uploadAsync(log)
          }(log)
        }
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()

  def depthMap(width: Int, height: Int, text: String, fontName: String = "Calibri")(implicit log: NotebookOutput): Tensor = {
    val font = TextUtil.fit(text, width, height, text_padding, fontName, Font.BOLD | Font.CENTER_BASELINE)
    val bounds: Rectangle2D = TextUtil.measure(font, text)
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
    val depthScale = canvasWidth / (depthFactor * depthMap.doubleStream().max().getAsDouble)

    def getPixel(x: Int, y: Int, c: Int): Double = {
      if (x < 0) getPixel(x + canvasWidth, y, c)
      else if (x < canvasWidth) canvas.get(x, y % dimensions(1), c)
      else {
        val depth = depthMap.get(x, y, c)
        if (0 == depth) canvas.get(x % canvasWidth, y % dimensions(1), c)
        else getPixel(x - canvasWidth + (depthScale * depth).toInt, y, c)
      }
    }

    val tensor = depthMap.copy()
    tensor.setByCoord((c: Coordinate) => {
      val ints = c.getCoords()
      getPixel(ints(0), ints(1), ints(2))
    }, true)
    depthMap.freeRef()
    canvas.freeRef()
    tensor.toRgbImage
  }

}
