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

import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.bukkit.entity.Player

interface PunishmentApi {
  fun getViolationLevel(playerId: UUID, groupName: String): CompletableFuture<Int>

  fun resetViolationLevel(playerId: UUID, groupName: String): CompletableFuture<Void>

  fun getViolationLevel(player: Player?, groupName: String): CompletableFuture<Int> {
    if (player == null) {
      return CompletableFuture.completedFuture(0)
    }
    return getViolationLevel(player.uniqueId, groupName)
  }

  fun resetViolationLevel(player: Player?, groupName: String): CompletableFuture<Void> {
    if (player == null) {
      return CompletableFuture.completedFuture(null)
    }
    return resetViolationLevel(player.uniqueId, groupName)
  }
}
