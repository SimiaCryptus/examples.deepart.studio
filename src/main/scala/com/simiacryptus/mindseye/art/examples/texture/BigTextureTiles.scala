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

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.TiledTrainable
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.ArtUtil.load
import com.simiacryptus.mindseye.art.util.ImageArtUtil._
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.{Layer, Result, Tensor}
import com.simiacryptus.mindseye.layers.java.AffineImgViewLayer
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.lang.RefUtil
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.mutable


object BigTextureTiles extends BigTextureTiles with LocalRunner[Object] with NotebookRunner[Object]

class BigTextureTiles extends ArtSetup[Object, BigTextureTiles] {

    val styleUrl = "http://test.deepartist.org/BigTexture/1d165554-f60e-41b8-ab41-4e730ed17d72/etc/58098b35-0203-40c6-b3c6-c860a882089a.jpg"
//    val styleUrl = "file:///C:/Users/andre/code/all-projects/report/BigTexture/556a080f-5ef7-4c58-bbdd-4bee36486502/etc/shutterstock_87165334.jpg"

  override def inputTimeoutSeconds = 0

  //  val initUrl: String = "file:///C:/Users/andre/code/all-projects/report/BigTexture/7d8f3695-9b29-4c83-b7fd-83ebafd4bb8b/etc/image_4648be07568b7c0f.jpg"
    val initUrl: String = "file:///C:/Users/andre/code/all-projects/report/BigTexture/faef1a35-a7ee-49a3-9f47-31380da7b5cc/etc/image_2ae03bd5518bf032.jpg"

//      val styleUrl = "upload:Style"
//  val initUrl: String = "plasma"
//  val initUrl: String = "50 + noise * 0.5"
  //  override def inputTimeoutSeconds = 3600
  val s3bucket: String = "test.deepartist.org"
  val aspectRatio = 0.5774
  val tile_size = 400
  val tile_padding = 64

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
    log.subreport("Styles", (sub: NotebookOutput) => {
      loadImages(sub, styleUrl, -1).foreach(img => sub.p(sub.jpg(img, "Input Style")))
    })
    val canvas = new RefAtomicReference[Tensor](null)

    // Tiling layer used by the optimization engine.
    // Expands the canvas by a small amount, using tile wrap to draw in the expanded boundary.
    val min_padding = 0
    val max_padding = 128
    val border_factor = 0.0

    def viewLayer(dims: Seq[Int]) = {
//      val paddingX = Math.min(max_padding, Math.max(min_padding, dims(0) * border_factor)).toInt
//      val paddingY = Math.min(max_padding, Math.max(min_padding, dims(1) * border_factor)).toInt
//      val layer = new AffineImgViewLayer(dims(0) + paddingX, dims(1) + paddingY, true)
//      layer.setOffsetX(-paddingX / 2)
//      layer.setOffsetY(-paddingY / 2)
//      List(layer)
      List(new PipelineNetwork(1))
    }

    // Execute the main process while registered with the site index
    val registration = registerWithIndexJPG(() => canvas.get())

    try {
      withMonitoredJpg(() => Option(canvas.get()).map(tensor => {
        val image = tensor.toRgbImage
        tensor.freeRef()
        image
      }).orNull) {
//        paint(
//          contentUrl = initUrl,
//          initUrl = initUrl,
//          canvas = canvas.addRef(),
//          network = new VisualStyleNetwork(
//            styleLayers = List(
//              // We select all the lower-level layers to achieve a good balance between speed and accuracy.
//              VGG19.VGG19_0b,
//              VGG19.VGG19_1a,
//              VGG19.VGG19_1b1,
//              VGG19.VGG19_1b2,
//              VGG19.VGG19_1c1,
//              VGG19.VGG19_1c2,
//              VGG19.VGG19_1c3,
//              VGG19.VGG19_1c4,
//              VGG19.VGG19_1d1,
//              VGG19.VGG19_1d2,
//              VGG19.VGG19_1d3,
//              VGG19.VGG19_1d4
//            ),
//            styleModifiers = List(
//              // These two operators are a good combination for a vivid yet accurate style
//              //new GramMatrixEnhancer().setMinMax(-.05, .05).scale(1e1),
//              new MomentMatcher()
////              new GramMatrixMatcher()
//            ),
//            styleUrls = Seq(styleUrl),
//            magnification = Array(1.0),
//            viewLayer = viewLayer
//          ),
//          optimizer = new BasicOptimizer {
//            override val trainingMinutes: Int = 60
//            override val trainingIterations: Int = 50
//            override val maxRate = 1e9
//          },
//          aspect = Option(aspectRatio),
//          resolutions = new GeometricSequence {
//            override val min: Double = 200
//            override val max: Double = 1600
//            override val steps = 1
//          }.toStream.map(_.round.toDouble)
//        )

        new GeometricSequence {
          override val min: Double = 1600
          override val max: Double = 6000
          override val steps = 3
        }.toStream.map(_.round.toInt).foreach(res=>{

          if(isEmpty(canvas)) {
            canvas.set(getImageTensor(initUrl, log, res))
          }
          val priorTiles = Array.empty[Tensor].toBuffer
          val content = Tensor.fromRGB(ImageUtil.resize(canvas.get().toRgbImage, res, true))

          val contentSize = content.getDimensions()
          val style = new VisualStyleNetwork(
            styleLayers = List(
              //VGG19.VGG19_0b,
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
            ),
            styleModifiers = List(
              //              new GramMatrixEnhancer().setMinMax(-.05, .05).scale(5e1),
              //              new MomentMatcher()
              new GramMatrixMatcher(),
              new ChannelMeanMatcher().scale(1e1)
            ),
            styleUrls = Seq(styleUrl),
            magnification = Array(contentSize(0).toDouble / tile_size),
            viewLayer = viewLayer
          )

          val prepCache = new mutable.HashMap[List[Int], (Tensor, Trainable)]()
          def getPrep(dims:List[Int]) = {
            val (tile, styleTrainable) = prepCache.getOrElseUpdate(dims, {
              val tile = new Tensor(dims:_*)
              val styleTrainable: Trainable = stylePrepFn(
                contentUrl = initUrl,
                network = style,
                canvas = tile.addRef(),
                width = tile_size
              )
              (tile, styleTrainable)
            })
            (tile.addRef(), styleTrainable.addRef())
          }

          def fade(tile: Tensor, tileView_fade: Layer) = {
            val maskTile = Result.getData0(tileView_fade.eval(content.map(x => 1)))
            require(maskTile.getDimensions.toList == tile.getDimensions.toList, s"${maskTile.getDimensions.toList} != ${tile.getDimensions.toList}")
            tile.mapCoords(c => tile.get(c) * maskTile.get(c))
          }

          val selectors_fade = TiledTrainable.selectors(tile_padding, contentSize(0), contentSize(1), tile_size, true)
          val selectors_sharp = TiledTrainable.selectors(tile_padding, contentSize(0), contentSize(1), tile_size, false)
          val tileRefs = for (tileView_sharp <- selectors_sharp) yield {
            new RefAtomicReference(Result.getData0(tileView_sharp.eval(content.addRef())))
          }
          val tileProcessedFlags = for (_ <- tileRefs) yield {
            new AtomicBoolean(false)
          }
          def tiles = for(((tile, tileProcessed), tileView_fade) <- tileRefs.zip(tileProcessedFlags).zip(selectors_fade)) yield {
            if(tileProcessed.get()) {
              tile.get()
            } else {
              fade(tile.get(), tileView_fade)
            }
          }
          def reassembled = TiledTrainable.reassemble(contentSize(0), contentSize(1), tiles, tile_padding, tile_size, true, false)

          log.eval("Tile Sizes", ()=>{
            tileRefs.map(_.get().getDimensions.toList).groupBy(x=>x).mapValues(_.size)
          })

          withMonitoredJpg(() => {
            val tensor = reassembled
            val image = tensor.toRgbImage
            tensor.freeRef()
            image
          }) {
            for (((tileView_fade, tileRef), tileProcessed) <- selectors_fade.zip(tileRefs).zip(tileProcessedFlags)) yield {
              if(priorTiles.isEmpty) {
                val tensor = tileRef.get()
                val (tile, styleTrainable) = getPrep(tensor.getDimensions.toList)
                tile.set(tensor)
                tileRef.set(tile.addRef())
                new BasicOptimizer {
                  override val trainingMinutes: Int = 360
                  override val trainingIterations: Int = 15
                  override val maxRate = 1e9
                }.optimize(tile.addRef(), styleTrainable)
                tileRef.set(fade(tile, tileView_fade))
                tileProcessed.set(true)
              } else {
                val prior: Tensor = fade(priorTiles.remove(0), tileView_fade)
                log.out(log.jpg(prior.toRgbImage, "Tile Product"))
                tileRef.set(prior)
                tileProcessed.set(true)
              }
            }
          }
          canvas.set(reassembled)
        })

      }
      null
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
      canvas.freeRef()
    }
  }

  def isEmpty(canvas : RefAtomicReference[Tensor]) = {
    val tensor = canvas.get()
    if(tensor == null) {
      true
    } else {
      tensor.freeRef()
      false
    }
  }

}
