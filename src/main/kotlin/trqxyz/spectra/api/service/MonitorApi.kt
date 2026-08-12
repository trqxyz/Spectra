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
package trqxyz.spectra.api.service

import java.util.Optional
import java.util.UUID
import org.bukkit.entity.Player
import trqxyz.spectra.api.model.MonitorSnapshot

/** Access to current monitor data for a player. */
interface MonitorApi {
  /** Returns the latest monitor snapshot if available. */
  fun getSnapshot(playerId: UUID): Optional<MonitorSnapshot>

  /** Convenience overload for Bukkit [Player]. */
  fun getSnapshot(player: Player?): Optional<MonitorSnapshot> {
    if (player == null) {
      return Optional.empty()
    }
    return getSnapshot(player.uniqueId)
  }
}
