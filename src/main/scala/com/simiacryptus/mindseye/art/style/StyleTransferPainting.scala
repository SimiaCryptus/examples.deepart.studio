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
import com.simiacryptus.mindseye.eval.{ArrayTrainable, Trainable}
import com.simiacryptus.mindseye.lang.cudnn.{CudaSettings, Precision}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.cudnn._
import com.simiacryptus.mindseye.layers.cudnn.conv.SimpleConvolutionLayer
import com.simiacryptus.mindseye.layers.java.BoundedActivationLayer
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.IterativeTrainer
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.lang.RefUtil
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner

object StyleTransferPainting extends StyleTransferPainting with LocalRunner[Object] with NotebookRunner[Object]

class StyleTransferPainting extends ArtSetup[Object] {

//  val styleUrl1 = "upload:Style1"
//  val styleUrl2 = "upload:Style2"
  val styleUrl1 = "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/9b306f00-011a-4fdb-ab2a-61e24d00c946/etc/pulleys_1.jpg"
  def styleUrl2 = styleUrl1
//  val contentUrl  = "upload:Content"
//  val styleUrl = "upload:Style"
//  val initUrl: String = "upload:Content"
  //val styleUrl = "file:///C:/Users/andre/code/all-projects/report/StyleTransferPainting/57a08e28-2280-458a-8de5-a6c3416830c6/etc/as2gf6b9zv041.jpg"
  val contentUrl = "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/9b306f00-011a-4fdb-ab2a-61e24d00c946/etc/IMG_20160806_144713504_HDR.jpg"
  val initUrl: String = "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/9b306f00-011a-4fdb-ab2a-61e24d00c946/etc/image_9c1dd89160abcaba.jpg"
//val initUrl: String = "10 + noise * 0.2 "
//  val initUrl: String = "plasma + noise * 0.2 "
  val s3bucket: String = "www.tigglegickle.com"
  val contentWeight = 1e1
  val adjustColors = false
  val skipResolution = 1600
  val tiles = List(
    // For failed runs, put tiles here
  )

  override def indexStr = "301"

  override def inputTimeoutSeconds = 1

  private val tileBuffer = tiles.map(ImageUtil.getTensor(_)).toBuffer

  override def description = <div>
    Paints an image in the style of another using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard VGG19 layers</li>
      <li>Operators to match content and constrain and enhance style</li>
      <li>Progressive resolution increase</li>
    </ol>
  </div>.toString.trim

  def updateCanvas(prev: Tensor, reference: BufferedImage)(implicit log: NotebookOutput) = {
    val width = reference.getWidth
    val height = reference.getHeight
    if (width == prev.getDimensions()(0) && height == prev.getDimensions()(1)) {
      prev
    } else {
      val image = prev.toRgbImage
      prev.freeRef()
      Tensor.fromRGB(ImageUtil.resize(image, width, height))
    }
  }


  override def postConfigure(log: NotebookOutput) = {

    implicit val implicitLog = log

    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    val contentImage = ImageArtUtil.loadImage(log, contentUrl, 3395)

    log.p(log.jpg(contentImage, "Input Content"))

    val (styleAdjustedFiles1, styleAdjustedFiles2) = log.subreport("Styles", (sublog:NotebookOutput) => {
      lazy val styleAdjustedFiles1 = for (styleImage <- ImageArtUtil.loadImages(sublog, styleUrl1, (1600 * Math.sqrt(2)).toInt)) yield {
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

        val img = if (adjustColors) colorMappingLayer.eval(Tensor.fromRGB(styleImage)).getData.get(0) else Tensor.fromRGB(styleImage)
        sublog.p(sublog.jpg(img.toRgbImage, "Adjusted Style"))
        "file:///" + sublog.jpgFile(img.toRgbImage).getAbsolutePath
      }

      lazy val styleAdjustedFiles2 = for (styleImage <- ImageArtUtil.loadImages(sublog, styleUrl2, (1600 * Math.sqrt(2)).toInt)) yield {
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

        val img = if (adjustColors) colorMappingLayer.eval(Tensor.fromRGB(styleImage)).getData.get(0) else Tensor.fromRGB(styleImage)
        sublog.p(sublog.jpg(img.toRgbImage, "Adjusted Style"))
        "file:///" + sublog.jpgFile(img.toRgbImage).getAbsolutePath
      }
      (styleAdjustedFiles1, styleAdjustedFiles2)
    })

    var canvas: Tensor = null
    val registration = registerWithIndexJPG(() => if(canvas==null) null else canvas.addRef())
    try {
      withMonitoredJpg(() => if(canvas==null) null else canvas.toImage) {
        // Initial Phase
        if(skipResolution < 256) {
          def res = 256
          val styleContentNetwork = new VisualStyleContentNetwork(
            styleLayers = List(
//              VGG19.VGG19_1a,
//              VGG19.VGG19_1b1,
              VGG19.VGG19_1b2,
              VGG19.VGG19_1c1,
              VGG19.VGG19_1c2,
              VGG19.VGG19_1c3,
              VGG19.VGG19_1c4,
              VGG19.VGG19_1d1,
              VGG19.VGG19_1d2,
              VGG19.VGG19_1d3,
              VGG19.VGG19_1d4,
              //      VGG19.VGG19_1e1,
              //      VGG19.VGG19_1e2,
              //      VGG19.VGG19_1e3,
              //      VGG19.VGG19_1e4
            ),
            styleModifiers = List(
              //new GramMatrixEnhancer().setMinMax(-10, 10),
              new GramMatrixMatcher()
            ),
            styleUrls = styleAdjustedFiles1,
            magnification = Array(64.0),
            contentModifiers = List(
              new ContentMatcher().scale(contentWeight * 7e-1)
            ),
            contentLayers = List(
//              VGG19.VGG19_1a
              VGG19.VGG19_1c1
            )
          )
          log.h1("Resolution " + res)
          val (currentCanvas: Tensor, trainable: Trainable) = {
            CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
            val content: BufferedImage = ImageArtUtil.loadImage(log, contentUrl, res.toInt)
            if (null == canvas) {
              canvas = ImageArtUtil.getImageTensor(initUrl, log, content.getWidth, content.getHeight)
              canvas.watch()
            } else {
              canvas = updateCanvas(canvas, content)
              canvas.watch()
            }
            val trainable: Trainable = styleContentNetwork.apply(canvas.addRef(), Tensor.fromRGB(content))
            ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], styleContentNetwork.precision)
            (canvas.addRef(), trainable)
          }
          new BasicOptimizer {
            override val trainingMinutes: Int = 90
            override val trainingIterations: Int = 100
            override val maxRate = 1e9
          }.optimize(currentCanvas, trainable)
        }

        if(skipResolution < 512) {
          val styleContentNetwork = new VisualStyleContentNetwork(
            styleLayers = List(
              VGG19.VGG19_1a,
              VGG19.VGG19_1b1,
              VGG19.VGG19_1b2,
              VGG19.VGG19_1c1,
              VGG19.VGG19_1c2,
              VGG19.VGG19_1c3,
              VGG19.VGG19_1c4,
              VGG19.VGG19_1d1,
              VGG19.VGG19_1d2,
              VGG19.VGG19_1d3,
              VGG19.VGG19_1d4,
              //      VGG19.VGG19_1e1,
              //      VGG19.VGG19_1e2,
              //      VGG19.VGG19_1e3,
              //      VGG19.VGG19_1e4
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-2, 2),
              new GramMatrixMatcher()
            ),
            styleUrls = styleAdjustedFiles1,
            magnification = Array(32.0),
            contentModifiers = List(
              new ContentMatcher().scale(contentWeight)
            ),
            contentLayers = List(
              VGG19.VGG19_1c1
            )
          )
          val res = 512.0
          log.h1("Resolution " + res)
          val (currentCanvas: Tensor, trainable: Trainable) = {
            CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
            val content: BufferedImage = ImageArtUtil.loadImage(log, contentUrl, res.toInt)
            if (null == canvas) {
              canvas = ImageArtUtil.getImageTensor(initUrl, log, content.getWidth, content.getHeight)
            } else {
              canvas = updateCanvas(canvas, content)
            }
            val trainable: Trainable = styleContentNetwork.apply(canvas.addRef(), Tensor.fromRGB(content))
            ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], styleContentNetwork.precision)
            (canvas.addRef(), trainable)
          }
          new BasicOptimizer {
            override val trainingMinutes: Int = 90
            override val trainingIterations: Int = 100
            override val maxRate = 1e9
          }.optimize(currentCanvas, trainable)
        }

        if(skipResolution < 960) {
          val styleContentNetwork = new VisualStyleContentNetwork(
            styleLayers = List(
              VGG19.VGG19_1a,
              VGG19.VGG19_1b1,
              VGG19.VGG19_1b2,
              VGG19.VGG19_1c1,
              VGG19.VGG19_1c2,
              VGG19.VGG19_1c3,
              VGG19.VGG19_1c4,
              VGG19.VGG19_1d1,
              VGG19.VGG19_1d2,
              VGG19.VGG19_1d3,
              VGG19.VGG19_1d4,
//              VGG19.VGG19_1e1,
//              VGG19.VGG19_1e2,
//              VGG19.VGG19_1e3,
//              VGG19.VGG19_1e4
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-1, 1),
              new GramMatrixMatcher()
            ),
            styleUrls = styleAdjustedFiles2,
            magnification = Array(16.0),
            contentModifiers = List(
              new ContentMatcher().scale(contentWeight)
            ),
            contentLayers = List(
              VGG19.VGG19_1c1
            ).map(_.prependAvgPool(1))
          )
          val res = 960.0
          log.h1("Resolution " + res)
          val (currentCanvas: Tensor, trainable: Trainable) = {
            CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
            val content: BufferedImage = ImageArtUtil.loadImage(log, contentUrl, res.toInt)
            if (null == canvas) {
              canvas = ImageArtUtil.getImageTensor(initUrl, log, content.getWidth, content.getHeight)
            } else {
              canvas = updateCanvas(canvas, content)
            }
            val trainable: Trainable = styleContentNetwork.apply(canvas.addRef(), Tensor.fromRGB(content))
            ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], styleContentNetwork.precision)
            (canvas.addRef(), trainable)
          }
          new BasicOptimizer {
            override val trainingMinutes: Int = 90
            override val trainingIterations: Int = 100
            override val maxRate = 1e9
          }.optimize(currentCanvas, trainable)
        }

//        for (res <- new GeometricSequence {
//          override val min: Double = 400
//          override val max: Double = 800
//          override val steps = 2
//        }.toStream
//          .dropWhile(x => skipResolution >= x)
//          .map(_.round.toDouble).toArray) yield {
//          log.h1("Resolution " + res)
//          val (currentCanvas: Tensor, trainable: Trainable) = {
//            CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
//            val content: BufferedImage = ImageArtUtil.loadImage(log, contentUrl, res.toInt)
//            if (null == canvas) {
//              canvas = ImageArtUtil.getImageTensor(initUrl, log, content.getWidth, content.getHeight)
//            } else {
//              canvas = updateCanvas(canvas, content)
//            }
//            val trainable: Trainable = styleContentNetwork.apply(canvas.addRef(), Tensor.fromRGB(content))
//            ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], styleContentNetwork.precision)
//            (canvas.addRef(), trainable)
//          }
//          new BasicOptimizer {
//            override val trainingMinutes: Int = 90
//            override val trainingIterations: Int = 100
//            override val maxRate = 1e9
//          }.optimize(currentCanvas, trainable)
//        }

        // Final Phase
        val tile_padding = 64
        val tile_size = 800
        val rawContent = ImageArtUtil.getImageTensor(contentUrl, log, -1)
        for (
          content <- new GeometricSequence {
            override val min: Double = 1400
            override val max: Double = 4000
            override val steps = 3
          }.toStream
            .dropWhile(x => null == x || (null != skipResolution && skipResolution >= x.toInt))
            .map(w => {
              if(rawContent==null) null else {
                val image = rawContent.toRgbImage
                if(null==w) image else {
                  val width = w.toInt
                  ImageUtil.resize(image, width, true)
                }
              }
            })
        ) {
          if (null == canvas) {
            canvas = ImageArtUtil.getImageTensor(initUrl, log, content.getWidth, content.getHeight)
          } else {
            canvas = updateCanvas(canvas, content.getWidth, content.getHeight)
          }
          log.out(log.jpg(canvas.toRgbImage, "Canvas"))
          val selectors_fade = TiledTrainable.selectors(tile_padding, content.getWidth, content.getHeight, tile_size, true)
          val selectors_sharp = TiledTrainable.selectors(tile_padding, content.getWidth, content.getHeight, tile_size, false)
          val styleContentNetwork = new VisualStyleContentNetwork(
            styleLayers = List(
//              VGG19.VGG19_1a,
//              VGG19.VGG19_1b1,
//              VGG19.VGG19_1b2,
              VGG19.VGG19_1c1,
              VGG19.VGG19_1c2,
              VGG19.VGG19_1c3,
              VGG19.VGG19_1c4,
              VGG19.VGG19_1d1,
              VGG19.VGG19_1d2,
              VGG19.VGG19_1d3,
              VGG19.VGG19_1d4,
//              VGG19.VGG19_1e1,
//              VGG19.VGG19_1e2,
//              VGG19.VGG19_1e3,
//              VGG19.VGG19_1e4
            ).flatMap(l=>List(
              l, l.prependAvgPool(2)
            )),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-0.1, 0.1),
              new GramMatrixMatcher()
            ),
            styleUrls = styleAdjustedFiles2,
            //maxWidth = 2048,
            magnification = Array(4 * Math.pow(content.getWidth / tile_size, 2.0)),
            contentLayers = List(
              VGG19.VGG19_1c1
            ).map(_.appendMaxPool(2)),
            contentModifiers = List(
              new ContentMatcher().scale(contentWeight)
            )
          ) {
            //override def maxImageSize = 2048
          }
          val styleTensors = styleContentNetwork.loadImages(content.getWidth * content.getHeight)
          val styleNetwork: Map[String, PipelineNetwork] = styleContentNetwork.buildStyle(
            Array(content.getWidth, content.getHeight, 3),
            styleTensors,
            styleContentNetwork.styleModifier
          )


          val tiles: Array[Tensor] = for ((tileView_sharp, tileView_fade) <- selectors_sharp.zip(selectors_fade)) yield withRetry() {
            val prior: Tensor = if (tileBuffer.isEmpty) null else tileBuffer.remove(0)
            if (null != prior) {
              log.out(log.jpg(prior.toRgbImage, "Tile Product"))
              prior
            } else {
              val canvasTensor = tileView_sharp.eval(canvas.addRef()).getData.get(0)
              val trainable = {
                CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
                //canvasTensor = updateCanvas(canvasTensor, canvasDims(0), canvasDims(1))
                val trainable: Trainable = styleContentNetwork.trainable_sharedStyle(
                  canvasTensor.addRef(),
                  tileView_sharp.eval(Tensor.fromRGB(content)).getData.get(0),
                  styleContentNetwork.contentModifiers.reduce(_ combine _),
                  styleNetwork.filter(t => t._1 != null && t._2 != null).mapValues(RefUtil.addRef(_))
                )
                ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], styleContentNetwork.precision)
                trainable
              }
              new BasicOptimizer {
                override val trainingMinutes: Int = 40
                override val trainingIterations: Int = 20
                override val maxRate = 1e9
              }.optimize(canvasTensor.addRef(), trainable)
              //log.out(log.jpg(canvasTensor.toRgbImage, "Canvas Tile"))
              val maskTile = tileView_fade.eval(canvas.map(x => 1)).getData.get(0)
              val product = canvasTensor.mapCoords(c => canvasTensor.get(c) * maskTile.get(c))
              log.out(log.jpg(product.toRgbImage, "Tile Product"))
              product
            }
          }
          canvas = TiledTrainable.reassemble(content.getWidth, content.getHeight, tiles, tile_padding, tile_size, true, false)
          //canvas = TileUtil.reassemble(canvas, selectors_fade, tiles, log)
          log.out(log.jpg(canvas.toRgbImage, "Reassembled"))
        }
      }
      null
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }


  def updateCanvas(canvas: Tensor, width: Int, height: Int)(implicit log: NotebookOutput) = {
    if (canvas == null) {
      Tensor.fromRGB(ImageArtUtil.loadImage(log, initUrl, width, height))
    } else if (canvas.getDimensions()(0) != width) {
      Tensor.fromRGB(ImageUtil.resize(canvas.toRgbImage, width, height))
    } else {
      canvas
    }
  }

  def withRetry[T](n: Int = 3)(fn: => T): T = {
    try {
      fn
    } catch {
      case e: Throwable if n > 0 =>
        System.gc()
        withRetry(n - 1)(fn)
    }
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
        TileUtil.lossFunction(colorMappingLayer.addRef().asInstanceOf[Layer], () => new GramianLayer()),
        network.getInput(0),
        network.getInput(1)
      ),
      network.add(
        TileUtil.lossFunction(colorMappingLayer, () => new BandAvgReducerLayer()),
        network.getInput(0),
        network.getInput(1)
      )
    ).freeRef()
    network
  }

}
