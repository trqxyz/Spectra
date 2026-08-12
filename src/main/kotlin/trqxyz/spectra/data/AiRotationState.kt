/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 KaelusAI
 *
 * Spectra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Spectra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * Per-player AI feature computer, ported 1:1 from the training data collector's RotationState.
 * CRITICAL: this must stay byte-for-byte equivalent to the SlothPanel collector, otherwise
 * inference features drift from training features and legit players false-flag near 100% (notably:
 * yaw delta is WRAPPED to [-180,180], which the raw movement pipeline does not do).
 */
class AiRotationState {
  private val xRotMode = RunningMode(TOTAL_SAMPLES_THRESHOLD)
  private val yRotMode = RunningMode(TOTAL_SAMPLES_THRESHOLD)
  private var initialized = false
  private var lastYaw = 0f
  private var lastPitch = 0f
  private var lastDeltaYaw = 0f
  private var lastDeltaPitch = 0f
  private var lastYawAccel = 0f
  private var lastPitchAccel = 0f
  private var currentYawAccel = 0f
  private var currentPitchAccel = 0f
  private var lastXRot = 0.0
  private var lastYRot = 0.0
  private var modeX = 0.0
  private var modeY = 0.0

  fun update(yaw: Float, pitch: Float): TickData {
    if (!initialized) {
      initialized = true
      lastYaw = yaw
      lastPitch = pitch
      return TickData(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    val deltaYaw = wrapDegrees(yaw - lastYaw)
    val deltaPitch = pitch - lastPitch
    val deltaYawAbs = abs(deltaYaw).toDouble()
    val deltaPitchAbs = abs(deltaPitch).toDouble()
    lastYawAccel = currentYawAccel
    lastPitchAccel = currentPitchAccel
    currentYawAccel = (deltaYawAbs - abs(lastDeltaYaw)).toFloat()
    currentPitchAccel = (deltaPitchAbs - abs(lastDeltaPitch)).toFloat()
    val jerkYaw = currentYawAccel - lastYawAccel
    val jerkPitch = currentPitchAccel - lastPitchAccel
    updateModes(deltaYawAbs, deltaPitchAbs)
    val gcdErrorYaw = gcdError(deltaYaw, modeX)
    val gcdErrorPitch = gcdError(deltaPitch, modeY)

    lastYaw = yaw
    lastPitch = pitch
    lastDeltaYaw = deltaYaw
    lastDeltaPitch = deltaPitch

    return TickData(
      deltaYaw,
      deltaPitch,
      currentYawAccel,
      currentPitchAccel,
      jerkYaw,
      jerkPitch,
      gcdErrorYaw,
      gcdErrorPitch,
    )
  }

  private fun updateModes(deltaYawAbs: Double, deltaPitchAbs: Double) {
    val divisorX = gcd(deltaYawAbs, lastXRot)
    if (deltaYawAbs > 0 && deltaYawAbs < 5 && divisorX > MINIMUM_DIVISOR) {
      xRotMode.add(divisorX)
      lastXRot = deltaYawAbs
    }
    val divisorY = gcd(deltaPitchAbs, lastYRot)
    if (deltaPitchAbs > 0 && deltaPitchAbs < 5 && divisorY > MINIMUM_DIVISOR) {
      yRotMode.add(divisorY)
      lastYRot = deltaPitchAbs
    }
    if (xRotMode.size() > SIGNIFICANT_SAMPLES_THRESHOLD) {
      xRotMode.updateMode()
      if (xRotMode.modeCount() > SIGNIFICANT_SAMPLES_THRESHOLD) modeX = xRotMode.modeValue()
    }
    if (yRotMode.size() > SIGNIFICANT_SAMPLES_THRESHOLD) {
      yRotMode.updateMode()
      if (yRotMode.modeCount() > SIGNIFICANT_SAMPLES_THRESHOLD) modeY = yRotMode.modeValue()
    }
  }

  private fun gcdError(delta: Float, mode: Double): Float {
    if (mode <= 0) return 0f
    val error = abs(delta % mode)
    return min(error, mode - error).toFloat()
  }

  private fun gcd(aInput: Double, bInput: Double): Double {
    if (aInput == 0.0) return 0.0
    var a = aInput
    var b = bInput
    if (a < b) {
      val temp = a
      a = b
      b = temp
    }
    while (b > MINIMUM_DIVISOR) {
      val temp = a - floor(a / b) * b
      a = b
      b = temp
    }
    return a
  }

  private fun wrapDegrees(degrees: Float): Float {
    var value = degrees % 360.0f
    if (value >= 180.0f) value -= 360.0f
    if (value < -180.0f) value += 360.0f
    return value
  }

  private class RunningMode(private val maxSize: Int) {
    private val addList = ArrayDeque<Double>(maxSize)
    private val popularityMap = HashMap<Double, Int>()
    private var modeValueField = 0.0
    private var modeCountField = 0

    fun size(): Int = addList.size

    fun add(value: Double) {
      pop()
      for ((key, count) in popularityMap) {
        if (abs(key - value) < THRESHOLD) {
          popularityMap[key] = count + 1
          addList.addLast(key)
          return
        }
      }
      popularityMap[value] = 1
      addList.addLast(value)
    }

    private fun pop() {
      if (addList.size >= maxSize) {
        val type = addList.removeFirstOrNull() ?: return
        val popularity = popularityMap.getOrDefault(type, 0)
        if (popularity <= 1) popularityMap.remove(type) else popularityMap[type] = popularity - 1
      }
    }

    fun updateMode() {
      var max = 0
      var mostPopular = 0.0
      for ((key, count) in popularityMap) {
        if (count > max) {
          max = count
          mostPopular = key
        }
      }
      modeValueField = mostPopular
      modeCountField = max
    }

    fun modeValue(): Double = modeValueField

    fun modeCount(): Int = modeCountField

    companion object {
      private const val THRESHOLD = 1e-3
    }
  }

  companion object {
    private const val SIGNIFICANT_SAMPLES_THRESHOLD = 15
    private const val TOTAL_SAMPLES_THRESHOLD = 80

    // Matches the collector's Math.pow(0.2F, 3.0) * 8.0 * 0.15 - 1e-3 exactly
    // (0.2F promoted to double, not the double literal 0.2).
    private val MINIMUM_DIVISOR = 0.2f.toDouble().pow(3.0) * 8.0 * 0.15 - 1e-3
  }
}
