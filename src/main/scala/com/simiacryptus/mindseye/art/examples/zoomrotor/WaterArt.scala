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

package com.simiacryptus.mindseye.art.examples.zoomrotor

trait WaterArt[U <: ArtSource[U]] extends ArtSource[U] {
  override val border: Double = 0.0
  override val magnification = Array(4.0)
  override val styles: Array[String] = Array(
    "http://examples.deepartist.org/TextureTiledRotor/f5fd9f51-02a1-4da1-ba5a-6968b9fd9521/etc/background-biology-blue-1426718.jpg"
  )
  override val keyframes = Array(
    "http://examples.deepartist.org/TextureTiledRotor/f5fd9f51-02a1-4da1-ba5a-6968b9fd9521/etc/image_d49187c054b3a94b.jpg"
  )
}


















