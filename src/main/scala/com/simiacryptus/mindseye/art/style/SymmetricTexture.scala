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
import com.simiacryptus.mindseye.layers.java.ImgTileAssemblyLayer
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
  val count = 4
  val rowsAndCols = 3
  val trainingMinutes = 90
  val trainingIterations = 30
  val animationDelay = 30 seconds

  def name: String

  def aspectRatio: Double

  def views(implicit log: NotebookOutput): Array[Array[ImageView]]
  def auxViews(implicit log: NotebookOutput): Array[Array[ImageView]] = Array.empty

  def resolutions: Map[Int, Seq[Double]] = new GeometricSequence {
    override val min: Double = 320
    override val max: Double = 640
    override val steps = 2
  }.toStream.map(x => {
    x.round.toInt -> Array(8.0)
  }: (Int, Seq[Double])).toMap

  //      "Dual Hex Tiling" -> JobDetails(
  //        aspectRatio = 1.732,
  //        views = Array(
  //          Array.empty[SymmetryTransform],
  //          Array(TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false))
  //        ).map(x => x ++ Array(RotatedVector(rotation = Map(Math.PI -> Permutation.unity(3)))))
  //      ),

  //      "Hex nonsymmetric center" -> JobDetails(
  //        aspectRatio = 1.732,
  //        views = Array(
  //          Array.empty[SymmetryTransform],
  //          Array(
  //            TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false)
  //          )
  //        ).map(Array[SymmetryTransform](
  //          RotatedVector(rotation = (1 until 6).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap, mask = ViewMask(radius_min = .15, radius_max = .3))
  //        ) ++ _),
  //        resolutions = Array(320, 640)
  //      ),

  //      "Pure Triangle" -> JobDetails(
  //        aspectRatio = 1.732,
  //        views = Array(Array(
  //          RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap),
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)))
  //        ))
  //      ),

  //      "Triangle-Hex Tiling" -> JobDetails(
  //        aspectRatio = 1.732,
  //        views = Array(Array(
  //          RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap),
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), rotation = Math.PI)
  //        ))
  //      ),

  //      "Nonsymmetric Hexagon" -> JobDetails(
  //        aspectRatio = 1.732,
  //        views = Array(Array(
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3))),
  //          TransformVector(offset = Map(Array(0.5, 1.0 / 6.0) -> Permutation.unity(3)))
  //        ))
  //      ),

  //      "Primary Hex Tiling" -> JobDetails(
  //        aspectRatio = 1.732,
  //        views = Array(Array(
  //          RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap),
  //          RotatedVector(rotation = Map(Math.PI -> Permutation.unity(3))),
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)))
  //        ))
  //      ),

  //    "Hex rotating rings" -> JobDetails(
  //      aspectRatio = 1.732,
  //      views = Array(
  //        Array.empty[SymmetryTransform],
  //        Array(
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false)
  //        )
  //      ).map(Array[SymmetryTransform](
  //        RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap, mask = ViewMask(radius_min = 0, radius_max = .2)),
  //        RotatedVector(rotation = (1 until 6).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap, mask = ViewMask(radius_min = .2, radius_max = .35))
  //      ) ++ _),
  //      resolutions = Array(320, 640)
  //    ),

  //    "Square rotating rings" -> JobDetails(
  //      aspectRatio = 1.0,
  //      views = Array(
  //        Array.empty[SymmetryTransform],
  //        Array(
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false)
  //        )
  //      ).map(Array[SymmetryTransform](
  //        RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap, mask = ViewMask(radius_min = 0, radius_max = .25)),
  //        RotatedVector(rotation = (1 until 6).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap, mask = ViewMask(radius_min = .25, radius_max = .5))
  //      ) ++ _),
  //      resolutions = Array(320, 640)
  //    ),

  //    "Simple square tile" -> JobDetails(
  //      aspectRatio = 1.0,
  //      views = Array(
  //        Array.empty[SymmetryTransform],
  //        Array(
  //          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), symmetry = false)
  //        )
  //      ).map(Array[SymmetryTransform](
  //        RotatedVector(rotation = (1 until 4).map(_ * Math.PI / 2 -> Permutation.unity(3)).toMap, mask = ViewMask(radius_min = .5))
  //      ) ++ _),
  //      resolutions = Array(320, 640)
  //    ),


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

  def getDims(tensor: Tensor): Array[Int] = try {
    tensor.getDimensions
  } finally { tensor.freeRef() }

  override def postConfigure(log: NotebookOutput) = {
    if (Option(s3bucket).filter(!_.isEmpty).isDefined) {
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
    }
    val images = Seq[() => BufferedImage]().toBuffer
    val registration = registerWithIndexGIF(images.map(_.apply()), delay = animationDelay.toMillis.toInt)(log)
    try {
      implicit val implicitLog = log
      val views = this.views
      ImageArtUtil.loadImages(log, styleUrl, 1280).foreach(img => log.p(log.jpg(img, "Input Style")))

      def buildViews(dimensions: Array[Int]) = views.map(views => PipelineNetwork.build(1, views.map(_.getView(dimensions)): _*))
      def buildFinalViews(dimensions: Array[Int]) = auxViews.map(auxView=>PipelineNetwork.build(1, auxView.map(_.getView(dimensions)): _*))
      val canvases = (1 to count).map(_ => new RefAtomicReference[Tensor](null)).toList
      def canvasViews(input: Tensor) = {
        (for (viewLayer <- buildViews(input.getDimensions)) yield () => {
          if (null == input) {
            input.addRef()
          } else {
            getResult(viewLayer.eval(input.addRef()))
          }
        }).toList
      }


      def viewContent(res: Int): Tensor = {
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
      for (canvas <- canvases) {
        canvas.set(load(viewContent(this.resolutions.head._1.toInt).addRef(), initUrl))
        images ++= List(() => canvas.get().toRgbImage)
      }

      def withMonitoredCanvases[T]()(fn: => T): T = {
        canvases.foldLeft((_: Any) => fn)((fn: Any => T, canvas) => { (x: Any) => {
          def canvasTensor = canvas.get()

          def finalViews = buildFinalViews(getDims(canvasTensor))
          def workingViews = canvasViews(canvas.get())
          ((0 until workingViews.size).map(i => () => workingViews(i)()) ++
            (0 until finalViews.size).map(i => () => {
              val dimensions = getDims(canvasTensor)
              val viewLayer = buildFinalViews(dimensions)(i)
              val result = getResult(viewLayer.eval(canvasTensor))
              viewLayer.freeRef()
              val image = result.toRgbImage
              result.freeRef()
              Tensor.fromRGB(ImageUtil.resize(image, dimensions(0), dimensions(1)))
            })).foldLeft((_: Any) => fn(x))((fn: Any => T, function: () => Tensor) => { (x: Any) => {
            withMonitoredJpg(() => {
              val canvasTensor = function()
              try {
                canvasTensor.toImage
              } finally {
                canvasTensor.freeRef()
              }
            }) {
              if(rowsAndCols>1) {
                withMonitoredJpg(() => image(tile(function()))) {
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



      withMonitoredCanvases() {
        for ((res, mag) <- this.resolutions) {
          log.h1("Resolution " + res)
          val style = new VisualStyleNetwork(
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
              VGG19.VGG19_1d4
              //                  VGG19.VGG19_1e1,
              //                  VGG19.VGG19_1e2,
              //                  VGG19.VGG19_1e3,
              //                  VGG19.VGG19_1e4
            ),
            styleModifiers = List(
              new GramMatrixEnhancer(),
              new GramMatrixMatcher()
              //new SingleChannelEnhancer(130, 131)
            ),
            styleUrls = Seq(styleUrl),
            magnification = mag.toList,
            viewLayer = dims => buildViews(dims.toArray).toList
          )
          for (canvas <- canvases) {
            CudaSettings.INSTANCE().setDefaultPrecision(Precision.Float)
            require(null != canvas)
            canvas.set({
              val content = viewContent(res.toInt).toImage
              val currentCanvas: Tensor = canvas.get()
              val width = if (null == content) res.toInt else content.getWidth
              val height = if (null == content) res.toInt else content.getHeight
              if (width == currentCanvas.getDimensions()(0) && height == currentCanvas.getDimensions()(1)) {
                currentCanvas
              } else {
                val image = currentCanvas.toRgbImage
                currentCanvas.freeRef()
                Tensor.fromRGB(ImageUtil.resize(image, width, height))
              }
            })
          }
          val canvasPrototype = canvases.head.get().copy()
          val trainable: Trainable = style.apply(canvasPrototype, canvasPrototype.getDimensions.take(2))
          ArtUtil.resetPrecision(trainable.addRef().asInstanceOf[Trainable], style.precision)
          for (canvas <- canvases) {
            trainable.setData(RefArrays.asList(Array(canvas.get())))
            new BasicOptimizer {
              override val trainingMinutes: Int = SymmetricTexture.this.trainingMinutes
              override val trainingIterations: Int = SymmetricTexture.this.trainingIterations
              override val maxRate = 1e8

              override def trustRegion(layer: Layer): TrustRegion = null

              override def renderingNetwork(dims: Seq[Int]) = buildViews(dims.toArray).head
            }.optimize(canvas.get(), trainable.addRef().asInstanceOf[Trainable])
          }
        }
      }
      uploadAsync(log)
    } finally {
      registration.foreach(_.close())
    }

    null
  }

  private def image[T](tensor: Tensor) = {
    try {
      tensor.toImage
    } finally {
      tensor.freeRef()
    }
  }

  private def getResult(result: Result) = {
    val data = result.getData
    result.freeRef()
    val tensor = data.get(0)
    data.freeRef()
    tensor
  }
}
