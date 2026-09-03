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

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.PacketEventsAPI
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import java.util.logging.Level

internal class PacketEventsLoader(private val plugin: SpectraPlugin) {

  fun load() {
    PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin))
    PacketEvents.getAPI()
      .settings
      .checkForUpdates(false)
      .debug(false)
      .fullStackTrace(false)
      .kickOnPacketException(false)
      .reEncodeByDefault(false)
    PacketEvents.getAPI().load()
  }

  fun shutdown() {
    val api = PacketEvents.getAPI() ?: return

    runCatching {
        when {
          api.isInitialized -> api.terminate()
          api.isLoaded && !api.isTerminated -> shutdownLoadedApi(api)
        }
      }
      .onFailure { exception ->
        plugin.logger.log(Level.WARNING, "Failed to shut down PacketEvents cleanly.", exception)
      }
  }

  private fun shutdownLoadedApi(api: PacketEventsAPI<*>) {
    api.injector.uninject()
    api.eventManager.unregisterAllListeners()
  }
}
