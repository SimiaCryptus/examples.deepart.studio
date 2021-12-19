package com.simiacryptus.mindseye.art.examples.zoomrotor

import com.simiacryptus.sparkbook.aws.P3_2XL

object ZoomingRotorEC2 extends ZoomingRotor with P3_2XL {

  override val s3bucket: String = reportingBucket

}
