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

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner


object HighResStyleTransfer extends HighResStyleTransfer with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 8080
}

class HighResStyleTransfer extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val styleUrl = "upload:Style"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  override def indexStr = "301"

  override def description = <div>
    Paints an image in the style of another using multiple resolution phases, each with tuned parameters.
    The result is a high resolution, high quality rendered painting.
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600


  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input images (user upload prompts) and display a rescaled copies
      log.p(log.jpg(ImageArtUtil.load(log, styleUrl, (1200 * Math.sqrt(2)).toInt), "Input Style"))
      log.p(log.jpg(ImageArtUtil.load(log, contentUrl, 1200), "Input Content"))
      val canvas = new AtomicReference[Tensor](null)
      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(canvas.get())
      try {
        // Display an additional image inside the report itself
        withMonitoredJpg(() => canvas.get().toImage) {
          paint(contentUrl, initUrl, canvas, new VisualStyleContentNetwork(
            styleLayers = List(
              // We select all the lower-level layers to achieve a good balance between speed and accuracy.
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
              // These two operators are a good combination for a vivid yet accurate style
              new GramMatrixEnhancer(),
              new MomentMatcher()
            ),
            styleUrl = List(styleUrl),
            contentLayers = List(
              // We use fewer layer to be a constraint, since the ContentMatcher operation defines
              // a stronger operation. Picking a mid-level layer ensures the match is somewhat
              // faithful to color, contains detail, and still accomidates local changes for style.
              VGG16.VGG16_1b2
            ),
            contentModifiers = List(
              // Standard content matching operator
              new ContentMatcher()
            ),
            magnification = 9
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 200
            override val max: Double = 400
            override val steps = 2
          }.toStream.map(_.round.toDouble): _*)
          paint(contentUrl, initUrl, canvas, new VisualStyleContentNetwork(
            styleLayers = List(
              // We select all the lower-level layers to achieve a good balance between speed and accuracy.
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
              VGG16.VGG16_1e3,
              VGG16.VGG16_2,
              VGG16.VGG16_3a
            ),
            styleModifiers = List(
              // These two operators are a good combination for a vivid yet accurate style
              new GramMatrixEnhancer(),
              new MomentMatcher()
            ),
            styleUrl = List(styleUrl),
            contentLayers = List(
              // We use fewer layer to be a constraint, since the ContentMatcher operation defines
              // a stronger operation. Picking a mid-level layer ensures the match is somewhat
              // faithful to color, contains detail, and still accomidates local changes for style.
              VGG16.VGG16_1b2.prependAvgPool(2)
            ),
            contentModifiers = List(
              // Standard content matching operator
              new ContentMatcher().scale(5)
            )
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 600
            override val max: Double = 800
            override val steps = 2
          }.toStream.map(_.round.toDouble): _*)
          paint(contentUrl, initUrl, canvas, new VisualStyleContentNetwork(
            styleLayers = List(
              // We select all the lower-level layers to achieve a good balance between speed and accuracy.
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
            ),
            styleModifiers = List(
              // These two operators are a good combination for a vivid yet accurate style
              new GramMatrixEnhancer(),
              new MomentMatcher()
            ),
            styleUrl = List(styleUrl),
            contentLayers = List(
              // We use fewer layer to be a constraint, since the ContentMatcher operation defines
              // a stronger operation. Picking a mid-level layer ensures the match is somewhat
              // faithful to color, contains detail, and still accomidates local changes for style.
              VGG16.VGG16_1b2.prependAvgPool(2)
            ),
            contentModifiers = List(
              // Standard content matching operator
              new ContentMatcher().scale(5)
            )
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 1024
            override val max: Double = 1600
            override val steps = 3
          }.toStream.map(_.round.toDouble): _*)
          paint(contentUrl, initUrl, canvas, new VisualStyleContentNetwork(
            styleLayers = List(
              // We select all the lower-level layers to achieve a good balance between speed and accuracy.
              VGG16.VGG16_1a,
              VGG16.VGG16_1b1,
              VGG16.VGG16_1b2
            ),
            styleModifiers = List(
              // These two operators are a good combination for a vivid yet accurate style
              new GramMatrixEnhancer(),
              new MomentMatcher()
            ),
            styleUrl = List(styleUrl),
            contentLayers = List(
              // We use fewer layer to be a constraint, since the ContentMatcher operation defines
              // a stronger operation. Picking a mid-level layer ensures the match is somewhat
              // faithful to color, contains detail, and still accomidates local changes for style.
              VGG16.VGG16_1b2.prependAvgPool(3)
            ),
            contentModifiers = List(
              // Standard content matching operator
              new ContentMatcher().scale(1e1)
            )
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 20
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = 2000
            override val max: Double = 4000
            override val steps = 3
          }.toStream.map(_.round.toDouble): _*)
        }
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()
}
