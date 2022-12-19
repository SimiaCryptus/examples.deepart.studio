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

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.ec2client
import com.simiacryptus.mindseye.art.util.{ImageOptimizer, _}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.LocalRunner

object AnimatedStyleTransfer extends AnimatedStyleTransfer with LocalRunner[Object] with NotebookRunner[Object]

class AnimatedStyleTransfer extends ArtSetup[Object,AnimatedStyleTransfer] {

  val contentUrl = "upload:Content"
  val styleUrl = "upload:Style"
  val initUrl: String = "50 + plasma * 0.5"
  val minResolution = 300
  val maxResolution = 800
  val magnification = Array(2.0)
  val steps = 2
  val keyframes = 3

  override def indexStr = "302"

  override def description = <div>
    Paints a series of images, each to match the content of one while in the style of another using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard VGG16 layers</li>
      <li>Operators to match content and constrain and enhance style</li>
      <li>Progressive resolution increase</li>
    </ol>
    The parameters for each frame are fixed, but due to the random initialization
    and loose constraints we can achive a dynamic effect.
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      implicit val implicitLog = log
      implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
      // First, basic configuration so we publish to our s3 site
      if (Option(s3bucket).filter(!_.isEmpty).isDefined)
        log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)

      // Fetch input images (user upload prompts) and display rescaled copies
      ImageArtUtil.loadImages(log, styleUrl, (maxResolution * Math.sqrt(magnification.head)).toInt).foreach(img => log.p(log.jpg(img, "Input Style")))
      log.p(log.jpg(ImageArtUtil.loadImage(log, contentUrl, maxResolution), "Input Content"))

      def frames = keyframes * 2 - 1

      val canvases = (1 to frames).map(_ => new RefAtomicReference[Tensor](null)).toList.toBuffer
      // Execute the main process while registered with the site index
      val registration = registerWithIndexGIF_Cyclic(canvases.map(_.get()).toList)
      try {
        animate(
          contentUrl = contentUrl,
          initUrl = initUrl,
          canvases = canvases,
          networks = (1 to frames).map(f => f.toDouble -> {
            new VisualStyleContentNetwork(
              styleLayers = List(
                // We select all the lower-level layers to achieve a good balance between speed and accuracy.
                VGG16.VGG16_0b,
                VGG16.VGG16_1a,
                VGG16.VGG16_1b1,
                VGG16.VGG16_1b2,
                VGG16.VGG16_1c1,
                VGG16.VGG16_1c2,
                VGG16.VGG16_1c3
              ),
              styleModifiers = List(
                // These two operators are a good combination for a vivid yet accurate style
                new GramMatrixEnhancer(),
                new MomentMatcher()
              ),
              styleUrls = Seq(styleUrl),
              contentLayers = List(
                VGG16.VGG16_1b2
              ),
              contentModifiers = List(
                new ContentMatcher()
              ),
              magnification = magnification
            )
          }).toList.toBuffer,
          optimizer = new ImageOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 30
            override val maxRate = 1e9
          },
          resolutions = new GeometricSequence {
            override val min: Double = minResolution
            override val max: Double = maxResolution
            override val steps = AnimatedStyleTransfer.this.steps
          }.toStream.map(_.round.toDouble)
        )
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()
}
