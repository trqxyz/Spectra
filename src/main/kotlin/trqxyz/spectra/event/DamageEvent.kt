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
package trqxyz.spectra.event

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import trqxyz.spectra.player.PlayerDataManager

class DamageEvent(private val playerDataManager: PlayerDataManager) : Listener {
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
    val damager = event.damager
    if (damager !is Player) {
      return
    }
    val spectraPlayer = playerDataManager.getPlayer(damager) ?: return
    val multiplier = spectraPlayer.combat.damageMultiplier
    if (multiplier < 1.0) {
      event.damage = event.damage * multiplier
    }
  }
}
