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
package trqxyz.spectra.api.service

import java.util.Optional
import java.util.UUID
import org.bukkit.entity.Player
import trqxyz.spectra.api.model.AiSnapshot

interface AiApi {
  fun isEnabled(): Boolean

  fun getSnapshot(playerId: UUID): Optional<AiSnapshot>

  fun getSnapshot(player: Player?): Optional<AiSnapshot> {
    if (player == null) {
      return Optional.empty()
    }
    return getSnapshot(player.uniqueId)
  }
}
