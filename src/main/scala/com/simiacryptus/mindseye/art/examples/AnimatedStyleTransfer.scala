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
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.Step
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

object AnimatedStyleTransfer extends AnimatedStyleTransfer with LocalRunner[Object] with NotebookRunner[Object]

class AnimatedStyleTransfer extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val styleUrl = "upload:Style"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  val minResolution = 300
  val maxResolution = 800
  val magnification = 2
  val steps = 3
  val frames = keyframes * 2 - 1
  val keyframes = 3
  override def indexStr = "302"

  override def description =
    """
      |Paints a series of images, each to reproduce the content of one image using the style of another reference image.
      |Combines them into a single animation.
    """.stripMargin.trim

  override def inputTimeoutSeconds = 3600


  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
    implicit val _ = log
    log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/"))
    log.onComplete(() => upload(log): Unit)
    log.p(log.jpg(ImageArtUtil.load(log, styleUrl, (maxResolution * Math.sqrt(magnification)).toInt), "Input Style"))
    log.p(log.jpg(ImageArtUtil.load(log, contentUrl, maxResolution), "Input Content"))
    val canvases = (1 to frames).map(_ => new AtomicReference[Tensor](null)).toList
    val registration = registerWithIndexGIF2(canvases.map(_.get()))
    try {
      paintBisection(contentUrl, initUrl, canvases, (1 to frames).map(f => f.toString -> {
        new VisualStyleContentNetwork(
          styleLayers = List(
            VGG16.VGG16_0,
            VGG16.VGG16_1a,
            VGG16.VGG16_1b1,
            VGG16.VGG16_1b2,
            VGG16.VGG16_1c1,
            VGG16.VGG16_1c2,
            VGG16.VGG16_1c3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer(),
            new MomentMatcher()
          ),
          styleUrl = List(styleUrl),
          contentLayers = List(
            VGG16.VGG16_1b2
          ),
          contentModifiers = List(
            new ContentMatcher()
          ),
          magnification = magnification
        )
      }), new BasicOptimizer {
        override val trainingMinutes: Int = 60
        override val trainingIterations: Int = 30
        override val maxRate = 1e9

        override def onStepComplete(trainable: Trainable, currentPoint: Step): Boolean = {
          super.onStepComplete(trainable, currentPoint)
        }
      }, x => new PipelineNetwork(1), keyframes, new GeometricSequence {
        override val min: Double = minResolution
        override val max: Double = maxResolution
        override val steps = AnimatedStyleTransfer.this.steps
      }.toStream.map(_.round.toDouble): _*)
      null
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }}()
}
