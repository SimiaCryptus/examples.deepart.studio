package com.simiacryptus.mindseye.art.examples.styled

import com.simiacryptus.mindseye.art.photo.{SmoothSolver, SmoothSolver_EJML}
import com.simiacryptus.notebook.Jsonable
import com.simiacryptus.sparkbook.aws.P3_2XL

object SmoothStyleEC2 extends SmoothStyleEC2 {

}

class SmoothStyleEC2 extends SmoothStyle[SmoothStyleEC2] with P3_2XL[Object, SmoothStyleEC2] with Jsonable[SmoothStyleEC2] {


  override def className: String = "SmoothStyle"

  override def solver: SmoothSolver = new SmoothSolver_EJML()

}
