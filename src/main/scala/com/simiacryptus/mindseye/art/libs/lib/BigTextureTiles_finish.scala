package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.texture.BigTextureTiles_Simple
import com.simiacryptus.mindseye.art.libs.StandardLibraryNotebookLocal
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class BigTextureTiles_finish extends BigTextureTiles_Simple with EmailNotebookWrapper[BigTextureTiles_Simple] with Jsonable[BigTextureTiles_Simple] {
  override def className: String = "BigTextureTiles"
  override def inputTimeoutSeconds: Int = Integer.MAX_VALUE

  s3bucket = StandardLibraryNotebookLocal.s3bucket
  override var emailAddress: String = StandardLibraryNotebookLocal.emailAddress
  override def postConfigure(log: NotebookOutput) = withEmailNotifications(super.postConfigure(log))(log)
}
