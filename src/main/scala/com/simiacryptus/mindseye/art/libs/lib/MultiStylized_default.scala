package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.styled.MultiStylized
import com.simiacryptus.mindseye.art.libs.{StandardLibraryNotebook, StandardLibraryNotebookLocal}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class MultiStylized_default extends MultiStylized with EmailNotebookWrapper[MultiStylized] with Jsonable[MultiStylized] {
  override def className: String = "MultiStylized"

  override val styleUrls = Array("upload:Style")
  override val contentUrl: String = "upload:Content"
  override def inputTimeoutSeconds: Int = Integer.MAX_VALUE

  s3bucket = StandardLibraryNotebookLocal.s3bucket
  override var emailAddress: String = StandardLibraryNotebookLocal.emailAddress
  override def postConfigure(log: NotebookOutput) = withEmailNotifications(super.postConfigure(log))(log)
}
