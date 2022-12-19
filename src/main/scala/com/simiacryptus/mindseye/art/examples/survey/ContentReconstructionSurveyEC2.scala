package com.simiacryptus.mindseye.art.examples.survey

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.art.examples.styled.SegmentStyleEC2
import com.simiacryptus.mindseye.lang.cudnn.CudaMemory
import com.simiacryptus.notebook.Jsonable
import com.simiacryptus.sparkbook.{AWSNotebookRunner, EC2Runner}

import java.net.URI
import java.util.UUID

object ContentReconstructionSurveyEC2 extends ContentReconstructionSurveyEC2; class ContentReconstructionSurveyEC2 extends ContentReconstructionSurvey[ContentReconstructionSurveyEC2] with EC2Runner[Object] with AWSNotebookRunner[Object, ContentReconstructionSurveyEC2] with Jsonable[ContentReconstructionSurveyEC2] {

  override def nodeSettings: EC2NodeSettings = EC2NodeSettings.P3_2XL

  override def maxHeap: Option[String] = Option("50g")

  override def className: String = "ContentReconstructionSurvey"

  override def javaProperties: Map[String, String] = super.javaProperties ++ Map(
    "MAX_TOTAL_MEMORY" -> (15 * CudaMemory.GiB).toString,
    "MAX_DEVICE_MEMORY" -> (15 * CudaMemory.GiB).toString
  )
}
