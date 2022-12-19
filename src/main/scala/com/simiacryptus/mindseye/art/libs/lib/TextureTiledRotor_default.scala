package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.rotor.TextureTiledRotor
import com.simiacryptus.mindseye.art.libs.{StandardLibraryNotebook, StandardLibraryNotebookLocal}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.EmailNotebookWrapper

class TextureTiledRotor_default extends TextureTiledRotor with EmailNotebookWrapper[TextureTiledRotor] with Jsonable[TextureTiledRotor] {
  override def className: String = "TextureTiledRotor"

  override def inputTimeoutSeconds: Int = Integer.MAX_VALUE

  override val styleUrl: String = "upload:Style"
  override val aspectRatio: Double = 1 / 0.5774
  override val rotationalSegments: Int = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val initUrls: Array[String] = Array(
    "50 + noise * 0.5",
    "plasma",
    "50 + noise * 0.5",
    "plasma"
  )

  s3bucket = StandardLibraryNotebookLocal.s3bucket
  override var emailAddress: String = StandardLibraryNotebookLocal.emailAddress
  override def postConfigure(log: NotebookOutput) = withEmailNotifications(super.postConfigure(log))(log)
}
