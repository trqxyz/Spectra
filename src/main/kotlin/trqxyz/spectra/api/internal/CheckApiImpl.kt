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

import java.util.Locale
import java.util.Optional
import java.util.UUID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import trqxyz.spectra.api.model.CheckInfo
import trqxyz.spectra.api.service.CheckApi
import trqxyz.spectra.checks.AbstractCheck
import trqxyz.spectra.checks.ICheck
import trqxyz.spectra.player.PlayerDataManager

class CheckApiImpl(private val playerDataManager: PlayerDataManager) : CheckApi {
  override fun listChecks(playerId: UUID): ImmutableList<CheckInfo> {
    val player = playerDataManager.getPlayer(playerId) ?: return persistentListOf()
    val builder = persistentListOf<CheckInfo>().builder()
    for (check in player.checkManager.getAllChecks()) {
      builder.add(toInfo(check))
    }
    return builder.build()
  }

  override fun getCheck(playerId: UUID, checkName: String): Optional<CheckInfo> {
    if (checkName.isBlank()) {
      return Optional.empty()
    }
    val player = playerDataManager.getPlayer(playerId) ?: return Optional.empty()
    val needle = checkName.trim().lowercase(Locale.ROOT)
    for (check in player.checkManager.getAllChecks()) {
      val info = toInfo(check)
      if (info.name.lowercase(Locale.ROOT) == needle) {
        return Optional.of(info)
      }
      if (info.configName != null && info.configName.lowercase(Locale.ROOT) == needle) {
        return Optional.of(info)
      }
    }
    return Optional.empty()
  }

  private fun toInfo(check: ICheck): CheckInfo {
    val configName =
      if (check is AbstractCheck) {
        check.configName
      } else {
        null
      }
    return CheckInfo(check.checkName, configName)
  }
}
