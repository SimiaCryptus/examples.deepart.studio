package com.simiacryptus.mindseye.art.examples.styled

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.art.photo.{SmoothSolver, SmoothSolver_EJML}
import com.simiacryptus.mindseye.lang.cudnn.CudaMemory
import com.simiacryptus.sparkbook.{AWSNotebookRunner, EC2Runner}

object SegmentStyleEC2 extends SegmentStyle with EC2Runner[Object] with AWSNotebookRunner[Object] {
  override val s3bucket: String = "test.deepartist.org"

  override def solver: SmoothSolver = new SmoothSolver_EJML()

  override def nodeSettings: EC2NodeSettings = EC2NodeSettings.P3_2XL

  override def maxHeap: Option[String] = Option("50g")

  override def className: String = "SegmentStyle"

  override def javaProperties: Map[String, String] = super.javaProperties ++ Map(
    "MAX_TOTAL_MEMORY" -> (15 * CudaMemory.GiB).toString,
    "MAX_DEVICE_MEMORY" -> (15 * CudaMemory.GiB).toString
  )
}
