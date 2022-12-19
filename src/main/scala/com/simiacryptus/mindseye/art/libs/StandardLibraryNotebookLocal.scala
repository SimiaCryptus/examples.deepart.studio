package com.simiacryptus.mindseye.art.libs

import com.simiacryptus.notebook.Jsonable
import com.simiacryptus.sparkbook.LocalAWSNotebookRunner

object StandardLibraryNotebookLocal extends StandardLibraryNotebookLocal {
  def main(args: Array[String]): Unit = _main(args)
}

class StandardLibraryNotebookLocal
  extends StandardLibraryNotebook[StandardLibraryNotebookLocal]
    with LocalAWSNotebookRunner[Object, StandardLibraryNotebookLocal]
    with Jsonable[StandardLibraryNotebookLocal] {
  override def install: Boolean = false
}