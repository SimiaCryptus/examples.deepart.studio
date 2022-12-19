package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.zoomrotor.ZoomingRotor
import com.simiacryptus.mindseye.art.libs.{StandardLibraryNotebook, StandardLibraryNotebookLocal}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class ZoomingRotor_default extends ZoomingRotor with EmailNotebookWrapper[ZoomingRotor] with Jsonable[ZoomingRotor] {
  override def className: String = "ZoomingRotor"

  override val rotationalSegments: Int = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val keyframes: Array[String] = Array.empty

  s3bucket = StandardLibraryNotebookLocal.s3bucket
  override var emailAddress: String = StandardLibraryNotebookLocal.emailAddress
  override def postConfigure(log: NotebookOutput) = withEmailNotifications(super.postConfigure(log))(log)
}
