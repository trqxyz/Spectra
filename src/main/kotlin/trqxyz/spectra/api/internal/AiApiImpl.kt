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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.api.internal

import java.util.Optional
import java.util.UUID
import trqxyz.spectra.ai.AiService
import trqxyz.spectra.api.model.AiSnapshot
import trqxyz.spectra.api.service.AiApi
import trqxyz.spectra.checks.impl.ai.AiCheck
import trqxyz.spectra.player.PlayerDataManager

class AiApiImpl(
  private val aiService: AiService,
  private val playerDataManager: PlayerDataManager,
) : AiApi {
  override fun isEnabled(): Boolean = aiService.isEnabled

  override fun getSnapshot(playerId: UUID): Optional<AiSnapshot> {
    val spectraPlayer = playerDataManager.getPlayer(playerId) ?: return Optional.empty()
    val aiCheck =
      spectraPlayer.checkManager.getCheck(AiCheck::class.java) ?: return Optional.empty()
    return Optional.of(
      AiSnapshot(
        aiCheck.lastProbability,
        aiCheck.buffer,
        spectraPlayer.combat.damageMultiplier,
        aiCheck.prob90,
      )
    )
  }
}
