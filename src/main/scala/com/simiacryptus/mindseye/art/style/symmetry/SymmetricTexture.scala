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

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtUtil.load
import com.simiacryptus.mindseye.art.util.view.ImageView
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.cudnn.{CudaSettings, Precision}
import com.simiacryptus.mindseye.lang.{Layer, Result, Tensor}
import com.simiacryptus.mindseye.layers.java.{BoundedActivationLayer, ImgTileAssemblyLayer}
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.{RefArrays, RefAtomicReference}
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.util.FastRandom

import scala.concurrent.duration._

abstract class SymmetricTexture extends ArtSetup[Object] with GeometricArt {
  val styleUrl = "upload:Style"
  val initUrl: String = "10 + noise * 0.2"
  val s3bucket: String = "symmetry.deepartist.org"
  val count = 2
  val rowsAndCols = 3
  val trainingMinutes = 90
  val trainingIterations = 20

  override def inputTimeoutSeconds = 5*60

  def animationDelay = 15 seconds

  def name: String

  def aspectRatio: Double

  def optimizerViews(implicit log: NotebookOutput): Array[Array[ImageView]]

  def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = List.empty

  def resolutions: List[(Int, Seq[Double])] = new GeometricSequence {
    override val min: Double = 320
    override val max: Double = 640
    override val steps = 2
  }.toStream.map(x => {
    x.round.toInt -> Array(8.0)
  }: (Int, Seq[Double])).toList.sortBy(_._1)

  def tile(input: Tensor) = {
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

  override def postConfigure(log: NotebookOutput) = {
    if (Option(s3bucket).filter(!_.isEmpty).isDefined) {
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
    }
    val registeredImages = Seq[() => BufferedImage]().toBuffer
    val registration = registerWithIndexGIF(registeredImages.map(_.apply()), delay = animationDelay.toMillis.toInt)(log)
    withMonitoredGif(()=>registeredImages.map(_.apply()), delay = animationDelay.toMillis.toInt){
      try {
        val optimizerViews = this.optimizerViews(log).toList
        val displayViews = this.displayViews(log).toList
        val defaultViews = if(displayViews.isEmpty) optimizerViews else displayViews

        log.subreport("Symmetry View Demonstration", (sublog: NotebookOutput) => {
          val width = 600
          val height = (aspectRatio * width).toInt
          val views = sublog.eval(()=>{
            (optimizerViews ++ displayViews).map(_.map(_.getView(Array(width, height))).toList)
          })
          for(testImage <- List("plasma")) {
            val source = Tensor.fromRGB(ImageArtUtil.loadImage(sublog, testImage, width, height))
            for (image <- views.map(_.foldLeft(source.addRef())((tensor, layer) => getResult(layer.eval(tensor))))) {
              sublog.p(sublog.jpg(image.toRgbImage, ""))
            }
          }
        })

        log.subreport("Style Images", (sublog: NotebookOutput) => {
          ImageArtUtil.loadImages(sublog, styleUrl, 1280).foreach(img => sublog.p(sublog.jpg(img, "Input Style")))
        })

        val canvases = (1 to count).map(_ => new RefAtomicReference[Tensor](null)).toList

        def initCanvas(res: Int)(implicit log: NotebookOutput): Tensor = {
          val content = if (Option(this.aspectRatio).map(a => (w: Int) => (w * a).toInt).isDefined) {
            ImageArtUtil.loadImage(log, initUrl, res.toInt, Option(this.aspectRatio).map(a => (w: Int) => (w * a).toInt).get.apply(res.toInt))
          } else {
            ImageArtUtil.loadImage(log, initUrl, res.toInt)
          }
          if (null == content) {
            val tensor = new Tensor(res.toInt, Option(this.aspectRatio).map(a => (w: Int) => (w * a).toInt).map(_.apply(res.toInt)).getOrElse(res.toInt), 3)
            val map = tensor.map((x: Double) => FastRandom.INSTANCE.random())
            tensor.freeRef()
            map
          } else {
            Tensor.fromRGB(content)
          }
        }

        def withMonitoredCanvases[T]()(fn: => T)(implicit log: NotebookOutput): T = {
          canvases.foldLeft((_: Any) => fn)((fn: Any => T, canvas: RefAtomicReference[Tensor]) => { (x: Any) => {
            def canvasTensor = canvas.get()
            def dimensions = getDims(canvasTensor)

            def view(viewLayer: PipelineNetwork, dimensions: Array[Int]) =  try {
              Tensor.fromRGB(ImageUtil.resize(getImage(getResult(viewLayer.eval(canvasTensor))), dimensions(0), dimensions(1)))
            } finally {
              viewLayer.freeRef()
            }

            (0 until defaultViews.size)
              .map(i => () => view(compileView(dimensions, defaultViews(i)), dimensions))
              .foldLeft((_: Any) => fn(x))((fn: Any => T, function: () => Tensor) => { (x: Any) => {
                withMonitoredJpg(() => getImage(function())) {
                  if (rowsAndCols > 1) {
                    withMonitoredJpg(() => getImage(tile(function()))) {
                      fn(x)
                    }
                  } else {
                    fn(x)
                  }
                }
              }
              }).apply(null)
          }
          }).apply(null)
        }

        for (canvas <- canvases) {
          canvas.set(load(initCanvas(this.resolutions.head._1.toInt)(log).addRef(), initUrl)(log))
          registeredImages ++= animate(canvas)(log)
        }
        withMonitoredCanvases() {
          for ((res, mag) <- this.resolutions) {
            log.subreport("Resolution " + res, (log: NotebookOutput) => {
              for (canvas <- canvases) {
                CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
                require(null != canvas)
                canvas.set({
                  val currentCanvas: Tensor = canvas.get()
                  // We need to adjust size here to guard against occasional off-by-one errors
                  val content = initCanvas(res.toInt)(log).toImage
                  val width = if (null == content) res.toInt else content.getWidth
                  val height = if (null == content) (res.toInt*aspectRatio).toInt else content.getHeight
                  if (width == currentCanvas.getDimensions()(0) && height == currentCanvas.getDimensions()(1)) {
                    currentCanvas
                  } else {
                    val image = currentCanvas.toRgbImage
                    currentCanvas.freeRef()
                    Tensor.fromRGB(ImageUtil.resize(image, width, height))
                  }
                })
              }

              def getTrainable(style: VisualStyleNetwork) = {
                val canvasPrototype = canvases.head.get().copy()
                val trainable: Trainable = style.apply(canvasPrototype, canvasPrototype.getDimensions.take(2))
                ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], style.precision)
                trainable
              }

              val styleLayers = this.styleLayers(res)(log)
              log.p("Generating Style Network")
              val trainable = log.eval(()=>{
                getTrainable(new VisualStyleNetwork(
                  styleLayers = styleLayers,
                  styleModifiers = List(
                    new GramMatrixEnhancer(),
                    new GramMatrixMatcher()
                    //new SingleChannelEnhancer(130, 131)
                  ),
                  styleUrls = Seq(styleUrl),
                  magnification = mag.toList,
                  filterStyleInput = false,
                  viewLayer = dims => optimizerViews.map(compileView(dims.toArray, _))
                )(log))
              })
              try {
                for ((canvas,idx) <- canvases.zipWithIndex) {
                  log.p(s"Rendering Canvas $idx")
                  trainable.setData(RefArrays.asList(Array(canvas.get())))
                  new BasicOptimizer {
                    override val trainingMinutes: Int = SymmetricTexture.this.trainingMinutes
                    override val trainingIterations: Int = SymmetricTexture.this.trainingIterations
                    override val maxRate = 1e8

                    override def trustRegion(layer: Layer): TrustRegion = null

                    override def renderingNetwork(dims: Seq[Int]) = compileView(dims.toArray, defaultViews.head)
                  }.optimize(canvas.get(), trainable.addRef().asInstanceOf[Trainable])(log)
                }
              } finally {
                trainable.freeRef()
              }
            })
          }
        }(log)
        uploadAsync(log)
      } finally {
        registration.foreach(_.close())
      }
    }(log)
    null
  }

  def animate(canvas: RefAtomicReference[Tensor])(implicit log:NotebookOutput) = {
    List(() => {
      val tensor = canvas.get()
      val renderingNetwork = compileView(tensor.getDimensions, displayViews(log).headOption.getOrElse(optimizerViews(log).head))
      val image = getImage(getResult(renderingNetwork.eval(tensor)))
      renderingNetwork.freeRef()
      image
    })
  }

  def styleLayers(res: Int)(implicit log: NotebookOutput) = {
    log.out("Style Layers:")
    log.code(() => {
      List(
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
        VGG19.VGG19_1d4
      )
    })
  }

  def getImage[T](tensor: Tensor) = {
    try {
      tensor.toRgbImage
    } finally {
      tensor.freeRef()
    }
  }

  def getDims(tensor: Tensor): Array[Int] = try {
    tensor.getDimensions
  } finally {
    tensor.freeRef()
  }

  def getResult(result: Result) = {
    val data = result.getData
    result.freeRef()
    val tensor = data.get(0)
    data.freeRef()
    tensor
  }

  def compileView(dimensions: Array[Int], views: Array[ImageView]) = {
    val boundedActivationLayer = new BoundedActivationLayer()
    boundedActivationLayer.setMinValue(0);
    boundedActivationLayer.setMaxValue(255);
    PipelineNetwork.build(1, (views.map(_.getView(dimensions)) ++ List(boundedActivationLayer)): _*)
  }

}
