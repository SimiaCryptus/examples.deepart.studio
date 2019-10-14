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

import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URI
import java.util
import java.util.stream.{Collectors, DoubleStream, IntStream}
import java.util.zip.ZipFile

import com.simiacryptus.mindseye.art.photo.SegmentUtil.{paintWithRandomColors, removeTinyInclusions}
import com.simiacryptus.mindseye.art.photo.affinity.RasterAffinity.{adjust, degree}
import com.simiacryptus.mindseye.art.photo.affinity.RelativeAffinity
import com.simiacryptus.mindseye.art.photo.cuda.SmoothSolver_Cuda
import com.simiacryptus.mindseye.art.photo.topology.{SearchRadiusTopology, SimpleRasterTopology}
import com.simiacryptus.mindseye.art.photo.{FastPhotoStyleTransfer, RegionAssembler, SegmentUtil, SmoothSolver}
import com.simiacryptus.mindseye.art.util.ArtSetup
import com.simiacryptus.mindseye.lang.{Coordinate, Tensor}
import com.simiacryptus.notebook.{EditImageQuery, NotebookOutput, UploadImageQuery}
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.util.Util
import javax.imageio.ImageIO

import scala.collection.JavaConverters._
import scala.collection.mutable

abstract class SegmentingSetup extends ArtSetup[Object] {

  def smoothStyle(content: Tensor, style: Tensor, contentMask: Tensor)(implicit log: NotebookOutput) = {
    val contentDensity = DoubleStream.of(contentMask.getData: _*).average().getAsDouble
    val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
      "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
    val wctRestyled = fastPhotoStyleTransfer.photoWCT(style, content, contentDensity, 1.0)
    fastPhotoStyleTransfer.freeRef()
    val topology = new SearchRadiusTopology(content).setSelfRef(true).setVerbose(true)
    var affinity = new RelativeAffinity(content, topology).setContrast(10).setGraphPower1(2).setMixing(0.5)
      .wrap((graphEdges: util.List[Array[Int]], innerResult: util.List[Array[Double]]) => adjust(graphEdges, innerResult, degree(innerResult), 0.2))
    val smoothed = solver.solve(topology, affinity, 1e-4).apply(wctRestyled)
    smoothed.mapCoordsAndFree((c: Coordinate) => {
      val bg = contentMask.get(c)
      if (bg == 1) smoothed.get(c)
      else content.get(c)
    })
  }

  def solver: SmoothSolver = new SmoothSolver_Cuda()

  def drawMask(content: BufferedImage, colors: Color*)(implicit log: NotebookOutput) = {
    val image_tensor: Tensor = Tensor.fromRGB(content)
    val dimensions: Array[Int] = image_tensor.getDimensions
    val pixels: Int = dimensions(0) * dimensions(1)
    val topology = new SimpleRasterTopology(image_tensor.getDimensions).cached()

    log.h1("Image Region Decomposition")
    log.h2("Flattened Image")
    //val topology = new SimpleRasterTopology(image_tensor.getDimensions).cached()
    val topology_analysis = new SearchRadiusTopology(image_tensor)
      .setInitialRadius(1)
      .setMaxSpatialDist(32)
      .setMaxChromaDist(0.2)
      .setNeighborhoodSize(4)
      .setSelfRef(true)
      .setVerbose(true)
      .cached()
    val affinity = new RelativeAffinity(image_tensor, topology_analysis)
      .setContrast(50)
      .setGraphPower1(2)
      .setMixing(0.5)
    val flattenedColors = log.eval(() => {
      SegmentUtil.flattenColors(image_tensor,
        topology_analysis,
        affinity.wrap((graphEdges: util.List[Array[Int]], innerResult: util.List[Array[Double]]) =>
          adjust(graphEdges, innerResult, degree(innerResult), 0.5)), 3, solver)
    })
    val flattenedTensor = Tensor.fromRGB(flattenedColors)
    var pixelMap: Array[Int] = SegmentUtil.markIslands(
      topology,
      (x: Array[Int]) => flattenedTensor.getPixel(x: _*),
      (a: Array[Double], b: Array[Double]) => IntStream.range(0, a.length)
        .mapToDouble((i: Int) => a(i) - b(i))
        .map((x: Double) => x * x).average.getAsDouble < 0.5,
      128,
      pixels
    )
    var graph = SmoothSolver_Cuda.laplacian(affinity, topology).matrix.project(pixelMap)

    log.h2("Basic Regions")
    log.eval(() => {
      paintWithRandomColors(topology, image_tensor, pixelMap, graph)
    })

    log.h2("Remove tiny islands")
    log.eval(() => {
      var projection1 = removeTinyInclusions(pixelMap, graph, 1, 4)
      graph = graph.project(projection1).assertSymmetric()
      pixelMap = pixelMap.map(projection1(_))
      val activeRows = graph.activeRows()
      graph = graph.select(activeRows).assertSymmetric()
      var projection2 = activeRows.zipWithIndex.toMap
      pixelMap = pixelMap.map(projection2.get(_).getOrElse(0))
      paintWithRandomColors(topology, image_tensor, pixelMap, graph)
    })

    log.h2("Reduced Regions")
    log.eval(() => {
      val regionAssembler = RegionAssembler.volumeEntropy(graph, pixelMap, image_tensor, topology)
        .reduceTo(10000)
      graph = graph.project(regionAssembler.getProjection)
      pixelMap = regionAssembler.getPixelMap
      val activeRows = graph.activeRows()
      graph = graph.select(activeRows).assertSymmetric()
      val projection = activeRows.zipWithIndex.toMap
      pixelMap = pixelMap.map(projection.get(_).getOrElse(0))
      paintWithRandomColors(topology, image_tensor, pixelMap, graph)
    })

    val selection = select(log, content, colors: _*)
    val seedMarks: Map[Int, Int] = log.eval(() => {
      val seedMarks = (for (
        x <- 0 until dimensions(0);
        y <- 0 until dimensions(1);
        color <- selection(x, y)
      ) yield pixelMap(topology.getIndexFromCoords(x, y)) -> color).groupBy(_._1).mapValues(_.head._2)
      RegionAssembler.epidemic(graph, pixelMap, image_tensor, topology,
        seedMarks.map(t => t._1.asInstanceOf[Integer] -> t._2.asInstanceOf[Integer]).asJava
      ).reduceTo(1).regions.asScala.flatMap((region) =>
        region.original_regions.asScala.map(_.toInt -> region.marks)
      ).toMap.filter(_._2.size() == 1).mapValues(_.asScala.head.toInt)
    })

    log.h2("Final Markup")
    log.eval(() => {
      SegmentUtil.paint(topology, image_tensor, pixelMap, (for (
        (pixelAssignment, marking) <- seedMarks
      ) yield {
        val color = colors(marking)
        pixelAssignment.asInstanceOf[Integer] -> Array(
          color.getBlue.toDouble,
          color.getGreen.toDouble,
          color.getRed.toDouble
        )
      }).asJava)
    })

    for (clr <- 0 until colors.size) yield {
      image_tensor.mapCoords((coordinate: Coordinate) => {
        val coords = coordinate.getCoords()
        if (seedMarks.get(pixelMap(topology.getIndexFromCoords(coords(0), coords(1)))).map(_ == clr).getOrElse(false)) {
          image_tensor.get(coordinate)
        } else {
          0.0
        }
      })
    }
  }

  def select(log: NotebookOutput, image: BufferedImage, colors: Color*) = {
    val editResult = new EditImageQuery(log, image).print().get()
    val diff_tensor = diff(Tensor.fromRGB(image), Tensor.fromRGB(editResult))

    def apxColor(a: Array[Double]): List[Int] = a.map(x => x.toInt).toList

    val colorList = diff_tensor.getPixelStream.collect(Collectors.toList())
      .asScala.map(apxColor).filter(_.sum != 0).groupBy(x => x).mapValues(_.size)
      .toList.sortBy(-_._2).take(colors.size).map(_._1).toArray

    val selectionIndexToColorIndex = colors.zipWithIndex.map(tuple => {
      val (color, index) = tuple
      colorList.zipWithIndex.sortBy(x => -dist(color, x._1)).head._2 -> index
    }).toArray.toMap
    val colorsMap = colorList.zipWithIndex.toMap
    (x: Int, y: Int) => colorsMap.get(apxColor(diff_tensor.getPixel(x, y))).flatMap(selectionIndexToColorIndex.get(_))
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

  def uploadMask(content: BufferedImage, colors: Color*)(implicit log: NotebookOutput) = {
    val maskFile = new UploadImageQuery("Upload Mask", log).print().get()
    val maskTensor = Tensor.fromRGB(ImageIO.read(maskFile))

    def apxColor(a: Array[Double]): List[Int] = a.map(x => x.toInt).toList

    val colorList = maskTensor.getPixelStream.collect(Collectors.toList())
      .asScala.map(apxColor).filter(_.sum != 0).groupBy(x => x).mapValues(_.size)
      .toList.sortBy(-_._2).take(colors.size).map(_._1).toArray
    val selectionIndexToColorIndex = colors.zipWithIndex.map(tuple => {
      val (color, index) = tuple
      colorList.zipWithIndex.sortBy(x => -dist(color, x._1)).head._2 -> index
    }).toArray.toMap
    val colorsMap = colorList.zipWithIndex.toMap.mapValues(selectionIndexToColorIndex(_))
    val tensor = Tensor.fromRGB(content)
    val tensors = for (clr <- 0 until colors.size) yield {
      tensor.mapCoords((coordinate: Coordinate) => {
        val Array(x, y, c) = coordinate.getCoords()
        if (colorsMap(apxColor(maskTensor.getPixel(x, y))) == clr) {
          tensor.get(coordinate)
        } else {
          0.0
        }
      })
    }
    tensor.freeRef()
    tensors
  }

  def dist(color: Color, x: Seq[Int]) = {
    List(
      color.getRed - x(2).doubleValue(),
      color.getGreen - x(1).doubleValue(),
      color.getBlue - x(0).doubleValue()
    ).map(x => x * x).sum
  }

  def select(log: NotebookOutput, image: BufferedImage, partitions: Int) = {
    val editResult = new EditImageQuery(log, image).print().get()
    var diff_tensor = diff(Tensor.fromRGB(image), Tensor.fromRGB(editResult))

    def apxColor(a: Array[Double]): List[Int] = a.map(x => x.toInt).toList

    val dimensions = diff_tensor.getDimensions
    val colors: Map[List[Int], Int] = diff_tensor.getPixelStream.collect(Collectors.toList())
      .asScala.map(apxColor).filter(_.sum != 0).groupBy(x => x).mapValues(_.size)
      .toList.sortBy(-_._2).take(partitions).map(_._1).toArray.zipWithIndex.toMap
    (x: Int, y: Int) => colors.get(apxColor(diff_tensor.getPixel(x, y)))
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
