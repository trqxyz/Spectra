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

import kotlin.math.roundToLong

/**
 * One movement sample = the 8 aim channels the AI consumes. These are produced by [AiRotationState]
 * with EXACTLY the same maths as the training data collector (wrapped yaw delta,
 * |delta|-|lastDelta| accel, gcd-mode error), so inference features match training features.
 */
class TickData(
  val deltaYaw: Float,
  val deltaPitch: Float,
  val accelYaw: Float,
  val accelPitch: Float,
  val jerkYaw: Float,
  val jerkPitch: Float,
  val gcdErrorYaw: Float,
  val gcdErrorPitch: Float,
) {
  /** True when this sample contains an actual rotation-derived feature. */
  val isInformative: Boolean
    get() =
      deltaYaw != 0f ||
        deltaPitch != 0f ||
        accelYaw != 0f ||
        accelPitch != 0f ||
        jerkYaw != 0f ||
        jerkPitch != 0f ||
        gcdErrorYaw != 0f ||
        gcdErrorPitch != 0f

  fun toCsv(status: String): String {
    return buildString { appendCsv(this, status) }
  }

  fun appendCsv(out: Appendable, status: String) {
    val cheatingStatus = if (status.equals("CHEAT", ignoreCase = true)) 1 else 0
    out.append(if (cheatingStatus == 1) '1' else '0')
    out.append(',')
    appendFixed6(out, deltaYaw)
    out.append(',')
    appendFixed6(out, deltaPitch)
    out.append(',')
    appendFixed6(out, accelYaw)
    out.append(',')
    appendFixed6(out, accelPitch)
    out.append(',')
    appendFixed6(out, jerkYaw)
    out.append(',')
    appendFixed6(out, jerkPitch)
    out.append(',')
    appendFixed6(out, gcdErrorYaw)
    out.append(',')
    appendFixed6(out, gcdErrorPitch)
  }

  companion object {
    @JvmStatic
    fun getHeader(): String {
      return "is_cheating,delta_yaw,delta_pitch,accel_yaw,accel_pitch,jerk_yaw,jerk_pitch," +
        "gcd_error_yaw,gcd_error_pitch"
    }

    private const val SCALE = 1_000_000L

    private fun appendFixed6(out: Appendable, value: Float) {
      if (!value.isFinite()) {
        out.append("0.000000")
        return
      }

      var v = value.toDouble()
      val negative = v < 0.0
      if (negative) {
        v = -v
      }
      val scaled = (v * SCALE).roundToLong()
      val integerPart = scaled / SCALE
      val fractionPart = (scaled % SCALE).toInt()

      if (negative) {
        out.append('-')
      }
      out.append(integerPart.toString())
      out.append('.')
      val fractionText = fractionPart.toString()
      for (i in fractionText.length until 6) {
        out.append('0')
      }
      out.append(fractionText)
    }
  }
}
