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

import java.net.URI

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.java.{ImgTileAssemblyLayer, ImgViewLayer}
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

object TextureTiledRotor extends TextureTiledRotor with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

class TextureTiledRotor extends RotorArt {
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  val styleUrl = ""
  //  val initUrl: String = "50 + noise * 0.5"
  val initUrl: String = "plasma"
  //  val s3bucket: String = "examples.deepartist.org"
  val s3bucket: String = ""
  val minResolution = 128
  val maxResolution = 800
  val magnification = 2
  val rowsAndCols = 2
  val steps = 3
  val aspectRatio = 1
  val repeat = 3
  val min_padding = 64
  val max_padding = 256
  val border_factor = 0.5
  val iterations = 10

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
    log.eval[() => Seq[Tensor]](() => {
      () => {
        implicit val implicitLog = log
        // First, basic configuration so we publish to our s3 site
        if (Option(s3bucket).filter(!_.isEmpty).isDefined) {
          log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
          log.onComplete(() => upload(log): Unit)
        }
        ImageArtUtil.loadImages(log, styleUrl, (maxResolution * Math.sqrt(magnification)).toInt)
          .foreach(img => log.p(log.jpg(img, "Input Style")))
        (1 to repeat).map(_ => {
          val canvas = new RefAtomicReference[Tensor](null)

          def rotatedCanvas = {
            var input = canvas.get()
            if (null == input) input else {
              val viewLayer = getKaleidoscope(input.getDimensions)
              val result = viewLayer.eval(input)
              viewLayer.freeRef()
              val data = result.getData
              result.freeRef()
              val tensor = data.get(0)
              data.freeRef()
              tensor
            }
          }

          // Generates a pretiled image (e.g. 3x3) to display
          def tiledCanvas = {
            var input = rotatedCanvas
            if (null == input) input else {
              val layer = new ImgTileAssemblyLayer(rowsAndCols, rowsAndCols)
              val result = layer.eval((1 to (rowsAndCols * rowsAndCols)).map(_ => input.addRef()): _*)
              layer.freeRef()
              input.freeRef()
              val data = result.getData
              result.freeRef()
              val tensor = data.get(0)
              data.freeRef()
              tensor
            }
          }

          // Kaleidoscope+Tiling layer used by the optimization engine.
          // Expands the canvas by a small amount, using tile wrap to draw in the expanded boundary.
          def viewLayer(dims: Seq[Int]) = {
            val rotor = getKaleidoscope(dims.toArray)
            val paddingX = Math.min(max_padding, Math.max(min_padding, dims(0) * border_factor)).toInt
            val paddingY = Math.min(max_padding, Math.max(min_padding, dims(1) * border_factor)).toInt
            val tiling = new ImgViewLayer(dims(0) + paddingX, dims(1) + paddingY, true)
            tiling.setOffsetX(-paddingX / 2)
            tiling.setOffsetY(-paddingY / 2)
            rotor.add(tiling).freeRef()
            rotor
          }

          // Execute the main process while registered with the site index
          val registration = registerWithIndexJPG(() => tiledCanvas)
          try {
            // Display a pre-tiled image inside the report itself
            withMonitoredJpg(() => {
              val tiledCanvas1 = tiledCanvas
              val toImage = tiledCanvas1.toImage
              tiledCanvas1.freeRef()
              toImage
            }) {
              // Display an additional, non-tiled image of the canvas
              withMonitoredJpg(() => Option(rotatedCanvas).map(tensor => {
                val image = tensor.toRgbImage
                tensor.freeRef()
                image
              }).orNull) {
                log.subreport("Painting", (sub: NotebookOutput) => {
                  paint(
                    contentUrl = initUrl,
                    initUrl = initUrl,
                    canvas = canvas.addRef(),
                    network = getStyle(viewLayer _),
                    optimizer = new BasicOptimizer {
                      override val trainingMinutes: Int = 90
                      override val trainingIterations: Int = iterations
                      override val maxRate = 1e9

                      override def trustRegion(layer: Layer): TrustRegion = null

                      override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
                    },
                    aspect = Option(aspectRatio),
                    resolutions = new GeometricSequence {
                      override val min: Double = minResolution
                      override val max: Double = maxResolution
                      override val steps = TextureTiledRotor.this.steps
                    }.toStream.map(_.round.toDouble))(sub)
                  null
                })
                uploadAsync(log)
              }(log)
            }
            canvas.get()
          } finally {
            registration.foreach(_.stop()(s3client, ec2client))
          }
        })
      }
    })()
  }

  def getStyle(viewLayer: Seq[Int] => PipelineNetwork)(implicit log: NotebookOutput): VisualNetwork = {
    new VisualStyleNetwork(
      styleLayers = List(
        VGG19.VGG19_1c4
      ),
      styleModifiers = List(
        new SingleChannelEnhancer(130, 131)
      ),
      styleUrls = Seq(styleUrl),
      magnification = magnification,
      viewLayer = viewLayer
    )
  }

  def widebandStyle(viewLayer: Seq[Int] => PipelineNetwork)(implicit log: NotebookOutput) = {
    new VisualStyleNetwork(
      styleLayers = List(
        // We select all the lower-level layers to achieve a good balance between speed and accuracy.
        VGG19.VGG19_0a,
        VGG19.VGG19_0b,
        VGG19.VGG19_1a,
        VGG19.VGG19_1b1,
        VGG19.VGG19_1b2,
        VGG19.VGG19_1c1,
        VGG19.VGG19_1c2,
        VGG19.VGG19_1c3,
        VGG19.VGG19_1e1,
        VGG19.VGG19_1e2,
        VGG19.VGG19_1e3
      ),
      styleModifiers = List(
        // These two operators are a good combination for a vivid yet accurate style
        {
          new GramMatrixEnhancer()
            .setMinMax(-5, 5)
          //.scale(0.5)
        },
        {
          new MomentMatcher()
        }
      ),
      styleUrls = Seq(styleUrl),
      magnification = magnification,
      viewLayer = viewLayer
    )
  }

}
