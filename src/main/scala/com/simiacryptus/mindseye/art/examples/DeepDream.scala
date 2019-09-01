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


object DeepDream extends DeepDream with LocalRunner[Object] with NotebookRunner[Object]

class DeepDream extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val s3bucket: String = "examples.deepartist.org"
  val resolution = 800

  override def indexStr = "204"

  override def description = <div>
    Implements the DeepDream image processing algorithm:
    <ol>
      <li>Start from an input image</li>
      <li>Use the final layer of an inception network</li>
      <li>Operator to maximize RMS signal</li>
      <li>Single resolution</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600


  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input image (user upload prompt)
      ImageArtUtil.load(log, contentUrl, resolution)
      val canvas = new AtomicReference[Tensor](null)
      // Execute the main process while registered with the site index
      val registration = registerWithIndexJPG(canvas.get())
      try {
        // In contrast to other uses, in this painting operation we are enhancing
        // an input image (content) only, with no other inputs or canvas preparation.
        withMonitoredJpg(() => canvas.get().toImage) {
          paint(contentUrl, contentUrl, canvas, new VisualStyleNetwork(
            styleLayers = List(
              // DeepDream traditionally uses the last layer of a network
              VGG16.VGG16_3b
            ),
            styleModifiers = List(
              // This operator increases the RMS power of any signal
              new ChannelPowerEnhancer()
            ),
            styleUrl = List(contentUrl)
          ), new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 200
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = resolution
            override val max: Double = resolution
            override val steps = 1
          }.toStream.map(_.round.toDouble): _*)
          null
        }
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()
}
