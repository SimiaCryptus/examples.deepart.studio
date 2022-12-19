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

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.TiledTrainable
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.ImageArtUtil._
import com.simiacryptus.mindseye.art.util.{VisionLayerListJson, _}
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg

import java.net.URI
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable


class BigTextureTiles_Simple extends ArtSetup[Object, BigTextureTiles_Simple] with ImageTileProcessor with ArtworkStyleGalleries {


  override def inputTimeoutSeconds = 0

  val tile_size: Int = 600
  val tile_padding: Int = 64


  //val aspectRatio = 0.5774 // Hex Tiling
  //  val aspectRatio = 1 / 0.61803398875 // Golden Ratio
  val stages = Array(
    new BigTextureTiles_Simple_Stage(
      minRes = 1800,
      maxRes = 4200,
      resSteps = 3)
  )

  def styleUrls = stages.flatMap(_.styleUrls).distinct

  def initUrl = stages.map(_.initUrl).distinct.head

  override def indexStr = "201"


  override def description = <div>
    Creates a large texture based on a style using:
    <ol>
      <li>Random plasma initialization</li>
      <li>Standard VGG19 layers</li>
      <li>Operators constraining and enhancing style</li>
      <li>Progressive resolution increase</li>
      <li>View layer to enforce tiling</li>
    </ol>
  </div>.toString.trim

  override def postConfigure(log: NotebookOutput) = {
    implicit val implicitLog = log
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    // First, basic configuration so we publish to our s3 site
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    // Fetch image (user upload prompt) and display a rescaled copy
    log.subreport("Input Images", (sub: NotebookOutput) => {
      val lowRes = styleGalleries_lowRes(styleUrls).filter(!styleUrls.contains(_))
      val nonGallery = styleUrls.filter(!lowRes.contains(_))
      sub.h1("Styles")
      if (nonGallery.nonEmpty) {
        loadImages(sub, nonGallery.toList.asJava, -1).foreach(img => sub.p(sub.jpg(img, "Input Style")))
      }
      if (lowRes.nonEmpty) {
        sub.h2("Low Res Galleries")
        loadImages(sub, lowRes.asJava, -1).foreach(img => sub.p(sub.jpg(img, "Input Style")))
      }
      val highRes = styleGalleries_highRes(styleUrls).filter(!styleUrls.contains(_)).filter(!lowRes.contains(_))
      if (highRes.nonEmpty) {
        sub.h2("High Res Galleries")
        loadImages(sub, highRes.asJava, -1).foreach(img => sub.p(sub.jpg(img, "Input Style")))
      }

      sub.h1("Initial Image")
      sub.p("Note - This is an illustration/upload only. It will be re-rendered.")
      loadImages(sub, List(initUrl).asJava, 1024).foreach(img => sub.p(sub.jpg(img, "Initial Image")))
    })
    val canvas = new RefAtomicReference[Tensor](null)

    // Execute the main process while registered with the site index
    val registration = registerWithIndexJPG(() => canvas.get())

    try {
      withMonitoredJpg(() => Option(canvas.get()).map(tensor => {
        val image = tensor.toRgbImage
        tensor.freeRef()
        image
      }).orNull) {


        implicit val parent = this
        stages.foreach(_.run(canvas))

      }
      null
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
      canvas.freeRef()
    }
  }


}

case class BigTextureTiles_Simple_Stage
(
  minRes: Int,
  maxRes: Int,
  resSteps: Int,
  styleUrls: Array[String] = Array("upload:Style"),
  initUrl: String = "upload:Init",
  aspectRatio: Double = 0.0,
  enhancementScale: Double = 1e0,
  enhancementLimit: Double = .1,
  minMag: Double = 1.0,
  maxMag: Double = 1.0,
  magSteps: Int = 1,
  trainingIterations: Int = 15,
  trainingMinutes: Int = 60,
  maxRate: Double = 1e9,
  styleLayers: VisionLayerListJson = new VisionLayerListJson(
    VGG19.VGG19_1b1,
    VGG19.VGG19_1b2,
    VGG19.VGG19_1c1,
    VGG19.VGG19_1c2,
    VGG19.VGG19_1c3,
    VGG19.VGG19_1c4
  )
) {
  type Parent = ArtSetup[_, _] with ImageTileProcessor with ArtworkStyleGalleries {
    def tile_size: Int
  }

  def run(canvas: RefAtomicReference[Tensor])(implicit log: NotebookOutput, parent: Parent): Unit = {
    val layers = styleLayers.getLayerList().flatMap(layer => List(
      layer,
      layer.prependAvgPool(2)
    ))
    val modifiers = List(
      new GramMatrixEnhancer().setMinMax(-enhancementLimit, enhancementLimit).scale(enhancementScale),
      new GramMatrixMatcher()
    )
    paintPerTile(
      canvasRef = canvas,
      style = (width: Int) => new VisualStyleNetwork(
        styleLayers = layers,
        styleModifiers = modifiers,
        styleUrls = parent.styleGalleries_highRes(styleUrls),
        magnification = Array(width.toDouble / parent.tile_size).flatMap(x => new GeometricSequence {
          override val min: Double = minMag
          override val max: Double = maxMag
          override val steps = magSteps
        }.toStream.map(_ * x))
      ),
      resolutions = new GeometricSequence {
        override val min: Double = minRes
        override val max: Double = maxRes
        override val steps = resSteps
      },
      optimizer = new ImageOptimizer {
        override val trainingMinutes: Int = BigTextureTiles_Simple_Stage.this.trainingMinutes
        override val trainingIterations: Int = BigTextureTiles_Simple_Stage.this.trainingIterations
        override val maxRate = BigTextureTiles_Simple_Stage.this.maxRate
      })

  }

  def paintPerTile(canvasRef: RefAtomicReference[Tensor], style: Int => VisualStyleNetwork, resolutions: GeometricSequence, optimizer: ImageOptimizer)(implicit log: NotebookOutput, parent: Parent): Unit = {
    resolutions.toStream.map(_.round.toInt).foreach(res => {
      log.subreport(s"Resolution $res", (sub: NotebookOutput) => {

        initCanvas(canvasRef, res)(sub)
        tuneCanvas(canvasRef, parent.tile_size, parent.tile_padding)

        val cache = new mutable.HashMap[List[Int], (Tensor, Trainable)]()

        def getTrainer(dims: List[Int]) = {
          val (tile, styleTrainable) = cache.getOrElseUpdate(dims, {
            val tile = new Tensor(dims: _*)
            val styleTrainable: Trainable = parent.stylePrepFn(
              contentUrl = initUrl,
              network = style(res),
              canvas = tile.addRef(),
              width = parent.tile_size
            )(sub)
            (tile, styleTrainable)
          })
          (tile.addRef(), styleTrainable.addRef())
        }

        parent.paintPerTile(canvasRef, parent.tile_size, (tileInput, tileCanvasRef) => {
          val (tileCanvas, styleTrainable) = getTrainer(tileInput.getDimensions.toList)
          tileCanvas.set(tileInput)
          tileCanvasRef.set(tileCanvas.addRef())
          optimizer.optimize(tileCanvas, styleTrainable)(sub)
        })(sub)

      }: Unit)
    })
  }

  def initCanvas(canvasRef: RefAtomicReference[Tensor], res: Int)(implicit log: NotebookOutput): Unit = {
    val canvas = canvasRef.get()
    if (canvas == null) {
      canvasRef.set(getImageTensors(initUrl, log, res).head)
    } else {
      canvasRef.set(Tensor.fromRGB(ImageUtil.resize(canvas.toRgbImage, res, true)))
      canvas.freeRef()
    }
  }

  def tuneCanvas(canvasRef: RefAtomicReference[Tensor], tileSize: Int, padding: Int): Unit = {
    val canvas = canvasRef.get()
    val dims = canvas.getDimensions()
    val width = TiledTrainable.closestEvenSize(tileSize, padding, dims(0))
    val height = TiledTrainable.closestEvenSize(tileSize, padding, dims(1))
    canvasRef.set(Tensor.fromRGB(ImageUtil.resize(canvas.toRgbImage, width, height)))
    canvas.freeRef()
  }

}

