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
package trqxyz.spectra.di

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.logging.Logger
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.command.CommandSender
import org.incendo.cloud.SenderMapper
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import trqxyz.spectra.SpectraCore
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiDecisionHistory
import trqxyz.spectra.ai.AiResponseParser
import trqxyz.spectra.ai.AiSerializer
import trqxyz.spectra.ai.AiService
import trqxyz.spectra.ai.DefaultAiService
import trqxyz.spectra.ai.FlatBuffersAiSerializer
import trqxyz.spectra.ai.JacksonAiResponseParser
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.api.SpectraApi
import trqxyz.spectra.api.event.SpectraEventBus
import trqxyz.spectra.api.event.internal.SpectraEventBusImpl
import trqxyz.spectra.api.internal.AiApiImpl
import trqxyz.spectra.api.internal.CheckApiImpl
import trqxyz.spectra.api.internal.MonitorApiImpl
import trqxyz.spectra.api.internal.PunishmentApiImpl
import trqxyz.spectra.api.internal.SpectraApiImpl
import trqxyz.spectra.api.service.AiApi
import trqxyz.spectra.api.service.CheckApi
import trqxyz.spectra.api.service.MonitorApi
import trqxyz.spectra.api.service.PunishmentApi
import trqxyz.spectra.checks.CheckFactory
import trqxyz.spectra.checks.CheckManager
import trqxyz.spectra.checks.impl.ai.ActionManager
import trqxyz.spectra.checks.impl.ai.AiCheck
import trqxyz.spectra.checks.impl.ai.DataCollectorCheck
import trqxyz.spectra.checks.impl.ai.DataCollectorManager
import trqxyz.spectra.checks.impl.ai.PersistentBufferService
import trqxyz.spectra.checks.impl.combat.AimProcessor
import trqxyz.spectra.checks.impl.misc.ClientBrand
import trqxyz.spectra.command.CommandManager
import trqxyz.spectra.command.CommandRegister
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.command.commands.admin.AlertsCommand
import trqxyz.spectra.command.commands.admin.BrandsCommand
import trqxyz.spectra.command.commands.admin.ConnectCommand
import trqxyz.spectra.command.commands.admin.DataCollectCommand
import trqxyz.spectra.command.commands.admin.ExemptCommand
import trqxyz.spectra.command.commands.admin.PunishCommand
import trqxyz.spectra.command.commands.admin.ReloadCommand
import trqxyz.spectra.command.commands.admin.ReportCommand
import trqxyz.spectra.command.commands.admin.SuspiciousCommand
import trqxyz.spectra.command.commands.info.DecisionsCommand
import trqxyz.spectra.command.commands.info.HelpCommand
import trqxyz.spectra.command.commands.info.HistoryCommand
import trqxyz.spectra.command.commands.info.LogsCommand
import trqxyz.spectra.command.commands.info.MonitorCommand
import trqxyz.spectra.command.commands.info.ProfileCommand
import trqxyz.spectra.command.commands.info.StatsCommand
import trqxyz.spectra.command.commands.info.ViewCommand
import trqxyz.spectra.command.handler.SpectraCommandFailureHandler
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.config.LocaleManager
import trqxyz.spectra.connect.ConnectService
import trqxyz.spectra.connect.CredentialsStore
import trqxyz.spectra.connect.ServerHeartbeatService
import trqxyz.spectra.coroutines.SpectraCoroutines
import trqxyz.spectra.damage.AiDamageProcessor
import trqxyz.spectra.damage.DamageProcessor
import trqxyz.spectra.database.DatabaseManager
import trqxyz.spectra.database.OutboxHttpClient
import trqxyz.spectra.database.ViolationSyncService
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.event.DamageEvent
import trqxyz.spectra.integration.WorldGuardManager
import trqxyz.spectra.monitor.MonitorSettingsService
import trqxyz.spectra.monitor.MonitorViewService
import trqxyz.spectra.packet.PacketListener
import trqxyz.spectra.platform.scheduler.PlatformScheduler
import trqxyz.spectra.platform.scheduler.PlatformSchedulerFactory
import trqxyz.spectra.player.ExemptManager
import trqxyz.spectra.player.PlayerDataManager
import trqxyz.spectra.punishment.PunishmentManager
import trqxyz.spectra.redis.CrossServerAlertService
import trqxyz.spectra.redis.CrossServerSuspiciousService
import trqxyz.spectra.redis.RedisManager
import trqxyz.spectra.region.RegionProvider
import trqxyz.spectra.relations.BanStatusBridge
import trqxyz.spectra.relations.RelationCollector
import trqxyz.spectra.relations.RelationEventOutboxWriter
import trqxyz.spectra.relations.RelationEventStore
import trqxyz.spectra.relations.RelationIpCollector
import trqxyz.spectra.relations.RelationSources
import trqxyz.spectra.relations.RelationSyncService
import trqxyz.spectra.relations.VaultEconomyBridge
import trqxyz.spectra.relations.WorldGuardRelationProvider
import trqxyz.spectra.report.ReportMenu
import trqxyz.spectra.report.ReportService
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.sender.SenderFactory
import trqxyz.spectra.server.AIServerProvider

fun spectraModules(plugin: SpectraPlugin) =
  listOf(coreModule(plugin), aiModule(), apiModule(), commandModule(), checkModule())

private fun coreModule(plugin: SpectraPlugin) = module {
  single { plugin }
  single { ObjectMapper() }
  single { BukkitAudiences.create(plugin) }
  single<Logger> { plugin.logger }
  single<PlatformScheduler> { PlatformSchedulerFactory.create() }
  single<SpectraEventBus> { SpectraEventBusImpl() }

  singleOf(::SchedulerService)
  singleOf(::SpectraCoroutines)
  singleOf(::CredentialsStore)
  singleOf(::ConfigManager)
  singleOf(::ConnectService)
  singleOf(::ServerHeartbeatService)
  singleOf(::LocaleManager)
  singleOf(::DatabaseManager)
  singleOf(::OutboxHttpClient)
  singleOf(::ViolationSyncService)
  singleOf(::RelationEventOutboxWriter)
  singleOf(::RelationEventStore)
  singleOf(::RelationIpCollector)
  singleOf(::RelationSources)
  singleOf(::RelationSyncService)
  singleOf(::BanStatusBridge)
  singleOf(::VaultEconomyBridge)
  singleOf(::WorldGuardRelationProvider)
  singleOf(::RelationCollector)
  singleOf(::DebugManager)
  singleOf(::AIServerProvider)
  singleOf(::AlertManager)
  singleOf(::RedisManager)
  singleOf(::CrossServerAlertService)
  singleOf(::CrossServerSuspiciousService)
  singleOf(::MonitorSettingsService)
  singleOf(::MonitorViewService)
  singleOf(::ExemptManager)
  singleOf(::DataCollectorManager)
  singleOf(::PersistentBufferService)
  singleOf(::AiDecisionHistory)
  singleOf(::ReportService)
  singleOf(::ReportMenu)
  singleOf(::WorldGuardManager)
  single<RegionProvider> { get<WorldGuardManager>() }
  single<DamageProcessor> { AiDamageProcessor(get()) }

  singleOf(::SenderFactory).bind<SenderMapper<CommandSender, Sender>>()

  singleOf(::SpectraCommandFailureHandler)

  singleOf(::PlayerDataManager)
  singleOf(::PacketListener)
  singleOf(::DamageEvent)

  // Koin's constructor DSL supports at most 22 parameters. Keep this provider
  // explicit so adding lifecycle collaborators cannot make compilation fail.
  single {
    SpectraCore(
      plugin = get(),
      playerDataManager = get(),
      configManager = get(),
      localeManager = get(),
      aiServerProvider = get(),
      commandManager = get(),
      alertManager = get(),
      databaseManager = get(),
      violationSyncService = get(),
      relationSyncService = get(),
      relationCollector = get(),
      relationEventStore = get(),
      redisManager = get(),
      crossServerAlertService = get(),
      crossServerSuspiciousService = get(),
      debugManager = get(),
      packetListener = get(),
      monitorViewService = get(),
      damageEvent = get(),
      spectraApi = get(),
      adventure = get(),
      coroutines = get(),
      scheduler = get(),
      reportMenu = get(),
      serverHeartbeatService = get(),
    )
  }
}

private fun aiModule() = module {
  singleOf(::FlatBuffersAiSerializer).bind<AiSerializer>()
  singleOf(::JacksonAiResponseParser).bind<AiResponseParser>()
  singleOf(::DefaultAiService).bind<AiService>()
}

private fun apiModule() = module {
  singleOf(::AiApiImpl).bind<AiApi>()
  singleOf(::CheckApiImpl).bind<CheckApi>()
  singleOf(::MonitorApiImpl).bind<MonitorApi>()
  singleOf(::PunishmentApiImpl).bind<PunishmentApi>()
  singleOf(::SpectraApiImpl).bind<SpectraApi>()
}

private fun commandModule() = module {
  includes(adminCommandsModule(), infoCommandsModule())

  single { CommandRegister(getAll(), get()) }
  singleOf(::CommandManager)
}

private fun adminCommandsModule() = module {
  singleOf(::AlertsCommand).bind<SpectraCommand>()
  singleOf(::BrandsCommand).bind<SpectraCommand>()
  singleOf(::ConnectCommand).bind<SpectraCommand>()
  singleOf(::DataCollectCommand).bind<SpectraCommand>()
  singleOf(::ExemptCommand).bind<SpectraCommand>()
  singleOf(::PunishCommand).bind<SpectraCommand>()
  singleOf(::ReloadCommand).bind<SpectraCommand>()
  singleOf(::ReportCommand).bind<SpectraCommand>()
  singleOf(::SuspiciousCommand).bind<SpectraCommand>()
}

private fun infoCommandsModule() = module {
  singleOf(::DecisionsCommand).bind<SpectraCommand>()
  singleOf(::HelpCommand).bind<SpectraCommand>()
  singleOf(::HistoryCommand).bind<SpectraCommand>()
  singleOf(::LogsCommand).bind<SpectraCommand>()
  singleOf(::MonitorCommand).bind<SpectraCommand>()
  singleOf(::ProfileCommand).bind<SpectraCommand>()
  singleOf(::StatsCommand).bind<SpectraCommand>()
  singleOf(::ViewCommand).bind<SpectraCommand>()
}

private fun checkModule() = module {
  single<CheckFactory>(named("aim")) { CheckFactory { player -> AimProcessor(player) } }
  single<CheckFactory>(named("action")) { CheckFactory { player -> ActionManager(player, get()) } }
  single<CheckFactory>(named("ai")) {
    CheckFactory { player ->
      AiCheck(player, get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
  }
  single<CheckFactory>(named("collector")) {
    CheckFactory { player -> DataCollectorCheck(player, get(), get(), get()) }
  }
  single<CheckFactory>(named("brand")) {
    CheckFactory { player -> ClientBrand(player, get(), get()) }
  }

  single<Set<CheckFactory>> { getAll<CheckFactory>().toSet() }

  single<CheckManager.Factory> { CheckManager.Factory { player -> CheckManager(player, get()) } }
  single<PunishmentManager.Factory> {
    PunishmentManager.Factory { player ->
      PunishmentManager(player, get(), get(), get(), get(), get(), get(), get())
    }
  }
}
