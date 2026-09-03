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
package trqxyz.spectra.database

import java.util.UUID
import trqxyz.spectra.monitor.MonitorSettings
import trqxyz.spectra.player.SpectraPlayer

interface ViolationDatabase {
  fun logAlert(player: SpectraPlayer, verbose: String, checkName: String, vls: Int)

  fun getLogCount(player: UUID): Int

  fun getViolations(player: UUID, page: Int, limit: Int): List<Violation>

  fun getUniqueViolatorsSince(since: Long): Int

  fun recordLogin(playerUUID: UUID, timestamp: Long)

  fun countUniquePlayersSince(since: Long): Int

  fun saveAiBuffer(playerUUID: UUID, buffer: Double, updatedAt: Long)

  fun loadAiBuffer(playerUUID: UUID): AiBufferState?

  fun getLogCount(since: Long): Int

  fun getLogCounts(playerUUIDs: Collection<UUID>): Map<UUID, Int>

  fun getViolations(page: Int, limit: Int, since: Long): List<Violation>

  fun getViolationLevel(playerUUID: UUID, punishGroupName: String): Int

  fun incrementViolationLevel(playerUUID: UUID, punishGroupName: String): Int

  fun resetViolationLevel(playerUUID: UUID, punishGroupName: String)

  fun resetAllViolationLevels(playerUUID: UUID)

  fun loadMonitorSettings(playerUUID: UUID): MonitorSettings?

  fun saveMonitorSettings(playerUUID: UUID, settings: MonitorSettings)
}
