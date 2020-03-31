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

import java.awt.image.BufferedImage
import java.net.URI
import java.util

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.cudnn.ProductLayer
import com.simiacryptus.mindseye.opt.line.LineSearchStrategy
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.collection.mutable.ArrayBuffer

object ZoomingRotor extends ZoomingRotorTest with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

class ZoomingRotor extends ZoomingRotorBase {
  override val rotationalSegments = 5
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styleUrl: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/b3b548e5-15b3-4668-8ff8-a0a5052be912.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/38e6440a-9f99-4a4e-936e-337f1f93aba5.jpg"
  )
  override val imageUrls = Array("http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/image_79e6507dcbc0e5a6.jpg")
  override val s3bucket: String = "examples.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val magnification: Int = 2
  override val stepZoom: Double = 0.5
  override val border: Double = 0.0
  override val enhancementCoeff: Int = 0
  override val innerCoeff: Int = 0

  override def getOptimizer()(implicit log:NotebookOutput): BasicOptimizer = {
    log.eval(()=>{
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def lineSearchFactory: LineSearchStrategy = {
          super.lineSearchFactory
        }

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
      }
    })
  }

  override def getStyle(innerMask:Tensor)(implicit log:NotebookOutput) : VisualNetwork = {
    log.eval(()=>{
      val outerMask = innerMask.map(x => 1 - x)
      var style: VisualNetwork = new VisualStyleNetwork(
        styleLayers = List(
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
          new MomentMatcher().scale(Math.pow(2, -enhancementCoeff * 2)),
          new GramMatrixEnhancer().setMinMax(-5, 5).scale(Math.pow(2, enhancementCoeff * 2))
        ),
        styleUrls = styleUrl,
        magnification = magnification,
        viewLayer = dims => getKaleidoscopeMask(dims.toArray, outerMask.addRef())
      )
      if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
        contentLayers = List(
          VGG19.VGG19_0a
        ), contentModifiers = List(
          new ContentMatcher().withMask(innerMask.addRef()).scale(innerCoeff)
        ))
      style
    })
  }

}

class ZoomingRotor2 extends ZoomingRotorBase {
  override val rotationalSegments = 5
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styleUrl: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/b3b548e5-15b3-4668-8ff8-a0a5052be912.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/38e6440a-9f99-4a4e-936e-337f1f93aba5.jpg"
  )
  override val imageUrls = Array("http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/image_79e6507dcbc0e5a6.jpg")
  override val s3bucket: String = "examples.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val magnification: Int = 2
  override val stepZoom: Double = 0.5
  override val border: Double = 0.125
  override val enhancementCoeff: Int = 0
  override val innerCoeff: Int = 0

  override def getOptimizer()(implicit log:NotebookOutput): BasicOptimizer = {
    log.eval(()=>{
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def lineSearchFactory: LineSearchStrategy = {
          super.lineSearchFactory
        }

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
      }
    })
  }

  override def getStyle(innerMask:Tensor)(implicit log:NotebookOutput) : VisualNetwork = {
    log.eval(()=>{
      val outerMask = innerMask.map(x => 1 - x)
      var style: VisualNetwork = new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1b2,
          VGG19.VGG19_1c1,
          VGG19.VGG19_1c2,
          VGG19.VGG19_1c3,
          VGG19.VGG19_1c4
        ),
        styleModifiers = List(
          new MomentMatcher().scale(Math.pow(2, -enhancementCoeff * 2))
        ),
        styleUrls = styleUrl,
        magnification = magnification,
        viewLayer = dims => getKaleidoscopeMask(dims.toArray, outerMask.addRef())
      ) + new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1d1,
          VGG19.VGG19_1d2,
          VGG19.VGG19_1d3,
          VGG19.VGG19_1d4
        ),
        styleModifiers = List(
          new GramMatrixEnhancer().setMinMax(-5, 5).scale(Math.pow(2, enhancementCoeff * 2))
        ),
        styleUrls = styleUrl,
        magnification = magnification,
        viewLayer = dims => getKaleidoscopeMask(dims.toArray, outerMask.addRef())
      )
      if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
        contentLayers = List(
          VGG19.VGG19_0a
        ), contentModifiers = List(
          new ContentMatcher().withMask(innerMask.addRef()).scale(innerCoeff)
        ))
      style
    })
  }

}

class ZoomingRotorTest extends ZoomingRotorBase {
  override val rotationalSegments = 5
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styleUrl: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/b3b548e5-15b3-4668-8ff8-a0a5052be912.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/38e6440a-9f99-4a4e-936e-337f1f93aba5.jpg"
  )
  override val imageUrls = Array("http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/image_79e6507dcbc0e5a6.jpg")
  override val s3bucket: String = null
  override val resolution = 320
  override val totalZoom = 0.25
  override val magnification = 8
  override val stepZoom = 0.5
  override val border: Double = 0.125
  override val enhancementCoeff: Int = 0
  override val innerCoeff: Int = 0

  override def getOptimizer()(implicit log:NotebookOutput): BasicOptimizer = {
    log.eval(()=>{
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def lineSearchFactory: LineSearchStrategy = {
          //new ArmijoWolfeSearch().setMaxAlpha(maxRate).setMinAlpha(1e-4).setAlpha(1e3).setC1(1e-8).setC2(1-1e-2).setRelativeTolerance(1e-4)
          //new BisectionSearch().setMaxRate(maxRate).setCurrentRate(1e3).setSpanTol(1e-2).setZeroTol(1e-5)
          //new QuadraticSearch().setMaxRate(maxRate).setCurrentRate(1e5).setAbsoluteTolerance(1e-7).setRelativeTolerance(1e-1)
          super.lineSearchFactory
        }

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
      }
    })
  }

  override def getStyle(innerMask:Tensor)(implicit log:NotebookOutput) : VisualNetwork = {
    log.eval(()=>{
      val outerMask = innerMask.map(x => 1 - x)
      var style: VisualNetwork = new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1b2,
          VGG19.VGG19_1c1,
          VGG19.VGG19_1c2,
          VGG19.VGG19_1c3,
          VGG19.VGG19_1c4
        ),
        styleModifiers = List(
          new MomentMatcher().scale(Math.pow(2, -enhancementCoeff * 2))
        )
        //.map(_.withMask(outerMask.addRef()))
        ,
        styleUrls = styleUrl,
        magnification = magnification,
        //viewLayer = dims => getKaleidoscope(dims.toArray)
        viewLayer = dims => getKaleidoscopeMask(dims.toArray, outerMask.addRef())
      ) + new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1d1,
          VGG19.VGG19_1d2,
          VGG19.VGG19_1d3,
          VGG19.VGG19_1d4
        ),
        styleModifiers = List(
          new GramMatrixEnhancer().setMinMax(-5, 5).scale(Math.pow(2, enhancementCoeff * 2))
        )
        //.map(_.withMask(outerMask.addRef()))
        ,
        styleUrls = styleUrl,
        magnification = magnification,
        //viewLayer = dims => getKaleidoscope(dims.toArray)
        viewLayer = dims => getKaleidoscopeMask(dims.toArray, outerMask.addRef())
      )
      if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
        contentLayers = List(
          VGG19.VGG19_0a
        ), contentModifiers = List(
          new ContentMatcher().withMask(innerMask.addRef()).scale(innerCoeff)
        ))
      style
    })
  }

}

abstract class ZoomingRotorBase extends RotorArt {
  def rotationalSegments: Int
  def rotationalChannelPermutation: Array[Int]
  def styleUrl: Array[String]
  def imageUrls: Array[String]
  def s3bucket: String
  def resolution: Int
  def totalZoom: Double
  def magnification: Int
  def stepZoom: Double
  def border: Double
  def enhancementCoeff: Int
  def innerCoeff: Int

  override def className: String = "ZoomingRotor"
  override def indexStr = "202"

  override def description = <div>
    Creates a kaliedoscopic animation with:
    <ol>
      <li>Static images defining the start and end state</li>
      <li>Standard VGG19 layers</li>
      <li>Operators constraining and enhancing style</li>
      <li>Progressive scaling/zooming at constant resolution</li>
      <li>Kaleidoscopic view layer</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = {
    implicit val implicitLog = log
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    log.subreport[Null]("Styles", (sub:NotebookOutput)=>{
      ImageArtUtil.loadImages(sub, styleUrl.toList.asJava, (resolution * Math.sqrt(magnification)).toInt)
        .foreach(img => sub.p(sub.jpg(img, "Input Style")))
      null
    })
    val result = renderTransitionLinearSeq(imageUrls ++ List(imageUrls.head))
    log.json(result)
    result
  }

  def renderTransitionLinearSeq(imageUrls: Seq[String])(implicit log: NotebookOutput): Seq[String] = {
    if(imageUrls.size > 2) {
      val tail = imageUrls.tail
      renderTransitionLinearSeq(imageUrls.take(2)) ++ renderTransitionLinearSeq(tail.drop(1))
    } else {
      val outerImage: BufferedImage = ImageArtUtil.loadImage(log, imageUrls(0), resolution)
      val innerImage: BufferedImage = ImageArtUtil.loadImage(log, imageUrls(1), resolution)
      log.p(log.jpg(outerImage, "Outer Image"))
      log.p(log.jpg(innerImage, "Inner Image"))
      log.subreport("Single Transition",(sub:NotebookOutput)=>{
        renderTransitionAB(outerImage, innerImage)(sub)
      })
    }
  }

  def toFile(tensor: Tensor)(implicit log : NotebookOutput) = {
    "file:///" + log.jpgFile(toRGB(tensor)).getAbsolutePath
  }

  def renderTransitionAB(outerImage: BufferedImage, innerImage: BufferedImage)(implicit log : NotebookOutput): Seq[String] = {
    val zoomFactor = 1.0 / (1.0 + stepZoom)
    val repeat: Int = log.eval(() => {
      (Math.log(totalZoom) / Math.log(zoomFactor)).ceil.toInt
    })
    var finalZoom = Math.pow(zoomFactor, -(repeat + 1))
    var zoomedInnerUrl: String = null
    var innerMask: Tensor = null
    var initUrl: String = null

    def zoomInner() = {
      finalZoom *= zoomFactor
      val width = innerImage.getWidth()
      val height = innerImage.getHeight()
      val zoomedImage = zoom(Tensor.fromRGB(innerImage), finalZoom)
      zoomedInnerUrl = toFile(zoomedImage.addRef())
      Option(innerMask).foreach(_.freeRef())
      innerMask = zoomMask(Array(width, height, 3), finalZoom, (width * border).toInt)
      innerMask.watch()
      zoomedImage
    }

    def zoomOuter(previousOuterImage: BufferedImage, zoomedInner: Tensor) = {
      val zoomedOuter = zoom(Tensor.fromRGB(previousOuterImage), zoomFactor)
      val tensor = zoomedInner.mapCoords(c => {
        val mask = innerMask.get(c)
        zoomedInner.get(c) * mask + (1 - mask) * zoomedOuter.get(c)
      })
      initUrl = toFile(tensor.addRef())
      zoomedInner.freeRef()
      zoomedOuter.freeRef()
      tensor
    }

    val flipbook = new ArrayBuffer[Tensor]()
    flipbook += Tensor.fromRGB(outerImage)
    var zoomedInner = zoomInner()
    var zoomedOuterTensor = zoomOuter(outerImage, zoomedInner.addRef())
    def animation: Seq[BufferedImage] = (flipbook.map(_.addRef()) ++ Option(rotatedCanvas(zoomedOuterTensor)).toList).map(toRGB(_))

    val indexRegistration = registerWithIndexGIF(animation, 1000)
    try {
      withMonitoredGif(() => animation, 1000) {
        try {
          (1 to repeat).map(_ => {
            val thisOuterTensor = zoomedOuterTensor
            withMonitoredJpg(() => toRGB(rotatedCanvas(thisOuterTensor))) {
              log.subreport("Painting", (sub: NotebookOutput) => {
                displayInner(zoomedInner.addRef(), innerMask)(sub)
                paint_aspectFn(
                  contentUrl = zoomedInnerUrl,
                  initFn = _ => thisOuterTensor.addRef(),
                  canvas = new RefAtomicReference[Tensor](null),
                  network = getStyle(innerMask)(sub),
                  optimizer = getOptimizer()(sub),
                  resolutions = List(resolution.toDouble),
                  heightFn = Option(_ => innerImage.getHeight)
                )(sub)
                null
              })
              uploadAsync(log)
            }(log)
            val finalCanvas = rotatedCanvas(thisOuterTensor)
            val url = "file:///" + log.jpgFile(finalCanvas.toRgbImage).getAbsolutePath
            zoomedInner.freeRef()
            zoomedInner = zoomInner()
            zoomedOuterTensor = zoomOuter(finalCanvas.toRgbImage, zoomedInner.addRef())
            flipbook += finalCanvas
            url
          })
        } finally {
          zoomedOuterTensor = null
        }
      }
    } finally {
      indexRegistration.foreach(_.close())
    }
  }

  def displayInner(zoomedInner: Tensor, innerMask: Tensor)(implicit log:NotebookOutput) = {
    log.p(log.jpg(toRGB(zoomedInner.mapCoords(c => zoomedInner.get(c) * innerMask.get(c))), "Zoomed Inner"));
    zoomedInner.freeRef()
  }

  def getOptimizer()(implicit log:NotebookOutput): BasicOptimizer

  def rotatedCanvas(input:Tensor) = {
    if (input == null) {
      null
    } else {
      val viewLayer = getKaleidoscope(input.getDimensions)
      val result = viewLayer.eval(input.addRef())
      viewLayer.freeRef()
      val data = result.getData
      result.freeRef()
      val tensor = data.get(0)
      data.freeRef()
      tensor
    }
  }

  def getKaleidoscopeMask(canvasDims: Array[Int], mask:Tensor) = {
    val network = getKaleidoscope(canvasDims)
    network.add(new ProductLayer(), network.getHead, network.constValue(mask)).freeRef()
    network
  }

  def getStyle(innerMask:Tensor)(implicit log:NotebookOutput) : VisualNetwork

  def toRGB(tensor: Tensor) = {
    val image = tensor.toRgbImage
    tensor.freeRef()
    image
  }

  def zoomMask(dims: Array[Int], factor: Double, border: Int): Tensor = {
    val tensor = new Tensor(dims: _*)
    val zoomedTensor = tensor.mapCoords(coord => {
      val coords = coord.getCoords()
      val x = coords(0)
      val y = coords(1)
      val distFromBorder = List(x, dims(0) - x, y, dims(1) - y).reduce(Math.min(_, _))
      Math.min(1, distFromBorder.toDouble / border)
    })
    tensor.freeRef()
    zoom(zoomedTensor, factor)
  }

  def zoom(tensor: Tensor, factor: Double): Tensor = {
    val width = tensor.getDimensions()(0)
    val height = tensor.getDimensions()(1)
    val zoomedTensor = tensor.mapCoords(coord => {
      val coords = coord.getCoords()
      val x = ((coords(0) - (width / 2)) * factor).toInt + (width / 2)
      val y = ((coords(1) - (height / 2)) * factor).toInt + (height / 2)
      if (x < 0 || x >= width || y < 0 || y >= height) {
        0
      } else {
        tensor.get(x, y, coords(2))
      }
    })
    tensor.freeRef()
    zoomedTensor
  }
}
