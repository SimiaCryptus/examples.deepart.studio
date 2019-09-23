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
import java.util.stream.Collectors
import java.util.zip.ZipFile

import com.simiacryptus.mindseye.art.photo._
import com.simiacryptus.mindseye.art.photo.affinity.RasterAffinity._
import com.simiacryptus.mindseye.art.photo.affinity.RelativeAffinity
import com.simiacryptus.mindseye.art.photo.cuda.{EigenvectorSolver_Cuda, SmoothSolver_Cuda}
import com.simiacryptus.mindseye.art.photo.topology.SearchRadiusTopology
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.mindseye.lang.{Coordinate, Tensor}
import com.simiacryptus.notebook.{EditImageQuery, NotebookOutput}
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.util.Util

import scala.collection.JavaConverters._


object SegmentIsolation extends SegmentIsolation with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

class SegmentIsolation extends ArtSetup[Object] {

  val contentUrl = "file:///C:/Users/andre/Downloads/postable_pics/0DSC_0005.jpg"
  val s3bucket: String = ""

  override def indexStr = "401"

  override def description = <div>
    Demonstrates simple isolation of an image segment based on user drawing
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      lazy val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
        "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
      // Fetch input images (user upload prompts) and display a rescaled copies
      var image = ImageArtUtil.load(log, contentUrl, 600)

      def image_tensor = Tensor.fromRGB(image)

      val topology = new SearchRadiusTopology(image_tensor).setSelfRef(true).setVerbose(true)
      //      val topology = new SimpleRasterTopology(image_tensor.getDimensions)
      val affinity = new RelativeAffinity(image_tensor, topology).setContrast(10).setGraphPower1(2).setMixing(0.5)
        .wrap((graphEdges: java.util.List[Array[Int]], innerResult: java.util.List[Array[Double]]) => adjust(graphEdges, innerResult, degree(innerResult), 0.5))
      //val affinity = new MattingAffinity(image_tensor, topology).setGraphPower1(2).setMixing(0.5)
      //      val affinity = new GaussianAffinity(image_tensor, 10, topology)

      //new MattingAffinity(image_tensor, topology)
      // new RelativeAffinity(image_tensor, topology).setContrast(20).setGraphPower1(2).setMixing(0.1)

      val laplacian = SmoothSolver_Cuda.laplacian(affinity, topology)
      new GeometricSequence {
        override def min: Double = 0.5

        override def max: Double = 2

        override def steps: Int = 20
      }.toStream.foreach(mu0 => {
        log.h3(s"mu0=$mu0")
        new EigenvectorSolver_Cuda(laplacian, mu0.floatValue()).eigenVectors(topology, 1).asScala.foreach(eigenvect => {
          log.p(log.png(eigenvect.rescaleRms(100).toGrayImage, "Solution"))
        })
      })


      for (i <- 0 to 5) {
        log.p(log.png(image, "Content"))

        val editResult = new EditImageQuery(log, image).print().get()
        var diff_tensor = diff(image_tensor, Tensor.fromRGB(editResult))

        log.p(log.png(diff_tensor.toImage, "Edit"))

        def apxColor(a: Array[Double]): List[Int] = {
          a.map(x => x.toInt).toList
        }

        val dimensions = diff_tensor.getDimensions
        val colors = diff_tensor.getPixelStream.collect(Collectors.toList())
          .asScala.map(apxColor).filter(_.sum != 0).groupBy(x => x).mapValues(_.size)
          .toList.sortBy(-_._2).take(3).map(_._1).toArray
        val eigenSeeds = colors.map(color => {
          new Tensor(dimensions(0), dimensions(1)).mapCoords((c: Coordinate) => {
            val ints = c.getCoords()
            if (color == apxColor((0 until dimensions(2)).map(c => diff_tensor.get(ints(0), ints(1), c)).toArray)) {
              255.0
            } else {
              0.0
            }
          })
        })
        val eigenRefine = new EigenvectorSolver_Cuda(laplacian, 1e-4f).eigenRefiner(topology)
        eigenSeeds.foreach(tensor => {
          log.p(log.png(tensor.toGrayImage, "Seed"))
          log.p(log.png(eigenRefine.apply(tensor).rescaleRms(100).toGrayImage, "Solution"))
        })

        image = editResult
      }
      null
    }
  }()

  def diff(image_tensor: Tensor, edit_tensor: Tensor): Tensor = {
    edit_tensor.mapCoords((c: Coordinate) => {
      val val_tensor = image_tensor.get(c.getIndex)
      val val_edit = edit_tensor.get(c.getIndex)
      if (Math.abs(val_edit - val_tensor) > 1) {
        val_edit
      } else {
        0
      }
    })
  }
}
