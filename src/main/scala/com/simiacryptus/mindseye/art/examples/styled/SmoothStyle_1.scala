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
import com.simiacryptus.mindseye.art.photo.cuda.SmoothSolver_Cuda
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.util.Util


object SmoothStyle_1 extends SmoothStyle_1 with LocalRunner[Object] with NotebookRunner[Object]

class SmoothStyle_1 extends ArtSetup[Object, SmoothStyle_1] {

  val styleUrl = "upload:Style"
  val s3bucket: String = ""
  val initUrl = "50 + noise * 0.5"

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

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    () => {
      implicit val l = log
      // First, basic configuration so we publish to our s3 site
      if (Option(s3bucket).filter(!_.isEmpty).isDefined)
        log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input images (user upload prompts) and display a rescaled copies


      log.p(log.jpg(ImageArtUtil.loadImage(log, styleUrl, 1200), "Input Style"))
      val canvas = new RefAtomicReference[Tensor](null)
      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(() => canvas.get()).toList
      try {
        withMonitoredJpg(() => canvas.get().toImage) {
          paint(styleUrl, contentDims => {
            val wctRes = 400
            val height = wctRes * (contentDims.getDimensions()(1).toDouble / contentDims.getDimensions()(0))
            val content = Tensor.fromRGB(ImageArtUtil.loadImage(log, initUrl, wctRes, height.ceil.toInt))
            val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
              "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
            val style = Tensor.fromRGB(ImageArtUtil.loadImage(log, styleUrl, wctRes))
            fastPhotoStyleTransfer.photoWCT(style, content)
          }, canvas, new VisualStyleNetwork(
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
              VGG19.VGG19_1e1,
              VGG19.VGG19_1e2,
              VGG19.VGG19_1e3,
              VGG19.VGG19_1e4
            ).flatMap(x => List(
              x, x.prependAvgPool(2)
            )),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-10, 10).scale(1e0),
              new MomentMatcher()
            ),
            styleUrls = List(styleUrl),
            magnification = Array(16.0)
          ),
            new BasicOptimizer {
              override val trainingMinutes: Int = 60
              override val trainingIterations: Int = 20
              override val maxRate = 1e8
            }, new GeometricSequence {
              override val min: Double = 400
              override val max: Double = 600
              override val steps = 2
            }.toStream.map(_.ceil))
          paint(styleUrl, x => x, canvas, new VisualStyleNetwork(
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
            ),
            styleModifiers = List(
              new GramMatrixEnhancer().setMinMax(-10, 10).scale(1e0),
              new MomentMatcher()
            ),
            styleUrls = List(styleUrl),
            magnification = Array(4.0)
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 90
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 1000
            override val max: Double = 1000
            override val steps = 1
          }.toStream.map(_.round.toDouble))
          paint(styleUrl, x => x, canvas, new VisualStyleNetwork(
            styleLayers = List(
              VGG19.VGG19_1a,
              VGG19.VGG19_1b1,
              VGG19.VGG19_1b2,
              VGG19.VGG19_1c1,
              VGG19.VGG19_1c2,
              VGG19.VGG19_1c3,
              VGG19.VGG19_1c4
            ),
            styleModifiers = List(
              //new ChannelMeanMatcher(),
              new GramMatrixMatcher()
            ),
            styleUrls = List(styleUrl),
            magnification = Array(1.0),
            maxWidth = 6000,
            tileSize = 800
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 90
            override val trainingIterations: Int = 10
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 1800
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
