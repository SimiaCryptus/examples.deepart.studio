/*
 * Copyright (c) 2019 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.photo._
import com.simiacryptus.mindseye.art.photo.affinity.RasterAffinity.{adjust, degree}
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
  val s3bucket: String = "examples.deepartist.org"

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
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input images (user upload prompts) and display a rescaled copies
      log.p(log.jpg(ImageArtUtil.load(log, styleUrl, 1200), "Input Style"))
      log.p(log.jpg(ImageArtUtil.load(log, contentUrl, 1200), "Input Content"))
      val canvas = new RefAtomicReference[Tensor](null)
      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(() => canvas.get())
      try {
        withMonitoredJpg(() => canvas.get().toImage) {
          paint(contentUrl, content => {
            val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
              "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
            val style = Tensor.fromRGB(ImageArtUtil.load(log, styleUrl, 600))
            val wctRestyled = fastPhotoStyleTransfer.photoWCT(style, content)
            val topology = new SearchRadiusTopology(content)
            topology.setSelfRef(true)
            topology.setVerbose(true)
            var affinity = new RelativeAffinity(content, topology)
            affinity.setContrast(20)
            affinity.setGraphPower1(2)
            affinity.setMixing(0.1)
            affinity.wrap((graphEdges: java.util.List[Array[Int]], innerResult: java.util.List[Array[Double]]) => adjust(graphEdges, innerResult, degree(innerResult), 0.2))
            solver.solve(topology, affinity, 1e-4).apply(wctRestyled)
          }, canvas, new VisualStyleContentNetwork(
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
              VGG16.VGG16_1e3
            ).flatMap(x => List(
              x, x.prependAvgPool(2)
            )),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-3, 3),
              new MomentMatcher()
            ),
            styleUrl = List(styleUrl),
            contentLayers = List(
              VGG16.VGG16_1c3
            ),
            contentModifiers = List(
              new ContentMatcher().scale(1e0)
            ),
            magnification = 2
          ),
            new BasicOptimizer {
              override val trainingMinutes: Int = 90
              override val trainingIterations: Int = 50
              override val maxRate = 1e8
            }, new GeometricSequence {
              override val min: Double = 800
              override val max: Double = 1400
              override val steps = 2
            }.toStream.map(_.round.toDouble))
          paint(contentUrl, x => x, canvas, new VisualStyleContentNetwork(
            styleLayers = List(
              VGG16.VGG16_1a,
              VGG16.VGG16_1b1,
              VGG16.VGG16_1b2,
              VGG16.VGG16_1c1,
              VGG16.VGG16_1c2,
              VGG16.VGG16_1c3,
              VGG16.VGG16_1d1,
              VGG16.VGG16_1d2,
              VGG16.VGG16_1d3
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-3, 3),
              new GramMatrixMatcher()
            ),
            styleUrl = List(styleUrl),
            contentLayers = List(
              VGG16.VGG16_1b2.prependAvgPool(2).appendMaxPool(2)
            ),
            contentModifiers = List(
              new ContentMatcher().scale(5e-1)
            ),
            magnification = 1
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 2000
            override val max: Double = 3000
            override val steps = 2
          }.toStream.map(_.round.toDouble))
        }
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()

  def solver: SmoothSolver = new SmoothSolver_Cuda()
}
