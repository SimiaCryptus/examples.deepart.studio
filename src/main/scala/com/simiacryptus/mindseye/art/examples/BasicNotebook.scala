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

import java.awt.{Font, Graphics2D}
import java.net.URI

import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

object BasicNotebook extends BasicNotebook with LocalRunner[Object] with NotebookRunner[Object]

class BasicNotebook extends ArtSetup[Object] {

  val styleUrl = "upload:Image"
  val s3bucket: String = "examples.deepartist.org"
  val message = "Hello World!"
  val resolution = 400

  override def indexStr = "000"

  override def description = <div>
    A very basic notebook that displays a message.
    No AI code, just a demo of the publishing system used.
    It first prompts the user to upload an image, then resizes it and draws some text.
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Now we evaluate the drawing code inside a logged eval block.
      // This will publish the code, the result, any logs, the duration, and also link to github.
      val canvas = log.eval(() => {
        val canvas = ImageArtUtil.loadImage(log, styleUrl, resolution.toInt)
        val graphics = canvas.getGraphics.asInstanceOf[Graphics2D]
        graphics.setFont(new Font("Calibri", Font.BOLD, 42))
        graphics.drawString(message, 10, 50)
        canvas
      })
      // Usually not on one line, this code publishes our result to the site's index so it is linked from the homepage.
      registerWithIndexJPG(() => Tensor.fromRGB(canvas)).foreach(_.stop()(s3client, ec2client))
      null
    }
  }()
}
