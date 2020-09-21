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

package com.simiacryptus.mindseye.art.style

import java.awt.image.BufferedImage
import java.net.URI

import com.simiacryptus.mindseye.art.TiledTrainable
import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.photo.{SmoothSolver, SmoothSolver_EJML}
import com.simiacryptus.mindseye.art.photo.affinity.RelativeAffinity
import com.simiacryptus.mindseye.art.photo.cuda.SmoothSolver_Cuda
import com.simiacryptus.mindseye.art.photo.topology.SearchRadiusTopology
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.eval.ArrayTrainable
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.cudnn._
import com.simiacryptus.mindseye.layers.cudnn.conv.SimpleConvolutionLayer
import com.simiacryptus.mindseye.layers.java.BoundedActivationLayer
import com.simiacryptus.mindseye.network.PipelineNetwork
import com.simiacryptus.mindseye.opt.IterativeTrainer
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.LocalRunner


object Posterize extends Posterize with LocalRunner[Object] with NotebookRunner[Object] {
  override def http_port: Int = 1081
}

class Posterize extends ArtSetup[Object] {

  val contentUrl = "upload:Image"
  val s3bucket: String = ""
  val useCuda = false
  val tile_size = 800
  val tile_padding = 64

  override def indexStr = "301"

  override def description = <div>
    High quality content smoothing via iterative connectivity matrix methods
  </div>.toString.trim

  override def inputTimeoutSeconds = 1


  def solver: SmoothSolver = if(useCuda) new SmoothSolver_Cuda() else new SmoothSolver_EJML()
  override def postConfigure(log: NotebookOutput) = {
    implicit val implicitLog = log
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    var fullContent = ImageArtUtil.getTensor(log, contentUrl)
    //fullContent = Tensor.fromRGB(ImageUtil.resize(fullContent.toRgbImage, 1400, true))
    log.p(log.jpg(fullContent.toRgbImage, "Input Content"))

    val registration = registerWithIndexJPG(() => fullContent)
    try {

      val smallContent = Tensor.fromRGB(ImageUtil.resize(fullContent.toRgbImage, tile_size, true))
      log.out(log.jpg(smallContent.toRgbImage, "Small Image"))
      var recolored = recolor(smallContent)(smallContent.addRef())
      log.out(log.jpg(recolored.toRgbImage, "Recolored Small Image"))

      for(width <- new GeometricSequence {
        override val min: Double = tile_size
        override val max: Double = fullContent.getDimensions()(0)
        override val steps = 3
      }.toStream.drop(1).map(_.toInt)) {
        val content = Tensor.fromRGB(ImageUtil.resize(fullContent.toRgbImage, width, true))
        val height = content.getDimensions()(1)
        val selectors_fade = TiledTrainable.selectors(tile_padding, width, height, tile_size, true)
        val selectors_sharp = TiledTrainable.selectors(tile_padding, width, height, tile_size, false)

        val enlarged = Tensor.fromRGB(ImageUtil.resize(recolored.toRgbImage, width, height))
        val tiles = for ((tileView_sharp, tileView_fade) <- selectors_sharp.zip(selectors_fade)) yield {
          var recoloredTile = tileView_sharp.eval(enlarged.addRef()).getData.get(0)
          val contentTile = tileView_sharp.eval(content).getData.get(0)
          log.out(log.jpg(recoloredTile.toRgbImage, "Coarse Recoloring Tile"))
          log.out(log.jpg(contentTile.toRgbImage, "Content Tile"))
          recoloredTile = recolor(contentTile)(recoloredTile)
          log.out(log.jpg(recoloredTile.toRgbImage, "Recolored Tile"))
          val maskTile = tileView_fade.eval(enlarged.map(x => 1)).getData.get(0)
          log.out(log.jpg(maskTile.toRgbImage, "Tile Mask"))
          val product = recoloredTile.mapCoords(c => recoloredTile.get(c) * maskTile.get(c))
          log.out(log.jpg(product.toRgbImage, "Tile Product"))
          product
        }
        recolored = reassemble(enlarged, selectors_fade, tiles, log)
      }

      null
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }

  private def recolor(source: Tensor)(target: Tensor) = {
    val topology = new SearchRadiusTopology(source.addRef())
    topology.setSelfRef(true)
    topology.setVerbose(true)
    var affinity = new RelativeAffinity(source, topology)
    affinity.setContrast(20)
    affinity.setGraphPower1(2)
    affinity.setMixing(0.1)
    //val wrapper = affinity.wrap((graphEdges, innerResult) => adjust(graphEdges, innerResult, degree(innerResult), 0.2))
    val operator = solver.solve(topology, affinity, 1e-4)
    val recoloredTensor = operator.apply(target)
    operator.freeRef()
    recoloredTensor
  }

  private def reassemble(contentTensor: Tensor, selectors: Array[Layer], tiles: Array[Tensor], log: NotebookOutput) = {
    val reassemblyNetwork = new PipelineNetwork(1 + tiles.size)
    reassemblyNetwork.add(new SumInputsLayer(), (
      for ((selector, index) <- selectors.zipWithIndex) yield {
        reassemblyNetwork.add(
          lossFunction(selector.addRef().asInstanceOf[Layer], () => new PipelineNetwork(1)),
          reassemblyNetwork.getInput(index + 1),
          reassemblyNetwork.getInput(0))
      }): _*).freeRef()
    val trainable = new ArrayTrainable(reassemblyNetwork, 1)
    trainable.setTrainingData(Array(Array(contentTensor.addRef()) ++ tiles.map(_.addRef())))
    trainable.setMask(Array(true) ++ tiles.map(_ => false): _*)
    val trainer = new IterativeTrainer(trainable)
    trainer.setMaxIterations(100)
    trainer.run()
    log.out(log.jpg(contentTensor.toRgbImage, "Combined Image"))
    contentTensor
  }

  def lossFunction(colorMappingLayer: Layer, summarizer: ()=>Layer) = {
    val network = new PipelineNetwork(2)
    network.add(
      new AvgReducerLayer(),
      network.add(
        new SquareActivationLayer(),
        network.add(
          new BinarySumLayer(1.0, -1.0),
          network.add(
            summarizer(),
            network.getInput(0)),
          network.add(
            summarizer(),
            network.add(
              colorMappingLayer,
              network.getInput(1)
            ))
        ))
    ).freeRef()
    network
  }



  def getCanvas(canvas: RefAtomicReference[Tensor], content:BufferedImage, log: NotebookOutput) = {
    var originalCanvas = canvas.get()
    val width = content.getWidth
    val height = content.getHeight
    if (originalCanvas == null) {
      originalCanvas = Tensor.fromRGB(ImageArtUtil.loadImage(log, contentUrl, width, height))
      canvas.set(originalCanvas)
    } else if (originalCanvas.getDimensions()(0) != width) {
      originalCanvas = Tensor.fromRGB(ImageUtil.resize(originalCanvas.toRgbImage, width, height))
      canvas.set(originalCanvas)
    }
    originalCanvas
  }


}
