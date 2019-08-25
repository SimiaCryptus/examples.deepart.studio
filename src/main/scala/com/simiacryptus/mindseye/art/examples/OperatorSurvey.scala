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

import java.awt.image.BufferedImage
import java.awt.{Font, Graphics2D}
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import com.simiacryptus.mindseye.art.models.{VGG16, VGG19}
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.opt.Step
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object OperatorSurvey extends OperatorSurvey with LocalRunner[Object] with NotebookRunner[Object]

class OperatorSurvey extends ArtSetup[Object] {

  val styleUrl = "upload:Style"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  val resolution = 400
  val animationDelay = 1000
  val magnification = 4
  override def indexStr = "102"

  override def description =
    """
      |Reconstructs an example image's texture using a variety of combinations of signal operators
      |""".stripMargin.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
    implicit val _ = log
    log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    log.out(log.jpg(ImageArtUtil.load(log, styleUrl, (resolution * Math.sqrt(magnification)).toInt), "Input Style"))
    val renderedCanvases = new ArrayBuffer[() => BufferedImage]
    val registration = registerWithIndexGIF(renderedCanvases.map(_ ()), delay = animationDelay)
    NotebookRunner.withMonitoredGif(() => {
      renderedCanvases.map(_ ())
    }, delay = animationDelay) {
      try {
        val operatorMap = Map(
          "GramMatrixEnhancer" -> new GramMatrixEnhancer(),
          "GramMatrixEnhancer (10)" -> new GramMatrixEnhancer().setMinMax(-10, 10),
          "ChannelMeanMatcher" -> new ChannelMeanMatcher(),
          "PatternPCAMatcher" -> new PatternPCAMatcher(),
          "MomentMatcher (no-cov)" -> new MomentMatcher().setCovCoeff(0.0),
          "MomentMatcher (no-pos)" -> new MomentMatcher().setPosCoeff(0.0),
          "MomentMatcher" -> new MomentMatcher()
        )
        for (modifiers <- oneAtATime(operatorMap)) {
          val canvas = new AtomicReference[Tensor](null)
          val keys = modifiers.keys.toList.sorted
          log.h2(keys.mkString(" + "))
          renderedCanvases += (() => {
            val image = canvas.get().toImage
            if (null == image) image else {
              val graphics = image.getGraphics.asInstanceOf[Graphics2D]
              graphics.setFont(new Font("Calibri", Font.BOLD, 24))
              var y = 25
              for (modifier <- keys) {
                graphics.drawString(modifier, 10, y)
                y = y + 50
              }
              image
            }
          })
          withMonitoredJpg(() => Option(canvas.get()).map(_.toRgbImage).orNull) {
            var steps = 0
            Try {
              log.subreport("Painting", (sub: NotebookOutput) => {
                paint(styleUrl, initUrl, canvas, new VisualStyleNetwork(
                  styleLayers = List(
                    VGG16.VGG16_0,
                    VGG16.VGG16_1a,
                    VGG16.VGG16_1b1,
                    VGG16.VGG16_1b2,
                    VGG16.VGG16_1c1,
                    VGG16.VGG16_1c2,
                    VGG16.VGG16_1c3
                  ),
                  styleModifiers = modifiers.values.toList,
                  styleUrl = List(styleUrl),
                  magnification = magnification
                ), new BasicOptimizer {
                  override val trainingMinutes: Int = 60
                  override val trainingIterations: Int = 50
                  override val maxRate = 1e9

                  override def onStepComplete(trainable: Trainable, currentPoint: Step): Boolean = {
                    steps = steps + 1
                    super.onStepComplete(trainable, currentPoint)
                  }
                }, new GeometricSequence {
                  override val min: Double = resolution
                  override val max: Double = resolution
                  override val steps = 1
                }.toStream.map(_.round.toDouble): _*)(sub)
                null
              })
            }
            if (steps < 3 && !renderedCanvases.isEmpty) {
              renderedCanvases.remove(renderedCanvases.size - 1)
            }
            uploadAsync(log)
          }(log)
        }
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }}()

  /**
    * Full expansion of sets containing one or more element of the source set
    * @param map Input collection
    * @tparam T collection type
    * @return All valid subsets of the collection
    */
  def submaps[T](map: Map[String, T]): List[Map[String, T]] = {
    subsets(map.toList).sortBy(_.toList.sortBy(_._1).mkString("")).map(_.toMap)
  }

  def oneAtATime[T](map: Map[String, T]): List[Map[String, T]] = {
    map.toList.map(Map(_))
  }

  def subsets[T](map: Seq[T]) = {
    map.map(x => List(List.empty, List(x)))
      .reduce((a, b) => for (a <- a; b <- b) yield a ++ b)
      .filterNot(_.isEmpty).map(_.toSet).distinct
  }
}
