package com.simiacryptus.mindseye.art.libs.lib

import com.simiacryptus.mindseye.art.examples.texture.BigTextureTiles_Simple_Stage
import com.simiacryptus.mindseye.art.libs.StandardLibraryNotebookLocal
import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.util.VisionLayerListJson
import com.simiacryptus.sparkbook.{EmailNotebookWrapper, NotebookRunner}
import com.simiacryptus.sparkbook.util.LocalRunner

class BigTextureTiles_dev extends BigTextureTiles_finish {

  override def inputTimeoutSeconds: Int = 0

  override val stages: Array[BigTextureTiles_Simple_Stage] = Array(
    //    BigTextureTiles_Simple_Stage(
    //      minRes = 2400,
    //      maxRes = 2400,
    //      resSteps = 1,
    //      enhancementScale = 1e1,
    //      trainingIterations = 100,
    //      maxRate = 1e6,
    //      styleUrls = Array(
    //        "file:///C:/Users/andre/Downloads/waiting_for_the_stage_2014.79.36.jpg"
    //      ),
    //      initUrl = "file:///C:/Users/andre/Pictures/dali/DALL·E 2022-07-15 09.13.png"
    //    ),
    BigTextureTiles_Simple_Stage(
      minRes = 4800,
      maxRes = 4800,
      resSteps = 1,
      enhancementScale = 1e2,
      enhancementLimit = 1e0,
      trainingIterations = 100,
      maxRate = 1e6,
      styleUrls = Array(
        "file:///C:/Users/andre/Downloads/waiting_for_the_stage_2014.79.36.jpg"
      ),
      initUrl = "file:///C:/Users/andre/code/all-projects/report/StandardLibraryNotebook/37bbcdc6-0958-4a29-a6db-36eb4ecc68ea/etc/image_67f1fe6e1729bcc0.jpg",
      styleLayers = new VisionLayerListJson(
        VGG16.VGG16_1b1,
        VGG16.VGG16_1b2,
        VGG16.VGG16_1c1
      )
    )
  )
}
