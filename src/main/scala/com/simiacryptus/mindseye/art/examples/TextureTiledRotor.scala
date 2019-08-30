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

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.java.{ImgTileAssemblyLayer, ImgViewLayer}
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

object TextureTiledRotor extends TextureTiledRotor with LocalRunner[Object] with NotebookRunner[Object]

class TextureTiledRotor extends RotorArt {

  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  val styleUrl = "upload:Style"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  val minResolution = 128
  val maxResolution = 400
  val magnification = 4
  val rowsAndCols = 3
  val steps = 3

  override def indexStr = "202"

  override def description = <div>
    Creates a tiled and rotationally symmetric texture based on a style using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard VGG16 layers</li>
      <li>Operators constraining and enhancing style</li>
      <li>Progressive resolution increase</li>
      <li>Kaleidoscopic view layer in addition to tiling layer</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600


  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      log.p(log.jpg(ImageArtUtil.load(log, styleUrl, (maxResolution * Math.sqrt(magnification)).toInt), "Input Style"))
      val canvas = new AtomicReference[Tensor](null)

      def rotatedCanvas = {
        var input = canvas.get()
        if (null == input) input else {
          val viewLayer = getKaleidoscope(input.getDimensions)
          viewLayer.eval(input).getDataAndFree.getAndFree(0)
        }
      }

      // Generates a pretiled image (e.g. 3x3) to display
      def tiledCanvas = {
        var input = rotatedCanvas
        if (null == input) input else {
          val layer = new ImgTileAssemblyLayer(rowsAndCols, rowsAndCols)
          val tensor = layer.eval((1 to (rowsAndCols * rowsAndCols)).map(_ => input): _*).getDataAndFree.getAndFree(0)
          layer.freeRef()
          tensor
        }
      }

      // Kaleidoscope+Tiling layer used by the optimization engine.
      // Expands the canvas by a small amount, using tile wrap to draw in the expanded boundary.
      def viewLayer(dims: Seq[Int]) = {
        val padding = Math.min(256, Math.max(16, dims(0) / 2))
        val viewLayer = getKaleidoscope(dims.toArray).copyPipeline()
        viewLayer.wrap(new ImgViewLayer(dims(0) + padding, dims(1) + padding, true)
          .setOffsetX(-padding / 2).setOffsetY(-padding / 2)
        ).freeRef()
        viewLayer
      }

      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(tiledCanvas)
      try {
        // Display a pre-tiled image inside the report itself
        withMonitoredJpg(() => tiledCanvas.toImage) {
          // Display an additional, non-tiled image of the canvas
          withMonitoredJpg(() => Option(rotatedCanvas).map(_.toRgbImage).orNull) {
            log.subreport("Painting", (sub: NotebookOutput) => {
              paint(initUrl, initUrl, canvas, new VisualStyleNetwork(
                styleLayers = List(
                  // We select all the lower-level layers to achieve a good balance between speed and accuracy.
                  VGG16.VGG16_0,
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
                styleUrl = List(styleUrl),
                magnification = magnification,
                viewLayer = viewLayer
              ), new BasicOptimizer {
                override val trainingMinutes: Int = 30
                override val trainingIterations: Int = 10
                override val maxRate = 1e9
                override def trustRegion(layer: Layer): TrustRegion = null
                override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray).copyPipeline()
              }, new GeometricSequence {
                override val min: Double = minResolution
                override val max: Double = maxResolution
                override val steps = TextureTiledRotor.this.steps
              }.toStream.map(_.round.toDouble): _*)(sub)
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
}
