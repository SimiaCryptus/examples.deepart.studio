package com.simiacryptus.mindseye.art.examples.rotor

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.lang.cudnn.{CudaMemory, Precision}
import com.simiacryptus.notebook.Jsonable
import com.simiacryptus.sparkbook.{AWSNotebookRunner, EC2Runner}

import java.net.URI
import java.util.UUID

object AnimatedRotorEC2 extends AnimatedRotorEC2; class AnimatedRotorEC2 extends AnimatedRotor[AnimatedRotorEC2] with EC2Runner[Object] with AWSNotebookRunner[Object, AnimatedRotorEC2] with Jsonable[AnimatedRotorEC2] {

  override def nodeSettings: EC2NodeSettings = EC2NodeSettings.P3_2XL

  override def maxHeap: Option[String] = Option("50g")

  override def className: String = "AnimatedRotor"

  override def javaProperties: Map[String, String] = super.javaProperties ++ Map(
    "MAX_TOTAL_MEMORY" -> (10 * CudaMemory.GiB).toString,
    "MAX_DEVICE_MEMORY" -> (10 * CudaMemory.GiB).toString,
    "CUDA_DEFAULT_PRECISION" -> Precision.Float.name,
    "MAX_FILTER_ELEMENTS" -> (256 * CudaMemory.MiB).toString,
    "MAX_IO_ELEMENTS" -> (256 * CudaMemory.MiB).toString
  )
}
