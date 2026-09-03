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
package trqxyz.spectra.checks.impl.ai

import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying
import trqxyz.spectra.checks.AbstractCheck
import trqxyz.spectra.checks.CheckData
import trqxyz.spectra.checks.CheckFactory
import trqxyz.spectra.checks.type.PacketCheck
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.entity.PacketEntity
import trqxyz.spectra.player.SpectraPlayer

@CheckData(name = "ActionManager_Internal")
class ActionManager(player: SpectraPlayer, configManager: ConfigManager) :
  AbstractCheck(player), PacketCheck {
  init {
    val sequence = configManager.aiSequence
    player.combat.ticksSinceAttack = sequence + 1
  }

  interface Factory : CheckFactory {
    override fun create(player: SpectraPlayer): ActionManager
  }

  override fun onPacketReceive(event: PacketReceiveEvent) {
    if (event.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
      val action = WrapperPlayClientInteractEntity(event)
      if (action.action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
        val entity: PacketEntity =
          spectraPlayer.compensatedEntities.getEntity(action.entityId) ?: return

        if (entity.isPlayer) {
          spectraPlayer.combat.ticksSinceAttack = 0
        }
      }
    } else if (WrapperPlayClientPlayerFlying.isFlying(event.packetType)) {
      spectraPlayer.combat.ticksSinceAttack = spectraPlayer.combat.ticksSinceAttack + 1
    }
  }
}
