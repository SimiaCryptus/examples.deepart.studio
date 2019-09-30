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
import java.net.URI
import java.util.stream.{Collectors, IntStream}
import java.util.zip.ZipFile

import com.simiacryptus.mindseye.art.photo.RegionAssembler.volumeEntropy
import com.simiacryptus.mindseye.art.photo.SegmentUtil.{paintWithRandomColors, removeTinyInclusions}
import com.simiacryptus.mindseye.art.photo._
import com.simiacryptus.mindseye.art.photo.affinity.RasterAffinity._
import com.simiacryptus.mindseye.art.photo.affinity.RelativeAffinity
import com.simiacryptus.mindseye.art.photo.cuda.SmoothSolver_Cuda
import com.simiacryptus.mindseye.art.photo.topology.{SearchRadiusTopology, SimpleRasterTopology}
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.mindseye.lang.{Coordinate, Tensor}
import com.simiacryptus.notebook.{EditImageQuery, NotebookOutput}
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.util.Util

import scala.collection.JavaConverters._
import scala.collection.mutable


object SegmentIsolation extends SegmentIsolation with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

class SegmentIsolation extends ArtSetup[Object] {

  val contentUrl = "file:///C:/Users/andre/Downloads/postable_pics/0DSC_0005.jpg"
  val s3bucket: String = ""
  val partitions = 3
  val imageSize = 800

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
      var image = ImageArtUtil.load(log, contentUrl, imageSize)

      val image_tensor = Tensor.fromRGB(image)
      val dimensions = image_tensor.getDimensions
      val pixels = dimensions(0) * dimensions(1)
      val topology_simple = new SimpleRasterTopology(image_tensor.getDimensions).cached()

      log.h1("Image Region Decomposition")
      log.h2("Flattened Image")
      //val topology = new SimpleRasterTopology(image_tensor.getDimensions).cached()
      val topology = new SearchRadiusTopology(image_tensor)
        .setInitialRadius(2)
        .setMaxChromaDist(1)
        .setNeighborhoodSize(4)
        .setSelfRef(true)
        .setVerbose(true)
        .cached()
      val affinity = new RelativeAffinity(image_tensor, topology)
        .setContrast(10)
        .setGraphPower1(2)
        .setMixing(0.1)
      val flattenedColors = log.eval(() => {
        SegmentUtil.flattenColors(image_tensor,
          topology,
          affinity.wrap((graphEdges: java.util.List[Array[Int]], innerResult: java.util.List[Array[Double]]) =>
            adjust(graphEdges, innerResult, degree(innerResult), 0.5)), 3)
      })
      val flattenedTensor = Tensor.fromRGB(flattenedColors)
      var pixelMap: Array[Int] = SegmentUtil.markIslands(
        topology_simple,
        (x: Array[Int]) => flattenedTensor.getPixel(x: _*),
        (a: Array[Double], b: Array[Double]) => IntStream.range(0, a.length)
          .mapToDouble((i: Int) => a(i) - b(i))
          .map((x: Double) => x * x).average.getAsDouble < 1.0,
        128,
        pixels
      )
      var graph = SmoothSolver_Cuda.laplacian(affinity, topology_simple).matrix.project(pixelMap)

      log.h2("Basic Regions")
      log.eval(() => {
        paintWithRandomColors(topology_simple, image_tensor, pixelMap, graph)
      })

      log.h2("Remove tiny islands")
      log.eval(() => {
        var projection1 = removeTinyInclusions(pixelMap, graph, 4)
        graph = graph.project(projection1).assertSymmetric()
        pixelMap = pixelMap.map(projection1(_))
        val activeRows = graph.activeRows()
        graph = graph.select(activeRows).assertSymmetric()
        var projection2 = activeRows.zipWithIndex.toMap
        pixelMap = pixelMap.map(projection2.get(_).getOrElse(0))
        paintWithRandomColors(topology_simple, image_tensor, pixelMap, graph)
      })

      log.h2("Reduced Regions")
      log.eval(() => {
        val regionAssembler = volumeEntropy(graph, pixelMap, image_tensor, topology_simple).reduceTo(5000)
        graph = graph.project(regionAssembler.getProjection)
        pixelMap = regionAssembler.getPixelMap
        val activeRows = graph.activeRows()
        graph = graph.select(activeRows).assertSymmetric()
        val projection = activeRows.zipWithIndex.toMap
        pixelMap = pixelMap.map(projection.get(_).getOrElse(0))
        paintWithRandomColors(topology_simple, image_tensor, pixelMap, graph)
      })

      for (i <- 0 to 2) {
        val selection = select(log, image)
        var seedMarks: Map[Int, Int] = (for (
          x <- 0 until dimensions(0);
          y <- 0 until dimensions(1);
          color <- selection(x, y)
        ) yield {
          pixelMap(topology_simple.getIndexFromCoords(x, y)) -> color
        }).groupBy(_._1).mapValues(_.head._2)

        for (clr <- 0 until partitions) {
          log.p(log.jpg(image_tensor.mapCoords((coordinate: Coordinate) => {
            val coords = coordinate.getCoords()
            if (seedMarks.get(pixelMap(topology_simple.getIndexFromCoords(coords(0), coords(1)))).map(_ == clr).getOrElse(false)) {
              image_tensor.get(coordinate)
            } else {
              0.0
            }
          }).toImage, "Selection " + clr))
        }

        seedMarks = log.eval(() => {
          val regionData = RegionAssembler.epidemic(graph, pixelMap, image_tensor, topology_simple,
            seedMarks.map(t => t._1.asInstanceOf[Integer] -> t._2.asInstanceOf[Integer]).asJava
          ).reduceTo(1)
          regionData.regions.asScala.filter(_.marks.size() > 1).toList
          val newMarks = regionData.regions.asScala.flatMap(region =>
            region.original_regions.asScala.map(_.toInt -> region.marks)
          ).toMap
          newMarks.filter(_._2.size() == 1).mapValues(_.asScala.head.toInt)
        })

        for (clr <- 0 until partitions) {
          log.p(log.jpg(image_tensor.mapCoords((coordinate: Coordinate) => {
            val coords = coordinate.getCoords()
            if (seedMarks.get(pixelMap(topology_simple.getIndexFromCoords(coords(0), coords(1)))).map(_ == clr).getOrElse(false)) {
              image_tensor.get(coordinate)
            } else {
              0.0
            }
          }).toImage, "Selection " + clr))
        }

      }
      null
    }
  }()

  def select(log: NotebookOutput, image: BufferedImage) = {
    val editResult = new EditImageQuery(log, image).print().get()
    var diff_tensor = diff(Tensor.fromRGB(image), Tensor.fromRGB(editResult))

    def apxColor(a: Array[Double]): List[Int] = a.map(x => x.toInt).toList

    val dimensions = diff_tensor.getDimensions
    val colors: Map[List[Int], Int] = diff_tensor.getPixelStream.collect(Collectors.toList())
      .asScala.map(apxColor).filter(_.sum != 0).groupBy(x => x).mapValues(_.size)
      .toList.sortBy(-_._2).take(partitions).map(_._1).toArray.zipWithIndex.toMap
    (x: Int, y: Int) => colors.get(apxColor(diff_tensor.getPixel(x, y)))
  }

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

  def expand(tree: RegionAssembler.RegionTree, markup: Map[Int, Set[Int]], recursion: Int = 0): Map[Int, Int] = {
    if (recursion > 1000) throw new RuntimeException()
    val paints = tree.regions.flatMap(r => markup.get(r)).flatten.distinct
    if (paints.length > 1) {
      val children = tree.children
      val buffer: mutable.Buffer[(Int, Int)] = List.empty.toBuffer // children.flatMap(expand(_, markup).toList).toMap.toBuffer
      for (child <- children) buffer ++= expand(child, markup, recursion + 1)
      buffer.toMap
    } else if (paints.length == 0) {
      Map.empty
    } else {
      tree.regions.map(_ -> paints.head).toMap
    }
  }
}
