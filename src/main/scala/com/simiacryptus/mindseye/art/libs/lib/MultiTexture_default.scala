package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.texture.MultiTexture
import com.simiacryptus.mindseye.art.libs.{StandardLibraryNotebook, StandardLibraryNotebookLocal}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class MultiTexture_default extends MultiTexture with EmailNotebookWrapper[MultiTexture] with Jsonable[MultiTexture] {
  override def className: String = "MultiTexture"

  override val styleUrls = Array("upload:Style")

  override def inputTimeoutSeconds: Int = Integer.MAX_VALUE

  override val initUrls = Array(
    "50 + noise * 0.5",
    "plasma",
    "50 + noise * 0.5",
    "plasma"
  )

  s3bucket = StandardLibraryNotebookLocal.s3bucket
  override var emailAddress: String = StandardLibraryNotebookLocal.emailAddress
  override def postConfigure(log: NotebookOutput) = withEmailNotifications(super.postConfigure(log))(log)
}
