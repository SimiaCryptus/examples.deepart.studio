package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.texture.BigTexture
import com.simiacryptus.mindseye.art.libs.{StandardLibraryNotebook, StandardLibraryNotebookLocal}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class BigTexture_default extends BigTexture with EmailNotebookWrapper[BigTexture] with Jsonable[BigTexture] {
  override def className: String = "BigTexture"
  override def inputTimeoutSeconds: Int = Integer.MAX_VALUE

  override val styleUrl: String = "upload:Style"
  override val initUrl: String = "plasma"
  override val aspectRatio: Double = 0.5774
  override val minRes: Int = 200

  s3bucket = StandardLibraryNotebookLocal.s3bucket
  override var emailAddress: String = StandardLibraryNotebookLocal.emailAddress
  override def postConfigure(log: NotebookOutput) = withEmailNotifications(super.postConfigure(log))(log)
}
