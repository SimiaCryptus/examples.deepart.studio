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
import java.io.File
import java.net.URI

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
import com.simiacryptus.util.Util
import javax.imageio.ImageIO

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object ZoomingRotor extends MeatRotor with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

trait WaterArt extends ArtSource {
  override val border: Double = 0.0
  override val magnification: Int = 4
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/f5fd9f51-02a1-4da1-ba5a-6968b9fd9521/etc/background-biology-blue-1426718.jpg"
  )
  override val keyframes = Array(
    "http://examples.deepartist.org/TextureTiledRotor/f5fd9f51-02a1-4da1-ba5a-6968b9fd9521/etc/image_d49187c054b3a94b.jpg"
  )
}

trait CosmicArt extends ArtSource {
  override val border: Double = 0.15
  override val magnification: Int = 4
  override val rotationalSegments = 1
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    "upload:Style"
  )
  override val keyframes = Array(
    "http://simiacryptus.s3.us-west-2.amazonaws.com/photos/cosmic_zoom/galaxy.jpg",
    "http://simiacryptus.s3.us-west-2.amazonaws.com/photos/cosmic_zoom/earth.jpg",
    "http://simiacryptus.s3.us-west-2.amazonaws.com/photos/cosmic_zoom/tree.jpg",
    "http://simiacryptus.s3.us-west-2.amazonaws.com/photos/cosmic_zoom/bug.jpg",
    "http://simiacryptus.s3.us-west-2.amazonaws.com/photos/cosmic_zoom/germs.jpg"
  )
}

trait GrafitiArt extends ArtSource {
  override val border: Double = 0.0
  override val magnification: Int = 2
  override val rotationalSegments = 5
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/b3b548e5-15b3-4668-8ff8-a0a5052be912.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/38e6440a-9f99-4a4e-936e-337f1f93aba5.jpg"
  )
  override val keyframes = Array(
    "http://examples.deepartist.org/TextureTiledRotor/81ff602d-6492-4872-adfc-e339e67781e1/etc/image_79e6507dcbc0e5a6.jpg"
  )
}

trait MeatRotor extends ZoomingRotorBase {
  override val border: Double = 0.0
  override val magnification: Int = 2
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    ""
  )
  override val keyframes = Array(
    "file:///H:/SimiaCryptus/all-projects/report/TextureTiledRotor/14f5738c-edd4-4d05-a5e9-593f2de9d1f6/etc/image_2d4bfbb17405ae22.jpg",
    "file:///H:/SimiaCryptus/all-projects/report/TextureTiledRotor/14f5738c-edd4-4d05-a5e9-593f2de9d1f6/etc/image_62a54cffead14b31.jpg",
    "file:///H:/SimiaCryptus/all-projects/report/TextureTiledRotor/14f5738c-edd4-4d05-a5e9-593f2de9d1f6/etc/image_b793d3f8c7dc1bc.jpg"
  )

  override val s3bucket: String = "examples.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val stepZoom: Double = 0.5
  override val innerCoeff: Int = 0

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
      }
    })
  }

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    log.eval(() => {
      val outerMask = innerMask.map(x => 1 - x)
      var style: VisualNetwork = new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1c4
        ),
        styleModifiers = List(
          new SingleChannelEnhancer(15,16)
        ).map(_.withMask(outerMask.addRef())),
        styleUrls = styles,
        magnification = magnification,
        viewLayer = dims => getKaleidoscope(dims.toArray)
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

trait FlowersArt extends ArtSource {
  override val border: Double = 0.0
  override val magnification: Int = 2
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/26db2c3d-6f29-4895-8053-684646ee92b1.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/722a3c1e-0be9-4e2b-93c0-e74acbb1fc6e.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/54f22692-5df5-4f35-aa2a-d0a62956dc28.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/2d1d77df-c88b-4445-b737-4bd646392cee.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/7baf8cf5-60b6-4c43-b69b-0c70d93fe354.jpg"
  )
  override val keyframes = Random.shuffle(List(
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_f038534c5e7bbc9c.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_f2dc0ed871423a83.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_9fe6db04f7917fd0.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_ac03cb567c4536dd.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_e1a59272b4266e4e.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_8be7ecec6831f03f.jpg",
    "http://examples.deepartist.org/TextureTiledRotor/427d8b22-5cbd-478b-ad84-055d8c146216/etc/image_20936532dd6565f0.jpg"
  )).take(3).toArray
}

class ZoomingRotor extends ZoomingRotorBase with CosmicArt {
  override val s3bucket: String = "examples.deepartist.org"
  override val resolution: Int = 640
  override val totalZoom: Double = 0.01
  override val stepZoom: Double = 0.5
  val enhancementCoeff: Double = 0
  override val innerCoeff: Int = 0
  val splitLayers = true

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
      }
    })
  }

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    if (splitLayers) {
      log.eval(() => {
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
          ).map(_.withMask(outerMask.addRef())),
          styleUrls = styles,
          magnification = magnification,
          viewLayer = dims => getKaleidoscope(dims.toArray)
        ) + new VisualStyleNetwork(
          styleLayers = List(
            VGG19.VGG19_1d1,
            VGG19.VGG19_1d2,
            VGG19.VGG19_1d3,
            VGG19.VGG19_1d4
          ),
          styleModifiers = List(
            new GramMatrixEnhancer().setMinMax(-5, 5).scale(Math.pow(2, enhancementCoeff * 2))
          ).map(_.withMask(outerMask.addRef())),
          styleUrls = styles,
          magnification = magnification,
          viewLayer = dims => getKaleidoscope(dims.toArray)
        )
        if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
          contentLayers = List(
            VGG19.VGG19_0a
          ), contentModifiers = List(
            new ContentMatcher()
              .withMask(innerMask.addRef())
              .scale(innerCoeff)
          ))
        style
      })
    } else {
      log.eval(() => {
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
          ).map(_.withMask(outerMask.addRef())),
          styleUrls = styles,
          magnification = magnification,
          viewLayer = dims => getKaleidoscope(dims.toArray)
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

}

class ZoomingRotor_altMask extends ZoomingRotorBase with GrafitiArt {
  override val s3bucket: String = "examples.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val stepZoom: Double = 0.5
  override val border: Double = 0.0
  val enhancementCoeff: Double = 0
  override val innerCoeff: Int = 0

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
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

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    log.eval(() => {
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
        styleUrls = styles,
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

class ZoomingRotor2 extends ZoomingRotorBase with GrafitiArt {
  override val s3bucket: String = "examples.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val stepZoom: Double = 0.5
  override val border: Double = 0.125
  val enhancementCoeff: Double = 0
  override val innerCoeff: Int = 0

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
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

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    log.eval(() => {
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
        styleUrls = styles,
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
        styleUrls = styles,
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

class ZoomingRotorTest extends ZoomingRotorBase with CosmicArt {
  override val s3bucket: String = null
  override val resolution = 320
  override val totalZoom = 0.25
  override val stepZoom = 1.0
  override val border: Double = 0.125
  val enhancementCoeff: Double = 0
  override val innerCoeff: Int = 0

  override def inputTimeoutSeconds: Int = 0

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray)
      }
    })
  }

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    log.eval(() => {
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
        ).map(_.withMask(outerMask.addRef())),
        styleUrls = styles,
        magnification = magnification,
        viewLayer = dims => getKaleidoscope(dims.toArray)
      ) + new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1d1,
          VGG19.VGG19_1d2,
          VGG19.VGG19_1d3,
          VGG19.VGG19_1d4
        ),
        styleModifiers = List(
          new GramMatrixEnhancer().setMinMax(-5, 5).scale(Math.pow(2, enhancementCoeff * 2))
        ).map(_.withMask(outerMask.addRef())),
        styleUrls = styles,
        magnification = magnification,
        viewLayer = dims => getKaleidoscope(dims.toArray)
      )
      if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
        contentLayers = List(
          VGG19.VGG19_0a
        ), contentModifiers = List(
          new ContentMatcher()
            .withMask(innerMask.addRef())
            .scale(innerCoeff)
        ))
      style
    })
  }

}

trait ArtSource extends RotorArt {

  def border: Double

  def magnification: Int

  def rotationalSegments: Int

  def rotationalChannelPermutation: Array[Int]

  def styles: Array[String]

  def keyframes: Array[String]

}

abstract class ZoomingRotorBase extends RotorArt with ArtSource {

  val repaintKeyframes = true

  def s3bucket: String

  def resolution: Int

  def totalZoom: Double

  def stepZoom: Double

  //def enhancementCoeff: Double

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
    log.subreport[Null]("Styles", (sub: NotebookOutput) => {
      ImageArtUtil.loadImages(sub, styles.toList.asJava, (resolution * Math.sqrt(magnification)).toInt)
        .foreach(img => sub.p(sub.jpg(img, "Input Style")))
      null
    })
    var keyframes: Array[String] = this.keyframes
    if (repaintKeyframes) {
      keyframes = log.subreport("KeyFrames", (sub: NotebookOutput) => {
        for (imageUrl <- keyframes) yield {
          val image = ImageArtUtil.loadImage(sub, imageUrl, resolution)
          "file:///" + sub.jpgFile(toRGB(paint(Tensor.fromRGB(image))(sub))).getAbsolutePath
        }
      })
    }

    val flipbook: ArrayBuffer[Tensor] = new ArrayBuffer[Tensor]()
    val indexRegistration = registerWithIndexGIF(flipbook.map(_.addRef()).map(toRGB(_)), 1000)
    try {
      val result = withMonitoredGif(() => flipbook.map(_.addRef()).map(toRGB(_)), 1000) {
        renderTransitionLinearSeq(flipbook, keyframes ++ List(keyframes.head)).map(file => {
          Util.pathTo(log.getRoot, file).toString
        }).toArray
      }
      log.subreport("Animation", (sub: NotebookOutput) => {
        animate(result)(sub)
        null
      })
      log.json(result)
      result
    } finally {
      indexRegistration.foreach(_.close())
    }
  }

  def animate(result: Seq[String])(implicit log: NotebookOutput) = {
    val image = ImageIO.read(new File(log.getRoot, result.head))
    val width = image.getWidth()
    val height = image.getHeight()
    val canvasId = "canvas_" + java.lang.Long.toHexString((Math.random() * 10000).toLong)
    log.addHeaderHtml("<script type=\"text/javascript\" src=\"https://simiacryptus.s3.us-west-2.amazonaws.com/descent-animator-opt.js\"></script>")
    log.out(<div>
      <canvas style="display: block" id={canvasId} width={width.toString} height={height.toString}></canvas>
      <script>
        {s"""var descent = new Descent($width, $height);
            |descent.stepZoom = ${stepZoom};
            |descent.animate(document.getElementById('$canvasId'),${result.map("'" + _ + "'").reduce(_ + "," + _)});""".stripMargin}
      </script>
    </div>.toString())
  }

  def renderTransitionLinearSeq(flipbook: ArrayBuffer[Tensor], imageUrls: Seq[String])(implicit log: NotebookOutput): Seq[File] = {
    if (imageUrls.size > 2) {
      renderTransitionLinearSeq(flipbook, imageUrls.take(2)) ++ renderTransitionLinearSeq(flipbook, imageUrls.drop(1))
    } else {
      val outerImage: BufferedImage = ImageArtUtil.loadImage(log, imageUrls(0), resolution)
      val innerImage: BufferedImage = ImageArtUtil.loadImage(log, imageUrls(1), resolution)
      log.p(log.jpg(outerImage, "Outer Image"))
      log.p(log.jpg(innerImage, "Inner Image"))
      log.subreport("Single Transition", (sub: NotebookOutput) => {
        renderTransitionAB(flipbook, outerImage, innerImage)(sub)
      })
    }
  }

  def renderTransitionAB(flipbook: ArrayBuffer[Tensor], outerImage: BufferedImage, innerImage: BufferedImage)(implicit log: NotebookOutput): Seq[File] = {
    val zoomFactor = 1.0 / (1.0 + stepZoom)
    val repeat: Int = log.eval(() => {
      (Math.log(totalZoom) / Math.log(zoomFactor)).ceil.toInt
    })
    var finalZoom = Math.pow(zoomFactor, -(repeat + 1))
    var innerMask: Tensor = null
    var initUrl: String = null

    def zoomInner() = {
      finalZoom *= zoomFactor
      val width = innerImage.getWidth()
      val height = innerImage.getHeight()
      val zoomedImage = zoom(Tensor.fromRGB(innerImage), finalZoom)
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

    flipbook += Tensor.fromRGB(outerImage)
    var zoomedInner = zoomInner()
    var currentImageTensor = zoomOuter(outerImage, zoomedInner.addRef())
    withMonitoredGif(() => (flipbook.map(_.addRef()) ++ Option(currentImageTensor).map(x => rotatedCanvas(x.addRef())).toList).map(toRGB(_)), 1000) {
      try {
        List(
          log.jpgFile(outerImage)
        ) ++ (1 to repeat).map(_ => {
          log.p(log.jpg(toRGB(zoomedInner.mapCoords(c => zoomedInner.get(c) * innerMask.get(c))), "Zoomed Inner"));
          val finalCanvas = paint(currentImageTensor, innerMask)
          val url = log.jpgFile(finalCanvas.toRgbImage)
          zoomedInner.freeRef()
          zoomedInner = zoomInner()
          currentImageTensor = zoomOuter(finalCanvas.toRgbImage, zoomedInner.addRef())
          flipbook += finalCanvas
          url
        }).toList
      } finally {
        currentImageTensor = null
      }
    }
  }

  def paint(imageTensor: Tensor)(implicit log: NotebookOutput): Tensor = {
    try {
      withMonitoredJpg(() => toRGB(rotatedCanvas(imageTensor.addRef()))) {
        log.subreport("Painting", (sub: NotebookOutput) => {
          paint_aspectFn(
            contentUrl = toFile(imageTensor.addRef())(sub),
            initFn = _ => imageTensor.addRef(),
            canvas = new RefAtomicReference[Tensor](null),
            network = getStyle(new Tensor(imageTensor.getDimensions: _*))(sub),
            optimizer = getOptimizer()(sub),
            resolutions = List(resolution.toDouble),
            heightFn = Option(_ => imageTensor.getDimensions()(1))
          )(sub)
        })
      }(log)
      rotatedCanvas(imageTensor)
    } finally {
      uploadAsync(log)
    }
  }

  def paint(imageTensor: Tensor, innerMask: Tensor)(implicit log: NotebookOutput): Tensor = {
    try {
      withMonitoredJpg(() => toRGB(rotatedCanvas(imageTensor.addRef()))) {
        log.subreport("Painting", (sub: NotebookOutput) => {
          paint_aspectFn(
            contentUrl = toFile(imageTensor.addRef())(sub),
            initFn = _ => imageTensor.addRef(),
            canvas = new RefAtomicReference[Tensor](null),
            network = getStyle(innerMask)(sub),
            optimizer = getOptimizer()(sub),
            resolutions = List(resolution.toDouble),
            heightFn = Option(_ => imageTensor.getDimensions()(1))
          )(sub)
        })
      }(log)
      rotatedCanvas(imageTensor)
    } finally {
      uploadAsync(log)
    }
  }

  def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer

  def rotatedCanvas(input: Tensor) = {
    if (input == null) {
      null
    } else {
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

  def getKaleidoscopeMask(canvasDims: Array[Int], mask: Tensor) = {
    val network = getKaleidoscope(canvasDims)
    network.add(new ProductLayer(), network.getHead, network.constValue(mask)).freeRef()
    network
  }

  def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork

  def toRGB(tensor: Tensor) = {
    val image = tensor.toRgbImage
    tensor.freeRef()
    image
  }

  def toFile(tensor: Tensor)(implicit log: NotebookOutput) = {
    "file:///" + log.jpgFile(toRGB(tensor)).getAbsolutePath
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
