/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
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
package trqxyz.spectra.damage

import kotlin.math.min
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.player.SpectraPlayer

class AiDamageProcessor(private val configManager: ConfigManager) : DamageProcessor {
  override fun reset(spectraPlayer: SpectraPlayer) {
    spectraPlayer.combat.damageMultiplier = 1.0
  }

  override fun applyProbability(spectraPlayer: SpectraPlayer, probability: Double) {
    if (!configManager.isAiDamageReductionEnabled()) {
      spectraPlayer.combat.damageMultiplier = 1.0
      return
    }

    spectraPlayer.combat.damageMultiplier =
      computeMultiplier(
        probability,
        configManager.aiDamageReductionProb,
        configManager.aiDamageReductionMultiplier,
      )
  }

  companion object {
    internal fun computeMultiplier(
      probability: Double,
      threshold: Double,
      multiplier: Double,
    ): Double {
      if (probability < threshold) return 1.0
      val ratio = (probability - threshold) / (1.0 - threshold)
      val reduction = min(1.0, ratio * multiplier)
      return 1.0 - reduction
    }
  }
}
