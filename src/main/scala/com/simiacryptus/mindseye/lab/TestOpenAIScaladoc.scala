package com.simiacryptus.mindseye.lab

import org.apache.commons.io.FileUtils

import java.io.File
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

object TestOpenAIScaladoc {
  def main(args: Array[String]): Unit = {
    val root = new File("C:\\Users\\andre\\code\\all-projects\\mindseye\\mindseye-core")
    val language = "scala"
    val files = FileUtils.listFiles(root, Array(language), true).asScala
    for (file <- files.take(1)) {
      val data = FileUtils.readFileToString(file, "UTF-8")

      import scala.meta._
      val tree: Source = data.parse[Source].get
      println(tree)

      val newTree = tree.transform({
        case x => x
      })

    }
  }
}
