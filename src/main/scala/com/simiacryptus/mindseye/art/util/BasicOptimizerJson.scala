package com.simiacryptus.mindseye.art.util

case class BasicOptimizerJson
(
  override val trainingMinutes: Int,
  override val trainingIterations: Int,
  override val maxRate: Double
) extends BasicOptimizer
