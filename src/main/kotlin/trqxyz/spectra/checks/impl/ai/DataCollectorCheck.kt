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
package trqxyz.spectra.checks.impl.ai

import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.checks.AbstractCheck
import trqxyz.spectra.checks.CheckData
import trqxyz.spectra.checks.CheckFactory
import trqxyz.spectra.checks.type.PacketCheck
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.data.AiRotationState
import trqxyz.spectra.data.DataSession
import trqxyz.spectra.player.SpectraPlayer

@CheckData(name = "DataCollector_Internal")
class DataCollectorCheck(
  spectraPlayer: SpectraPlayer,
  private val dataCollectorManager: DataCollectorManager,
  private val plugin: SpectraPlugin,
  private val configManager: ConfigManager,
) : AbstractCheck(spectraPlayer), PacketCheck {
  // Same canonical feature computer as inference, so any data collected here
  // matches the SlothPanel collector and the models.
  private val rotationState = AiRotationState()

  interface Factory : CheckFactory {
    override fun create(player: SpectraPlayer): DataCollectorCheck
  }

  override fun onPacketReceive(event: PacketReceiveEvent) {
    val spectraPlayer = spectraPlayer
    val session: DataSession = dataCollectorManager.getSession(spectraPlayer.uuid) ?: return
    if (!WrapperPlayClientPlayerFlying.isFlying(event.packetType)) return
    if (spectraPlayer.compensatedEntities.self.riding != null) return
    if (spectraPlayer.packetStateData.lastPacketWasOnePointSeventeenDuplicate) return

    val tick = rotationState.update(spectraPlayer.movement.yaw, spectraPlayer.movement.pitch)

    if (
      spectraPlayer.packetStateData.lastPacketWasTeleport ||
        spectraPlayer.packetStateData.lastPacketWasServerRotation
    ) {
      plugin.logger.info(
        "Skipping server-side rotation packet in data collection for player: ${spectraPlayer.player.name}"
      )
      return
    }

    if (
      configManager.aiContinuous ||
        spectraPlayer.combat.ticksSinceAttack <= configManager.aiSequence
    ) {
      session.addTick(tick)
    }
  }
}
