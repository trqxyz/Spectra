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

import java.util.Locale
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.min
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.database.DatabaseManager
import trqxyz.spectra.debug.DebugCategory
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.scheduler.SchedulerService

private const val MILLIS_PER_HOUR = 3_600_000.0

class PersistentBufferService(
  private val configManager: ConfigManager,
  private val databaseManager: DatabaseManager,
  private val scheduler: SchedulerService,
  private val debugManager: DebugManager,
  private val logger: Logger,
) {
  fun restoreOnLogin(spectraPlayer: SpectraPlayer) {
    if (!configManager.persistentBufferEnabled) return
    val aiCheck = spectraPlayer.checkManager.getCheck(AiCheck::class.java) ?: return

    scheduler.runAsync {
      val state = databaseManager.database.loadAiBuffer(spectraPlayer.uuid) ?: return@runAsync
      val now = System.currentTimeMillis()
      val ageMillis = now - state.updatedAt
      val playerName = spectraPlayer.player.name

      if (ageMillis < 0L) {
        logger.warning(
          "[PersistentBuffer] Skipped restore for $playerName: stored timestamp is in the future"
        )
        return@runAsync
      }

      if (!spectraPlayer.player.isOnline) return@runAsync

      if (ageMillis < configManager.persistentBufferDisconnectWindowMillis) {
        scheduler.runSync(spectraPlayer.player) {
          if (spectraPlayer.player.isOnline) aiCheck.restoreBuffer(state.buffer)
        }
        debugManager.log(
          DebugCategory.AI_PERSISTENT_BUFFER,
          "$playerName reconnected within disconnect window; buffer ${format(state.buffer)} kept",
        )
        return@runAsync
      }

      if (ageMillis > configManager.persistentBufferTtlMillis) {
        debugManager.log(
          DebugCategory.AI_PERSISTENT_BUFFER,
          "$playerName buffer expired (offline ${format(ageMillis / MILLIS_PER_HOUR)}h), discarded",
        )
        return@runAsync
      }

      val ageHours = ageMillis / MILLIS_PER_HOUR
      val decayed = state.buffer - configManager.persistentBufferDecayPerHour * ageHours
      val capped = min(decayed, configManager.persistentBufferCap)
      val finalBuffer = max(0.0, capped)

      scheduler.runSync(spectraPlayer.player) {
        if (spectraPlayer.player.isOnline) aiCheck.restoreBuffer(finalBuffer)
      }
      debugManager.log(
        DebugCategory.AI_PERSISTENT_BUFFER,
        "$playerName restored buffer ${format(state.buffer)} → ${format(finalBuffer)} (offline ${format(ageHours)}h)",
      )
    }
  }

  fun saveOnQuit(spectraPlayer: SpectraPlayer) {
    val buffer = bufferToPersist(spectraPlayer) ?: return
    databaseManager.database.saveAiBuffer(spectraPlayer.uuid, buffer, System.currentTimeMillis())
  }

  fun saveOnShutdown(spectraPlayer: SpectraPlayer) {
    saveOnQuit(spectraPlayer)
  }

  private fun bufferToPersist(spectraPlayer: SpectraPlayer): Double? {
    val buffer = spectraPlayer.checkManager.getCheck(AiCheck::class.java)?.buffer
    return when {
      !configManager.persistentBufferEnabled -> null
      buffer == null || buffer < configManager.persistentBufferSaveThreshold -> null
      else -> buffer
    }
  }

  private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
