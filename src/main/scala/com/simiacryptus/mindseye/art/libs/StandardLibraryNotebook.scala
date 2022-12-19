/*
 * Copyright (c) 2020 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.art.libs

import com.simiacryptus.mindseye.art.examples.rotor.TextureTiledRotor
import com.simiacryptus.mindseye.art.examples.styled.MultiStylized
import com.simiacryptus.mindseye.art.examples.texture.{BigTexture, BigTextureTiles, BigTextureTiles_Simple, MultiTexture}
import com.simiacryptus.mindseye.art.examples.zoomrotor.ZoomingRotor
import com.simiacryptus.mindseye.art.libs.lib._
import com.simiacryptus.mindseye.art.util.ImageOptimizer
import com.simiacryptus.mindseye.lang.Layer
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.aws.{P2_XL, P3_2XL}
import com.simiacryptus.sparkbook.util.LocalRunner
import com.simiacryptus.sparkbook.{InteractiveSetup, LocalAWSNotebookRunner, NotebookRunner}

abstract class StandardLibraryNotebook[T<:StandardLibraryNotebook[T]] extends LibraryNotebook[T] {

  override def inputTimeoutSeconds: Int = Integer.MAX_VALUE

  def applications: Map[String, List[ApplicationInfo[_ <: InteractiveSetup[_, _]]]] = Map(
    "Textures" -> List(
      ApplicationInfo[MultiTexture](
        "Multiple Textures", Map(
          "default" -> classOf[MultiTexture_default]
        )),
      ApplicationInfo[BigTexture](
        "Single Texture", Map(
          "default" -> classOf[BigTexture_default]
        )),
      ApplicationInfo[BigTextureTiles_Simple](
        "Enhance Texture Resolution", Map(
          "default" -> classOf[BigTextureTiles_finish],
//          "dev" -> classOf[BigTextureTiles_dev]
        )),
      ApplicationInfo[TextureTiledRotor](
        "Rotationally Symmetric Texture", Map(
          "default" -> classOf[TextureTiledRotor_default]
        )),
      ApplicationInfo[ZoomingRotor](
        "Zoom-In Animation Loop", Map(
          "default" -> classOf[ZoomingRotor_default],
//          "dev" -> classOf[ZoomingRotor_dev]
        ))
    ),
    "Stylized" -> List(
      ApplicationInfo[MultiStylized](
        "Multiple Stylizations", Map(
          "default" -> classOf[MultiStylized_default]
        ))
    )
  )
}
