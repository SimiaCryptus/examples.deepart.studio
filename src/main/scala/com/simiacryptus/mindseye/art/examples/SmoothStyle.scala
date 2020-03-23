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

import java.net.URI
import java.util.zip.ZipFile

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.photo._
import com.simiacryptus.mindseye.art.photo.affinity.RelativeAffinity
import com.simiacryptus.mindseye.art.photo.cuda.SmoothSolver_Cuda
import com.simiacryptus.mindseye.art.photo.topology.SearchRadiusTopology
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.util.Util

object SmoothStyle extends SmoothStyle with LocalRunner[Object] with NotebookRunner[Object]

class SmoothStyle extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val styleUrl = "upload:Style"
  //  val s3bucket: String = "examples.deepartist.org"
  val s3bucket: String = ""

  override def indexStr = "306"

  override def description = <div>
    Paints an image in the style of another using:
    <ol>
      <li>PhotoSmooth-based content initialization</li>
      <li>Standard VGG16 layers</li>
      <li>Operators to match content and constrain and enhance style</li>
      <li>Progressive resolution increase</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      implicit val implicitLog = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input images (user upload prompts) and display a rescaled copies
      ImageArtUtil.loadImages(log, styleUrl, 600).foreach(img => log.p(log.jpg(img, "Input Style")))
      log.p(log.jpg(ImageArtUtil.loadImage(log, contentUrl, 600), "Input Content"))
      val canvas = new RefAtomicReference[Tensor](null)
      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(() => canvas.get())
      try {
        withMonitoredJpg(() => {
          val tensor = canvas.get()
          if (tensor == null) null
          else tensor.toImage
        }) {
          paint(
            contentUrl = contentUrl,
            initFn = content => {
              val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
                "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
              val style = ImageArtUtil.loadImages(log, styleUrl, 500).map(Tensor.fromRGB(_)).head
              val wctRestyled = fastPhotoStyleTransfer.photoWCT(style, content.addRef())
              fastPhotoStyleTransfer.freeRef()
              val topology = new SearchRadiusTopology(content.addRef())
              topology.setSelfRef(true)
              topology.setVerbose(true)
              var affinity = new RelativeAffinity(content, topology)
              affinity.setContrast(20)
              affinity.setGraphPower1(2)
              affinity.setMixing(0.1)
              //val wrapper = affinity.wrap((graphEdges, innerResult) => adjust(graphEdges, innerResult, degree(innerResult), 0.2))
              val operator = solver.solve(topology, affinity, 1e-4)
              val tensor = operator.apply(wctRestyled)
              operator.freeRef()
              tensor
            },
            canvas = canvas.addRef(),
            network = new VisualStyleContentNetwork(
              styleLayers = List(
                VGG16.VGG16_1a,
                VGG16.VGG16_1b1,
                VGG16.VGG16_1b2,
                VGG16.VGG16_1c1,
                VGG16.VGG16_1c2,
                VGG16.VGG16_1c3,
                VGG16.VGG16_1d1,
                VGG16.VGG16_1d2,
                VGG16.VGG16_1d3,
                VGG16.VGG16_1e1,
                VGG16.VGG16_1e2,
                VGG16.VGG16_1e3
              ).flatMap(x => List(
                x, x.prependAvgPool(2)
              )),
              styleModifiers = List(
                //new GramMatrixEnhancer().setMinMax(-0.5, 0.5),
                new MomentMatcher()
              ),
              styleUrls = Option(styleUrl),
              contentLayers = List(
                VGG16.VGG16_1c1,
                VGG16.VGG16_1c3
              ),
              contentModifiers = List(
                new ContentMatcher().scale(1e1)
              ),
              magnification = 1
            ),
            optimizer = new BasicOptimizer {
              override val trainingMinutes: Int = 90
              override val trainingIterations: Int = 20
              override val maxRate = 1e9
            }, resolutions = new GeometricSequence {
              override val min: Double = 800
              override val max: Double = 1400
              override val steps = 2
            }.toStream.map(_.round.toDouble))
          paint(
            contentUrl = contentUrl,
            initFn = x => x,
            canvas = canvas.addRef(),
            network = new VisualStyleContentNetwork(
              styleLayers = List(
                VGG16.VGG16_1a,
                VGG16.VGG16_1b1,
                VGG16.VGG16_1b2,
                VGG16.VGG16_1c1,
                VGG16.VGG16_1c2,
                VGG16.VGG16_1c3
              ),
              styleModifiers = List(
                //new GramMatrixEnhancer().setMinMax(-0.5, 0.5),
                new GramMatrixMatcher()
              ),
              styleUrls = Option(styleUrl),
              contentLayers = List(
                VGG16.VGG16_1b2,
                VGG16.VGG16_1b2
              ).map(_.prependAvgPool(2).appendMaxPool(2)),
              contentModifiers = List(
                new ContentMatcher().scale(5e-1)
              ),
              magnification = 1
            ),
            optimizer = new BasicOptimizer {
              override val trainingMinutes: Int = 180
              override val trainingIterations: Int = 10
              override val maxRate = 1e9
            },
            resolutions = new GeometricSequence {
              override val min: Double = 2000
              override val max: Double = 3000
              override val steps = 2
            }.toStream.map(_.round.toDouble))
        }
        null
      } finally {
        canvas.freeRef()
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()

  def solver: SmoothSolver = new SmoothSolver_Cuda()
}
