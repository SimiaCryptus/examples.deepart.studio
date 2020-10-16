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

import java.net.URI

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.ArtUtil.load
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, Permutation, _}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.java.{AffineImgViewLayer, ImgTileAssemblyLayer}
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner


object ToroidalTexture extends ToroidalTexture with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1080

  case class JobDetails(
                         aspectRatio: Double,
                         views: Array[Array[SymmetryTransform]],
                         resolutions: Map[Int,Seq[Double]] = new GeometricSequence {
                           override val min: Double = 320
                           override val max: Double = 640
                           override val steps = 2
                         }.toStream.map(x=> {
                           x.round.toInt -> Array(8.0)
                         }: (Int, Seq[Double])).toMap
                       ) extends GeometricArt {

    def layer(dimensions: Array[Int]) = views.map(views => PipelineNetwork.build(1, views.map(_.getSymmetricView(dimensions)): _*))
  }
}
import ToroidalTexture.JobDetails

class ToroidalTexture extends ArtSetup[Object] with GeometricArt {
//  val styleUrl = "upload:Style"
      val styleUrl = "file:///C:/Users/andre/code/all-projects/report/TextureTiledVector/6d463783-9944-42c4-b22b-23efad472ede/etc/shutterstock_157227299_smoothed.jpg"
  //    val styleUrl = "file:///C:/Users/andre/code/all-projects/report/TextureTiledVector/11dcc2d7-c55a-459a-8199-3e53cc997fd8/etc/shutterstock_87165334.jpg"
  //  val styleUrl = "file:///C:/Users/andre/code/all-projects/report/TextureTiledVector/894bc9d2-abc3-49ab-b7f1-1469280da4d3/etc/shutterstock_736733038.jpg,file:///C:/Users/andre/code/all-projects/report/TextureTiledVector/894bc9d2-abc3-49ab-b7f1-1469280da4d3/etc/shutterstock_1060865300.jpg"
  //  val styleUrl = "file:///C:/Users/andre/code/all-projects/report/StyleTransferPainting/71e461bf-89c4-42a4-9f22-70c635aa4af2/etc/shutterstock_240121861.jpg"

  val initUrl: String = "10 + noise * 0.2"
  //  val initUrl: String = "plasma"
  //  val s3bucket: String = "test.deepartist.org"
  val s3bucket: String = ""
  val rowsAndCols = 2
  val min_padding = 32
  val max_padding = 128
  val border_factor = 0.2

  def vectorSequence =
//    Random.shuffle(Map(
          (List(

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
//
//      "Triangle-Hex Tiling" -> JobDetails(
//        aspectRatio = 1.732,
//        views = Array(Array(
//          RotatedVector(rotation = List(2, 4).map(_ * Math.PI / 3 -> Permutation.unity(3)).toMap),
//          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3)), rotation = Math.PI)
//        ))
//      ),
//
//      "Nonsymmetric Hexagon" -> JobDetails(
//        aspectRatio = 1.732,
//        views = Array(Array(
//          TransformVector(offset = Map(Array(0.5, 0.5) -> Permutation.unity(3))),
//          TransformVector(offset = Map(Array(0.5, 1.0 / 6.0) -> Permutation.unity(3)))
//        ))
//      ),
//
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

      "1/12 Echo" -> JobDetails(
        aspectRatio = 8.0,
        views = Array(Array(
          //TransformVector(offset = Map(Array(0.5, 0) -> Permutation.unity(3))),
          TransformVector(offset = (1 until 2).map(x => Array(0, x.toDouble / 8) -> Permutation.unity(3)).toMap)
        )),
        resolutions = Map(
          90 -> Array(2400.0),
          180 -> Array(600.0),
          320 -> Array(150.0)
        ).mapValues(_.flatMap(x => Array(x * 0.9, x, x * 1.1)))
      )
    )
    )

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

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = {
    implicit val implicitLog = log
    // First, basic configuration so we publish to our s3 site
    if (Option(s3bucket).filter(!_.isEmpty).isDefined) {
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
    }
    ImageArtUtil.loadImages(log, styleUrl, 1280).foreach(img => log.p(log.jpg(img, "Input Style")))

    for ((name, details) <- vectorSequence) {
      log.h2(name)
      val canvas = new RefAtomicReference[Tensor](null)

      def canvasViews(input: Tensor) = {
        (for (viewLayer <- details.layer(input.getDimensions)) yield () => {
          if (null == input) {
            input
          } else {
            val result = viewLayer.eval(input)
            viewLayer.freeRef()
            val data = result.getData
            result.freeRef()
            val tensor = data.get(0)
            data.freeRef()
            tensor
          }
        }).toList
      }

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

      def viewLayers(dims: Seq[Int]) = {
        val rotor = details.layer(dims.toArray)
        val paddingX = Math.min(max_padding, Math.max(min_padding, dims(0) * border_factor)).toInt
        val paddingY = Math.min(max_padding, Math.max(min_padding, dims(1) * border_factor)).toInt
        val tiling = new AffineImgViewLayer(dims(0) + paddingX, dims(1) + paddingY, true)
        tiling.setOffsetX(-paddingX / 2)
        tiling.setOffsetY(-paddingY / 2)
        rotor.foreach(_.add(tiling).freeRef())
        rotor.toList
      }

      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(() => tile(canvasViews(canvas.get()).head()))
      try {
        def withMonitoredTiledJpg[T](tiledCanvas: => Tensor)(fn: => T): T = {
          withMonitoredJpg(() => {
            val tiledCanvas1 = tiledCanvas
            val toImage = tiledCanvas1.toImage
            tiledCanvas1.freeRef()
            toImage
          }) {
            // Display an additional, non-tiled image of the canvas
            withMonitoredJpg(() => {
              val tensor1 = tile(tiledCanvas)
              val image = tensor1.toRgbImage
              tensor1.freeRef()
              image
            }) {
              fn
            }
          }
        }

        val viewCount = canvasViews(new Tensor(300, 300, 3)).size

        def withMonitoredTiledJpgs[T](tiledCanvas: => List[() => Tensor])(fn: => T): T = {
          (0 until viewCount).foldLeft((_: Any) => fn)((a, b) => { (x: Any) =>
            withMonitoredTiledJpg(tiledCanvas(b)()) {
              a(x)
            }
          }).apply(null)
        }

        withMonitoredTiledJpgs(canvasViews(canvas.get())) {
          for((res,mag) <- details.resolutions) {
            paint(
              contentUrl = initUrl,
              initFn = load(_, initUrl),
              canvas = canvas.addRef(),
              network = new VisualStyleNetwork(
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
                magnification = mag.toArray,
                viewLayer = viewLayers
              ),
              optimizer = new BasicOptimizer {
                override val trainingMinutes: Int = 180
                override val trainingIterations: Int = 50
                override val maxRate = 1e8

                override def trustRegion(layer: Layer): TrustRegion = null

                override def renderingNetwork(dims: Seq[Int]) = details.layer(dims.toArray).head
              },
              aspect = Option(details.aspectRatio),
              resolutions = Array(res).map(_.toDouble))
          }
        }

        uploadAsync(log)
        canvas.get()
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }

    null
  }

}



