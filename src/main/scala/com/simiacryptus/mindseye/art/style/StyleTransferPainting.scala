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

package com.simiacryptus.mindseye.art.style

import java.awt.image.BufferedImage
import java.net.URI

import com.simiacryptus.mindseye.art.TiledTrainable
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.ArrayTrainable
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.cudnn._
import com.simiacryptus.mindseye.layers.cudnn.conv.SimpleConvolutionLayer
import com.simiacryptus.mindseye.layers.java.BoundedActivationLayer
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.IterativeTrainer
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner


object StyleTransferPainting extends StyleTransferPainting with LocalRunner[Object] with NotebookRunner[Object]

class StyleTransferPainting extends ArtSetup[Object] {

//    val contentUrl = "upload:Content"
//    val styleUrl = "upload:Style"
//    val initUrl: String = "noise * 0.01"
  val contentUrl = "file:///C:/Users/andre/code/all-projects/report/IterativeSimple/d140b0b6-5fa3-4529-a66b-1d4058dd5d04/etc/ross-flag-26868.png"
  val styleUrl = "file:///C:/Users/andre/code/all-projects/report/IterativeSimple/d140b0b6-5fa3-4529-a66b-1d4058dd5d04/etc/shutterstock_157227299.jpg"
  val initUrl: String = "file:///C:/Users/andre/code/all-projects/report/IterativeSimple/d140b0b6-5fa3-4529-a66b-1d4058dd5d04/etc/image_a22131af2926cd15.jpg"
  val s3bucket: String = ""
  val contentWeight = 1e0
  val adjustColors = true

  override def indexStr = "301"

  override def description = <div>
    Paints an image in the style of another using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard VGG19 layers</li>
      <li>Operators to match content and constrain and enhance style</li>
      <li>Progressive resolution increase</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 1


  override def postConfigure(log: NotebookOutput) = {
    implicit val implicitLog = log
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    val contentImage = ImageArtUtil.loadImage(log, contentUrl, 1600)

    val styleAdjustedFiles = for (styleImage <- ImageArtUtil.loadImages(log, styleUrl, (1600 * Math.sqrt(2)).toInt)) yield {
      lazy val colorMappingLayer = {
        val colorMappingLayer = colorMapper()
        val trainable = new ArrayTrainable(lossFunctionColors(colorMappingLayer.addRef()), 1)
        trainable.setTrainingData(Array(Array(Tensor.fromRGB(contentImage), Tensor.fromRGB(styleImage))))
        val trainer = new IterativeTrainer(trainable)
        trainer.setMaxIterations(100)
        trainer.run()
        colorMappingLayer.freeze()
        colorMappingLayer
      }

      val img = if(adjustColors) colorMappingLayer.eval(Tensor.fromRGB(styleImage)).getData.get(0) else Tensor.fromRGB(styleImage)
      log.p(log.jpg(img.toRgbImage, "Adjusted Style"))
      "file:///" + log.jpgFile(img.toRgbImage).getAbsolutePath
    }

    log.p(log.jpg(contentImage, "Input Content"))
    val canvas = new RefAtomicReference[Tensor](null)
    val registration = registerWithIndexJPG(() => canvas.get())
    try {
      withMonitoredJpg(() => canvas.get().toImage) {

//        paint(
//          contentUrl = contentUrl,
//          initUrl = initUrl,
//          canvas = canvas.addRef(),
//          network = new VisualStyleContentNetwork(
//            styleLayers = List(
//              VGG19.VGG19_1b1,
//              VGG19.VGG19_1b2,
//              VGG19.VGG19_1c1,
//              VGG19.VGG19_1c2,
//              VGG19.VGG19_1c3,
//              VGG19.VGG19_1c4,
//              VGG19.VGG19_1d1,
//              VGG19.VGG19_1d2,
//              VGG19.VGG19_1d3,
//              VGG19.VGG19_1d4,
//              VGG19.VGG19_1e1,
//              VGG19.VGG19_1e2,
//              VGG19.VGG19_1e3,
//              VGG19.VGG19_1e4
//            ),
//            styleModifiers = List(
//              new GramMatrixEnhancer(), //.scale(1e-1),
//              new GramMatrixMatcher()
//              //new MomentMatcher()
//            ),
//            styleUrls = styleAdjustedFiles,
//            magnification = 16,
//            contentModifiers = List(
//              new ContentMatcher().scale(contentWeight * 1e1)
//            ),
//            contentLayers = List(
//              VGG19.VGG19_0a.prependAvgPool(1).appendMaxPool(1),
//              VGG19.VGG19_1c1
//            )
//          ),
//          optimizer = new BasicOptimizer {
//            override val trainingMinutes: Int = 90
//            override val trainingIterations: Int = 100
//            override val maxRate = 1e9
//          },
//          aspect = None,
//          resolutions = new GeometricSequence {
//            override val min: Double = 320
//            override val max: Double = 800
//            override val steps = 2
//          }.toStream.map(_.round.toDouble))

        val tile_padding = 64
        val tile_size = 800
        for(res <- new GeometricSequence {
          override val min: Double = 1600
          override val max: Double = 4000
          override val steps = 3
        }.toStream) {

          val contentFull = ImageArtUtil.loadImage(log, contentUrl, res.toInt)
          val originalCanvas = getCanvas(canvas, contentFull, log)
          val selectors_fade = TiledTrainable.selectors(tile_padding, contentFull.getWidth, contentFull.getHeight, tile_size, true)
          val selectors_sharp = TiledTrainable.selectors(tile_padding, contentFull.getWidth, contentFull.getHeight, tile_size, false)
          val contentTensor = Tensor.fromRGB(contentFull)
          val tiles = for ((tileView_sharp, tileView_fade) <- selectors_sharp.zip(selectors_fade)) yield {
            val originalCanvasTile = tileView_sharp.eval(originalCanvas.addRef()).getData.get(0)
            val tileWidth = originalCanvasTile.getDimensions()(0)
            val canvasRef = new RefAtomicReference[Tensor](originalCanvasTile)
            paint(
              contentUrl = "file:///" + log.jpgFile(tileView_sharp.eval(contentTensor.addRef()).getData.get(0).toRgbImage).getAbsolutePath,
              initUrl = null,
              canvas = canvasRef.addRef(),
              network = new VisualStyleContentNetwork(
                styleLayers = List(
                  VGG19.VGG19_1b1,
                  VGG19.VGG19_1b2,
                  VGG19.VGG19_1c1,
                  VGG19.VGG19_1c2,
                  VGG19.VGG19_1c3,
                  VGG19.VGG19_1c4,
                  VGG19.VGG19_1d1,
                  VGG19.VGG19_1d2,
                  VGG19.VGG19_1d3,
                  VGG19.VGG19_1d4
                ),
                styleModifiers = List(
                  new GramMatrixEnhancer(),
                  new GramMatrixMatcher()
                ),
                styleUrls = styleAdjustedFiles,
                magnification = res / tileWidth,
                contentLayers = List(
                  VGG19.VGG19_1c1.prependAvgPool(1)
                ),
                contentModifiers = List(
                  new ContentMatcher().scale(contentWeight * 1e-5)
                )
              ),
              optimizer = new BasicOptimizer {
                override val trainingMinutes: Int = 60
                override val trainingIterations: Int = 20
                override val maxRate = 1e9
              },
              aspect = None,
              resolutions = new GeometricSequence {
                override val min: Double = tileWidth
                override val max: Double = tileWidth
                override val steps = 1
              }.toStream.map(_.round.toDouble))
            val canvasTile = canvasRef.get()
            canvasRef.freeRef()
            log.out(log.jpg(canvasTile.toRgbImage, "Canvas Tile"))
            val maskTile = tileView_fade.eval(originalCanvas.map(x => 1)).getData.get(0)
            log.out(log.jpg(maskTile.toRgbImage, "Tile Mask"))
            val product = canvasTile.mapCoords(c => canvasTile.get(c) * maskTile.get(c))
            log.out(log.jpg(product.toRgbImage, "Tile Product"))
            product
          }
          canvas.set(reassemble(canvas.get(), selectors_fade, tiles, log))
        }
      }
      null
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }
  def getCanvas(canvas: RefAtomicReference[Tensor], content:BufferedImage, log: NotebookOutput) = {
    var originalCanvas = canvas.get()
    val width = content.getWidth
    val height = content.getHeight
    if (originalCanvas == null) {
      originalCanvas = Tensor.fromRGB(ImageArtUtil.loadImage(log, initUrl, width, height))
      canvas.set(originalCanvas)
    } else if (originalCanvas.getDimensions()(0) != width) {
      originalCanvas = Tensor.fromRGB(ImageUtil.resize(originalCanvas.toRgbImage, width, height))
      canvas.set(originalCanvas)
    }
    originalCanvas
  }

  private def reassemble(contentTensor: Tensor, selectors: Array[Layer], tiles: Array[Tensor], log: NotebookOutput) = {
    val reassemblyNetwork = new PipelineNetwork(1 + tiles.size)
    reassemblyNetwork.add(new SumInputsLayer(), (
      for ((selector, index) <- selectors.zipWithIndex) yield {
        reassemblyNetwork.add(
          lossFunction(selector.addRef().asInstanceOf[Layer], () => new PipelineNetwork(1)),
          reassemblyNetwork.getInput(index + 1),
          reassemblyNetwork.getInput(0))
      }): _*).freeRef()
    val trainable = new ArrayTrainable(reassemblyNetwork, 1)
    trainable.setTrainingData(Array(Array(contentTensor.addRef()) ++ tiles.map(_.addRef())))
    trainable.setMask(Array(true) ++ tiles.map(_ => false): _*)
    val trainer = new IterativeTrainer(trainable)
    trainer.setMaxIterations(100)
    trainer.run()
    log.out(log.jpg(contentTensor.toRgbImage, "Combined Image"))
    contentTensor
  }

  def colorMapper() = {
    val colorMappingLayer = new PipelineNetwork(1)
    val colorProduct = new SimpleConvolutionLayer(1, 1, 3, 3)
    colorMappingLayer.add(new ImgBandBiasLayer(3)).freeRef()
    colorMappingLayer.add(colorProduct.addRef()).freeRef()
    val boundedActivationLayer = new BoundedActivationLayer()
    boundedActivationLayer.setMinValue(0)
    boundedActivationLayer.setMaxValue(255)
    colorMappingLayer.add(boundedActivationLayer).freeRef()
    val kernel = colorProduct.getKernel
    kernel.setByCoord(c => {
      val b = c.getCoords()(2)
      if ((b / 3) == (b % 3)) 1.0
      else 0.0
    })
    kernel.freeRef
    colorMappingLayer
  }

  def lossFunctionColors(colorMappingLayer: Layer) = {
    val network = new PipelineNetwork(2)
    network.add(
      new BinarySumLayer(1.0, 1.0),
      network.add(
        lossFunction(colorMappingLayer.addRef().asInstanceOf[Layer], () => new GramianLayer()),
        network.getInput(0),
        network.getInput(1)
      ),
      network.add(
        lossFunction(colorMappingLayer, ()=>new BandAvgReducerLayer()),
        network.getInput(0),
        network.getInput(1)
      )
    ).freeRef()
    network
  }

  def lossFunction(colorMappingLayer: Layer, summarizer: ()=>Layer) = {
    val network = new PipelineNetwork(2)
    network.add(
      new AvgReducerLayer(),
      network.add(
        new SquareActivationLayer(),
        network.add(
          new BinarySumLayer(1.0, -1.0),
          network.add(
            summarizer(),
            network.getInput(0)),
          network.add(
            summarizer(),
            network.add(
              colorMappingLayer,
              network.getInput(1)
            ))
        ))
    ).freeRef()
    network
  }
}
