/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
 *
 * This file contains code derived from GrimAC.
 * The original authors of GrimAC are credited below.
 *
 * Copyright (c) 2021-2026 GrimAC, DefineOutside and contributors.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.checks.impl.combat

import trqxyz.spectra.checks.AbstractCheck
import trqxyz.spectra.checks.CheckData
import trqxyz.spectra.checks.CheckFactory
import trqxyz.spectra.checks.type.RotationCheck
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.utils.lists.RunningMode
import trqxyz.spectra.utils.math.SpectraMath
import trqxyz.spectra.utils.update.RotationUpdate

@CheckData(name = "AimProcessor_Internal")
class AimProcessor(spectraPlayer: SpectraPlayer) : AbstractCheck(spectraPlayer), RotationCheck {
  var sensitivityX: Double = 0.0
  var sensitivityY: Double = 0.0
  var divisorX: Double = 0.0
  var divisorY: Double = 0.0
  var modeX: Double = 0.0
  var modeY: Double = 0.0
  var deltaDotsX: Double = 0.0
  var deltaDotsY: Double = 0.0
  private val xRotMode = RunningMode(TOTAL_SAMPLES_THRESHOLD)
  private val yRotMode = RunningMode(TOTAL_SAMPLES_THRESHOLD)
  private var lastXRot = 0.0
  private var lastYRot = 0.0

  private var lastDeltaYaw = 0.0f
  private var lastDeltaPitch = 0.0f

  var lastYawAccel = 0.0f
    private set

  var lastPitchAccel = 0.0f
    private set

  var currentYawAccel = 0.0f
    private set

  var currentPitchAccel = 0.0f
    private set

  interface Factory : CheckFactory {
    override fun create(player: SpectraPlayer): AimProcessor
  }

  override fun process(rotationUpdate: RotationUpdate) {
    val deltaYaw = rotationUpdate.deltaYaw
    val deltaPitch = rotationUpdate.deltaPitch
    val deltaYawAbs = kotlin.math.abs(deltaYaw).toDouble()
    val deltaPitchAbs = kotlin.math.abs(deltaPitch).toDouble()
    lastYawAccel = currentYawAccel
    lastPitchAccel = currentPitchAccel
    currentYawAccel = (deltaYawAbs - kotlin.math.abs(lastDeltaYaw)).toFloat()
    currentPitchAccel = (deltaPitchAbs - kotlin.math.abs(lastDeltaPitch)).toFloat()
    lastDeltaYaw = deltaYaw
    lastDeltaPitch = deltaPitch

    divisorX = SpectraMath.gcd(deltaYawAbs, lastXRot)
    if (deltaYawAbs > 0 && deltaYawAbs < 5 && divisorX > SpectraMath.getMinimumDivisor()) {
      xRotMode.add(divisorX)
      lastXRot = deltaYawAbs
    }

    divisorY = SpectraMath.gcd(deltaPitchAbs, lastYRot)
    if (deltaPitchAbs > 0 && deltaPitchAbs < 5 && divisorY > SpectraMath.getMinimumDivisor()) {
      yRotMode.add(divisorY)
      lastYRot = deltaPitchAbs
    }

    if (xRotMode.size() > SIGNIFICANT_SAMPLES_THRESHOLD) {
      xRotMode.updateMode()
      if (xRotMode.modeCount > SIGNIFICANT_SAMPLES_THRESHOLD) {
        modeX = xRotMode.modeValue
        sensitivityX = convertToSensitivity(modeX)
      }
    }

    if (yRotMode.size() > SIGNIFICANT_SAMPLES_THRESHOLD) {
      yRotMode.updateMode()
      if (yRotMode.modeCount > SIGNIFICANT_SAMPLES_THRESHOLD) {
        modeY = yRotMode.modeValue
        sensitivityY = convertToSensitivity(modeY)
      }
    }

    if (modeX > 0) {
      deltaDotsX = deltaYawAbs / modeX
    }
    if (modeY > 0) {
      deltaDotsY = deltaPitchAbs / modeY
    }
  }

  companion object {
    private const val SIGNIFICANT_SAMPLES_THRESHOLD = 15
    private const val TOTAL_SAMPLES_THRESHOLD = 80

    fun convertToSensitivity(value: Double): Double {
      val normalized = value / 0.15F / 8.0
      val cubic = Math.cbrt(normalized)
      return (cubic - 0.2f) / 0.6f
    }
  }
}
