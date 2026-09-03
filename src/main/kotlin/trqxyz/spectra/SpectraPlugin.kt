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
package trqxyz.spectra

import java.io.File
import java.util.logging.Level
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import trqxyz.spectra.di.spectraModules
import trqxyz.spectra.integration.SpectraFlags

class SpectraPlugin : JavaPlugin() {
  private var core: SpectraCore? = null
  private val packetEventsLoader = PacketEventsLoader(this)
  private var packetEventsLoadFailure: Throwable? = null
  private var runtimeStopped = false

  override fun onLoad() {
    migrateLegacyDataFolder()
    packetEventsLoadFailure = runCatching { packetEventsLoader.load() }.exceptionOrNull()
    if (server.pluginManager.getPlugin("WorldGuard") != null) {
      runCatching { SpectraFlags.register(logger) }
        .onFailure { logger.log(Level.WARNING, "Failed to register WorldGuard flags", it) }
    }
  }

  private fun migrateLegacyDataFolder() {
    val legacy = File(dataFolder.parentFile, "SlothAC")
    if (!dataFolder.exists() && legacy.isDirectory) {
      legacy.copyRecursively(dataFolder, overwrite = false)
    }
  }

  override fun onEnable() {
    runtimeStopped = false
    packetEventsLoadFailure?.let { failure ->
      handleEnableFailure(failure)
      return
    }

    runCatching(::enableRuntime).onFailure(::handleEnableFailure)
  }

  override fun onDisable() {
    shutdownRuntime()
  }

  fun onReload() {
    core?.reload()
  }

  private companion object {
    const val BSTATS_PLUGIN_ID = 30367
  }

  private fun enableRuntime() {
    val koinApp = startKoin { modules(spectraModules(this@SpectraPlugin)) }
    core = koinApp.koin.get()
    core?.enable()
    Metrics(this, BSTATS_PLUGIN_ID)
  }

  private fun handleEnableFailure(failure: Throwable) {
    logger.log(Level.SEVERE, "Spectra failed to start and will disable itself safely.", failure)
    shutdownRuntime()
    server.pluginManager.disablePlugin(this)
  }

  private fun shutdownRuntime() {
    if (runtimeStopped) {
      return
    }
    runtimeStopped = true

    runCatching { core?.disable() }
    core = null
    runCatching { stopKoin() }
    packetEventsLoader.shutdown()
  }
}
