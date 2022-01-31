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

package com.simiacryptus.mindseye.art.examples.styled

import java.net.URI
import java.util.zip.ZipFile

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.photo._
import com.simiacryptus.mindseye.art.photo.affinity.RelativeAffinity
import com.simiacryptus.mindseye.art.photo.cuda.SmoothSolver_Cuda
import com.simiacryptus.mindseye.art.photo.topology.SearchRadiusTopology
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.{ImageOptimizer, _}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.util.Util
import jcuda.jcusolver.JCusolver
import jcuda.jcusparse.JCusparse
import jcuda.runtime.JCuda

object SmoothStyle extends SmoothStyle with LocalRunner[Object] with NotebookRunner[Object]

class SmoothStyle extends ArtSetup[Object, SmoothStyle] {

  override val s3bucket: String = "www.tigglegickle.com"
  val contentUrl = "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/0d41239e-17b5-4102-8cc6-150ab19cfa56/etc/Cavour_flight_deck.jpg"
  val styleUrl = List(
//    "upload:Style"
    "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/6b99a597-eab4-4d6f-8df3-559d28edb373/etc/the-starry-night.jpg"
//    "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/2e9e980e-6318-423d-92f4-848f96d456d7/etc/shutterstock_87165334.jpg"
//    "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/d93414a4-9c83-496f-9adb-c64b1e1fcff2/etc/picasso.png",
//    "file:///C:/Users/andre/code/all-projects/report/SmoothStyle/d93414a4-9c83-496f-9adb-c64b1e1fcff2/etc/Picasso-+Las+Meninas.jpg"
  ).mkString(",")
  //  val contentUrl = "upload:Content"
  //  val styleUrl = "upload:Style"
  //  override val s3bucket: String = ""

  override def indexStr = "306"

  override def description = <div>
    Paints an image in the style of another using:
    <ol>
      <li>PhotoSmooth-based content initialization</li>
      <li>Standard VGG19 layers</li>
      <li>Operators to match content and constrain and enhance style</li>
      <li>Progressive resolution increase</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = {
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    JCuda.setExceptionsEnabled(true)
    JCusparse.setExceptionsEnabled(true)
    JCusolver.setExceptionsEnabled(true)
    implicit val implicitLog = log
    // First, basic configuration so we publish to our s3 site
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    // Fetch input images (user upload prompts) and display a rescaled copies
    log.p(log.jpg(ImageArtUtil.loadImage(log, contentUrl, 600), "Input Content"))
    ImageArtUtil.loadImages(log, styleUrl, 600).foreach(img => log.p(log.jpg(img, "Input Style")))
    val canvas = new RefAtomicReference[Tensor](null)
    // Execute the main process while registered with the site index
    val registration = registerWithIndexJPG(() => canvas.get())
    try {
      withMonitoredJpg(() => {
        val tensor = canvas.get()
        if (tensor == null) null
        else tensor.toImage
      }) {
        var initFn = (content: Tensor) => {
          val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
            "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
          val style = ImageArtUtil.loadImages(log, styleUrl, 500).map(Tensor.fromRGB(_)).head
          val wctRestyled = fastPhotoStyleTransfer.photoWCT(style, content.addRef())
          fastPhotoStyleTransfer.freeRef()
          val topology = new SearchRadiusTopology(content.addRef())
          topology.setSelfRef(true)
          topology.setVerbose(true)
          var affinity = new RelativeAffinity(content, topology)
          affinity.setContrast(50)
          affinity.setGraphPower1(2)
          affinity.setMixing(0.05)
          //val wrapper = affinity.wrap((graphEdges, innerResult) => adjust(graphEdges, innerResult, degree(innerResult), 0.2))
          val operator = solver.solve(topology, affinity, 1e-5)
          val tensor = operator.apply(wctRestyled)
          operator.freeRef()
          tensor
        }
        //initFn = (content: Tensor) => ArtUtil.load(content, "10 + noise * 0.2")
        def style(magnification: Array[Double]) = List[VisualNetwork](
          //            new VisualStyleNetwork(
          //              styleLayers = List(
          //                VGG19.VGG19_1a
          //              ) //.flatMap(x => List(x, x.prependAvgPool(2)))
          //              , styleModifiers = List(
          //                //              new MomentMatcher()
          //                new ChannelMeanMatcher().scale(1e-1)
          //              ),
          //              styleUrls = Seq(styleUrl),
          //              magnification = Array(2.0)
          //            ),
          new VisualStyleContentNetwork(
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
              VGG19.VGG19_1d4,
              //              VGG19.VGG19_1e1,
              //              VGG19.VGG19_1e2,
              //              VGG19.VGG19_1e3,
              //              VGG19.VGG19_1e4
            )
            //.flatMap(x => List(x, x.prependAvgPool(2)))
            ,
            styleModifiers = List(
              //              new MomentMatcher()
              new GramMatrixMatcher()
            ),
            styleUrls = Seq(styleUrl),
            contentLayers = List(
              //              VGG19.VGG19_1b1,
              VGG19.VGG19_1c1
              //              VGG19.VGG19_1c2,
              //              VGG19.VGG19_1c3,
              //              VGG19.VGG19_1c4
              //VGG19.VGG19_1c3
            ),
            contentModifiers = List(
              new ContentMatcher().scale(3e-1)
            ),
            magnification = magnification
          ),
          new VisualStyleNetwork(
            styleLayers = List(
              //              VGG19.VGG19_1a,
              VGG19.VGG19_1c1,
              VGG19.VGG19_1c2,
              VGG19.VGG19_1c3,
              VGG19.VGG19_1d1,
              VGG19.VGG19_1d2,
              VGG19.VGG19_1d3,
              VGG19.VGG19_1d4,
              //              VGG19.VGG19_1e1,
              //              VGG19.VGG19_1e2,
              //              VGG19.VGG19_1e3
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-1, 1).scale(5e0)
            ),
            styleUrls = Seq(styleUrl),
            magnification = magnification
          )
        ).reduce(_ + _)
        paint(
          contentUrl = contentUrl,
          initFn = initFn,
          canvas = canvas.addRef(),
          network = style(Array(64.0)),
          optimizer = new ImageOptimizer {
            override val trainingMinutes: Int = 240
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, resolutions = new GeometricSequence {
            override val min: Double = 400
            override val max: Double = 400
            override val steps = 1
          }.toStream.map(_.round.toDouble))
        paint(
          contentUrl = contentUrl,
          initFn = initFn,
          canvas = canvas.addRef(),
          network = style(Array(16.0)),
          optimizer = new ImageOptimizer {
            override val trainingMinutes: Int = 240
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, resolutions = new GeometricSequence {
            override val min: Double = 800
            override val max: Double = 1600
            override val steps = 2
          }.toStream.map(_.round.toDouble))
        //        paint(
        //          contentUrl = contentUrl,
        //          initFn = x => x,
        //          canvas = canvas.addRef(),
        //          network = new VisualStyleContentNetwork(
        //            styleLayers = List(
        //              //VGG19.VGG19_1a,
        //              //VGG19.VGG19_1b1,
        //              //VGG19.VGG19_1b2,
        //              VGG19.VGG19_1c1,
        //              VGG19.VGG19_1c2,
        //              VGG19.VGG19_1c3,
        //              VGG19.VGG19_1d1,
        //              VGG19.VGG19_1d2,
        //              VGG19.VGG19_1d3
        //            ),
        //            styleModifiers = List(
        //              //new GramMatrixEnhancer().setMinMax(-0.5, 0.5),
        //              new GramMatrixMatcher()
        //            ),
        //            styleUrls = Seq(styleUrl),
        //            contentLayers = List(
        //              VGG19.VGG19_1a,
        //              VGG19.VGG19_1b2
        //            ).map(_.prependAvgPool(2).appendMaxPool(2)),
        //            contentModifiers = List(
        //              new ContentMatcher().scale(1e0)
        //            ),
        //            magnification = Array(1.0)
        //          ),
        //          optimizer = new BasicOptimizer {
        //            override val trainingMinutes: Int = 180
        //            override val trainingIterations: Int = 10
        //            override val maxRate = 1e9
        //          },
        //          resolutions = new GeometricSequence {
        //            override val min: Double = 2000
        //            override val max: Double = 4000
        //            override val steps = 2
        //          }.toStream.map(_.round.toDouble))
      }
      null
    } finally {
      canvas.freeRef()
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }

  def solver: SmoothSolver = new SmoothSolver_Cuda()
}
