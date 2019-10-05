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
import java.util
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.{Collectors, DoubleStream, IntStream}
import java.util.zip.ZipFile

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops.{ContentMatcher, GramMatrixEnhancer, MomentMatcher}
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
import com.simiacryptus.sparkbook.NotebookRunner.withMonitoredJpg
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.util.Util

import scala.collection.JavaConverters._
import scala.collection.mutable

object SegmentStyle extends SegmentStyle with LocalRunner[Object] with NotebookRunner[Object]

class SegmentStyle extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val s3bucket: String = ""
  val partitioningResolution = 600
  val styleUrl = "upload:Style"
  val initUrl: String = "upload:Content"
  val minResolution = 600
  val maxResolution = 1200
  val magnification = 2
  val steps = 2

  override def indexStr = "401"

  override def description = <div>
    Demonstrates application of style transfer to a masked region identified by user scribble
  </div>.toString.trim

  override def inputTimeoutSeconds = 1

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input images (user upload prompts) and display a rescaled copies
      val (foreground: Tensor, background: Tensor) = log.subreport("Partition_Input", (subreport: NotebookOutput) => {
        var image = ImageArtUtil.load(subreport, contentUrl, partitioningResolution)
        val Seq(foreground, background) = partition(image, 2)(subreport)
        subreport.p(subreport.jpg(foreground.toImage, "Selection mask"))
        (foreground, background)
      })
      val styleImage = log.eval(()=>{
        ImageArtUtil.load(log, styleUrl, 600)
      })
      val canvas = new AtomicReference[Tensor](null)
      withMonitoredJpg(() => canvas.get().toImage) {
        paint(contentUrl, content => {
          smoothStyle(content = content,
            style = Tensor.fromRGB(styleImage),
            mask = MomentMatcher.toMask(foreground.addRef()))
        }, canvas, new VisualStyleContentNetwork(
          styleLayers = List(
            VGG16.VGG16_0b,
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
          ).map(_.withMask(foreground)),
          styleUrl = List(styleUrl),
          contentLayers = List(
            VGG16.VGG16_1b2
          ),
          contentModifiers = List(
            new ContentMatcher()
          ),
          magnification = magnification
        ),
          new BasicOptimizer {
            override val trainingMinutes: Int = 180
            override val trainingIterations: Int = 30
            override val maxRate = 1e9
          }, new GeometricSequence {
            override val min: Double = minResolution
            override val max: Double = maxResolution
            override val steps = SegmentStyle.this.steps
          }.toStream.map(_.round.toDouble))
      }
      null
    }
  }()

  def solver: SmoothSolver = new SmoothSolver_Cuda()

  def smoothStyle(content: Tensor, style: Tensor, mask: Tensor)(implicit log: NotebookOutput) = {
    val foregroundDensity = DoubleStream.of(mask.getData: _*).average().getAsDouble
    val fastPhotoStyleTransfer = FastPhotoStyleTransfer.fromZip(new ZipFile(Util.cacheFile(new URI(
      "https://simiacryptus.s3-us-west-2.amazonaws.com/photo_wct.zip"))))
    val wctRestyled = fastPhotoStyleTransfer.photoWCT(style, content, foregroundDensity, 1.0)
    val topology = new SearchRadiusTopology(content).setSelfRef(true).setVerbose(true)
    var affinity = new RelativeAffinity(content, topology).setContrast(20).setGraphPower1(2).setMixing(0.1)
      .wrap((graphEdges: util.List[Array[Int]], innerResult: util.List[Array[Double]]) => adjust(graphEdges, innerResult, degree(innerResult), 0.2))
    val smoothed = solver.solve(topology, affinity, 1e-4).apply(wctRestyled)
    smoothed.mapCoordsAndFree((c: Coordinate) => {
      val bg = mask.get(c)
      if (bg == 1) smoothed.get(c)
      else content.get(c)
    })
  }

  def partition(image: BufferedImage, partitions: Int)(implicit log: NotebookOutput) = {
    val image_tensor: Tensor = Tensor.fromRGB(image)
    val dimensions: Array[Int] = image_tensor.getDimensions
    val pixels: Int = dimensions(0) * dimensions(1)
    val topology_simple = new SimpleRasterTopology(image_tensor.getDimensions).cached()

    log.h1("Image Region Decomposition")
    log.h2("Flattened Image")
    //val topology = new SimpleRasterTopology(image_tensor.getDimensions).cached()
    val topology = new SearchRadiusTopology(image_tensor)
      .setInitialRadius(2)
      .setMaxChromaDist(0.1)
      .setNeighborhoodSize(6)
      .setSelfRef(true)
      .setVerbose(true)
      .cached()
    val affinity = new RelativeAffinity(image_tensor, topology)
      .setContrast(50)
      .setGraphPower1(2)
      .setMixing(0.2)
    val flattenedColors = log.eval(() => {
      SegmentUtil.flattenColors(image_tensor,
        topology,
        affinity.wrap((graphEdges: util.List[Array[Int]], innerResult: util.List[Array[Double]]) =>
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

    val selection = select(log, image, partitions)
    var seedMarks: Map[Int, Int] = (for (
      x <- 0 until dimensions(0);
      y <- 0 until dimensions(1);
      color <- selection(x, y)
    ) yield {
      pixelMap(topology_simple.getIndexFromCoords(x, y)) -> color
    }).groupBy(_._1).mapValues(_.head._2)

    seedMarks = log.eval(() => {
      RegionAssembler.epidemic(graph, pixelMap, image_tensor, topology_simple,
        seedMarks.map(t => t._1.asInstanceOf[Integer] -> t._2.asInstanceOf[Integer]).asJava
      ).reduceTo(1).regions.asScala.flatMap(region =>
        region.original_regions.asScala.map(_.toInt -> region.marks)
      ).toMap.filter(_._2.size() == 1).mapValues(_.asScala.head.toInt)
    })

    for (clr <- 0 until partitions) yield {
      image_tensor.mapCoords((coordinate: Coordinate) => {
        val coords = coordinate.getCoords()
        if (seedMarks.get(pixelMap(topology_simple.getIndexFromCoords(coords(0), coords(1)))).map(_ == clr).getOrElse(false)) {
          image_tensor.get(coordinate)
        } else {
          0.0
        }
      })
    }
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
