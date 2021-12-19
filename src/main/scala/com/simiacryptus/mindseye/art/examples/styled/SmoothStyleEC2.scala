package com.simiacryptus.mindseye.art.examples.styled

import com.simiacryptus.mindseye.art.photo.{SmoothSolver, SmoothSolver_EJML}
import com.simiacryptus.sparkbook.aws.P3_2XL

object SmoothStyleEC2 extends SmoothStyle with P3_2XL {
  override val s3bucket: String = "test.deepartist.org"

  override def className: String = "SmoothStyle"

  override def solver: SmoothSolver = new SmoothSolver_EJML()
}
