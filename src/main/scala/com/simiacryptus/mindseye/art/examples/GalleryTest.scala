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

import com.amazonaws.services.s3.AmazonS3
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.util.ImageArtUtil.loadImages
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.JavaConverters.seqAsJavaListConverter


object GalleryTest extends GalleryTest with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

class GalleryTest extends ArtSetup[Object, GalleryTest] with ArtworkStyleGalleries {

  val styleUrls = Array(
    //"upload:Image"
    "cubism_portraits"
  )
  val s3bucket: String = "test.deepartist.org"
  val message = ""
  val resolution = -1

  override def indexStr = "000"

  override def description = <div>
    A basic notebook that displays an Artwork Style Gallery.
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = {
    implicit val l = log
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    // First, basic configuration so we publish to our s3 site
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    val lowRes = styleGalleries_lowRes(styleUrls).filter(!styleUrls.contains(_))
    val nonGallery = styleUrls.filter(lowRes.contains(_))
    log.h1("Styles")
    if(nonGallery.nonEmpty) {
      loadImages(log, nonGallery.toList.asJava, -1).foreach(img => log.p(log.jpg(img, "Input Style")))
    }
    if(lowRes.nonEmpty) {
      log.h2("Low Res Galleries")
      loadImages(log, lowRes.asJava, -1).foreach(img => log.p(log.jpg(img, "Input Style")))
    }
    val highRes = styleGalleries_highRes(styleUrls).filter(!styleUrls.contains(_)).filter(!lowRes.contains(_))
    if(highRes.nonEmpty) {
      log.h2("High Res Galleries")
      loadImages(log, highRes.asJava, -1).foreach(img => log.p(log.jpg(img, "Input Style")))
    }
    null
  }
}
