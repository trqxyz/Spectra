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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.api.internal

import java.util.Optional
import java.util.UUID
import trqxyz.spectra.api.model.MonitorSnapshot
import trqxyz.spectra.api.service.MonitorApi
import trqxyz.spectra.checks.impl.ai.AiCheck
import trqxyz.spectra.player.PlayerDataManager

class MonitorApiImpl(private val playerDataManager: PlayerDataManager) : MonitorApi {
  override fun getSnapshot(playerId: UUID): Optional<MonitorSnapshot> {
    val spectraPlayer = playerDataManager.getPlayer(playerId) ?: return Optional.empty()
    val aiCheck =
      spectraPlayer.checkManager.getCheck(AiCheck::class.java) ?: return Optional.empty()
    val ping = spectraPlayer.player.ping
    return Optional.of(
      MonitorSnapshot(
        aiCheck.lastProbability,
        aiCheck.buffer,
        ping,
        spectraPlayer.combat.damageMultiplier,
        aiCheck.prob90,
      )
    )
  }
}
