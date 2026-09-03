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
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.ServicePriority
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.api.SpectraApi
import trqxyz.spectra.command.CommandManager
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.config.LocaleManager
import trqxyz.spectra.connect.ServerHeartbeatService
import trqxyz.spectra.coroutines.SpectraCoroutines
import trqxyz.spectra.database.DatabaseManager
import trqxyz.spectra.database.ViolationSyncService
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.event.DamageEvent
import trqxyz.spectra.monitor.MonitorViewService
import trqxyz.spectra.packet.PacketListener
import trqxyz.spectra.player.PlayerDataManager
import trqxyz.spectra.redis.CrossServerAlertService
import trqxyz.spectra.redis.CrossServerSuspiciousService
import trqxyz.spectra.redis.RedisManager
import trqxyz.spectra.relations.RelationCollector
import trqxyz.spectra.relations.RelationEventStore
import trqxyz.spectra.relations.RelationSyncService
import trqxyz.spectra.report.ReportMenu
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.server.AIServerProvider
import trqxyz.spectra.utils.MessageUtil

class SpectraCore
@Suppress("LongParameterList")
constructor(
  private val plugin: SpectraPlugin,
  private val playerDataManager: PlayerDataManager,
  private val configManager: ConfigManager,
  private val localeManager: LocaleManager,
  private val aiServerProvider: AIServerProvider,
  private val commandManager: CommandManager,
  private val alertManager: AlertManager,
  private val databaseManager: DatabaseManager,
  private val violationSyncService: ViolationSyncService,
  private val relationSyncService: RelationSyncService,
  private val relationCollector: RelationCollector,
  private val relationEventStore: RelationEventStore,
  private val redisManager: RedisManager,
  private val crossServerAlertService: CrossServerAlertService,
  private val crossServerSuspiciousService: CrossServerSuspiciousService,
  private val debugManager: DebugManager,
  private val packetListener: PacketListener,
  private val monitorViewService: MonitorViewService,
  private val damageEvent: DamageEvent,
  private val spectraApi: SpectraApi,
  private val adventure: BukkitAudiences,
  private val coroutines: SpectraCoroutines,
  private val scheduler: SchedulerService,
  private val reportMenu: ReportMenu,
  private val serverHeartbeatService: ServerHeartbeatService,
) {
  fun enable() {
    commandManager.registerCommands()

    MessageUtil.init(localeManager, adventure, plugin.logger)

    initializePacketRuntime()
    plugin.server.pluginManager.registerEvents(damageEvent, plugin)
    plugin.server.pluginManager.registerEvents(reportMenu, plugin)
    plugin.server.servicesManager.register(
      SpectraApi::class.java,
      spectraApi,
      plugin,
      ServicePriority.Normal,
    )
    violationSyncService.start()
    serverHeartbeatService.start()
    relationCollector.start()
    relationSyncService.start()
    scheduler.runAsync {
      crossServerAlertService.start()
      crossServerSuspiciousService.start()
    }
  }

  fun disable() {
    plugin.server.servicesManager.unregister(SpectraApi::class.java, spectraApi)
    runCatching { playerDataManager.saveAllBuffersSync() }
    runCatching { relationCollector.stop() }
    runCatching { relationEventStore.flushAndStop() }
    runCatching { violationSyncService.stop() }
    runCatching { serverHeartbeatService.stop() }
    runCatching { relationSyncService.stop() }
    runCatching { aiServerProvider.shutdownTransport() }
    runCatching { crossServerAlertService.shutdown() }
    runCatching { crossServerSuspiciousService.shutdown() }
    runCatching { redisManager.shutdown() }
    adventure.close()
    coroutines.close()
    databaseManager.shutdown()
  }

  fun reload() {
    configManager.reloadConfig()
    localeManager.reload()
    debugManager.reload()
    alertManager.reload()
    aiServerProvider.reload()
    playerDataManager.reloadAllPlayers()
    monitorViewService.reload()
    crossServerAlertService.shutdown()
    crossServerSuspiciousService.shutdown()
    scheduler.runAsync {
      redisManager.shutdown()
      crossServerAlertService.start()
      crossServerSuspiciousService.start()
    }
  }

  private fun initializePacketRuntime() {
    PacketEvents.getAPI().eventManager.registerListener(packetListener)
    monitorViewService.start()
    PacketEvents.getAPI().init()
  }
}
