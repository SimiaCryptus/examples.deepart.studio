package com.simiacryptus.mindseye.art.examples.styled

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.art.photo.{SmoothSolver, SmoothSolver_EJML}
import com.simiacryptus.mindseye.lang.cudnn.CudaMemory
import com.simiacryptus.notebook.Jsonable
import com.simiacryptus.sparkbook.{AWSNotebookRunner, EC2Runner}

import java.net.URI
import java.util.UUID

object SegmentStyleEC2 extends SegmentStyleEC2; class SegmentStyleEC2 extends SegmentStyle[SegmentStyleEC2]
  with EC2Runner[Object]
  with AWSNotebookRunner[Object, SegmentStyleEC2] with Jsonable[SegmentStyleEC2]
  {

  override def solver: SmoothSolver = new SmoothSolver_EJML()

  override def nodeSettings: EC2NodeSettings = EC2NodeSettings.P3_2XL

  override def maxHeap: Option[String] = Option("50g")

  override def className: String = "SegmentStyle"

  override def javaProperties: Map[String, String] = super.javaProperties ++ Map(
    "MAX_TOTAL_MEMORY" -> (15 * CudaMemory.GiB).toString,
    "MAX_DEVICE_MEMORY" -> (15 * CudaMemory.GiB).toString
  )
}
