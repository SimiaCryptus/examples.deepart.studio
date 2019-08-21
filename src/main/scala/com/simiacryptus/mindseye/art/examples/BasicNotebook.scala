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
  val s3bucket: String = "examples.deepart.studio"
  val resolution = 400

  override def description = "Basic testing notebook that says hi."
  override def inputTimeoutSeconds = 5

  override def postConfigure(log: NotebookOutput) = {
    implicit val _ = log
    log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/"))
    log.onComplete(() => upload(log): Unit)
    val canvas = log.eval(()=>{
      val canvas = ImageArtUtil.load(log, styleUrl, resolution.toInt)
      val graphics = canvas.getGraphics.asInstanceOf[Graphics2D]
      graphics.setFont(new Font("Calibri", Font.BOLD, 42))
      graphics.drawString("Hello World!", 10, 50)
      canvas
    })
    registerWithIndexJPG(Tensor.fromRGB(canvas)).foreach(_.stop()(s3client, ec2client))
    null
  }
}
