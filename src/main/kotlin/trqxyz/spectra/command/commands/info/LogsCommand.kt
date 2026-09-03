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
package trqxyz.spectra.command.commands.info

import org.incendo.cloud.CommandManager
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.parser.standard.IntegerParser
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.config.LocaleManager
import trqxyz.spectra.database.DatabaseManager
import trqxyz.spectra.database.Violation
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil
import trqxyz.spectra.utils.TimeUtil

class LogsCommand(
  private val databaseManager: DatabaseManager,
  private val configManager: ConfigManager,
  private val localeManager: LocaleManager,
  private val scheduler: SchedulerService,
) : SpectraCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.buildAndRegister("spectra") {
      literal("logs")
        .permission("spectra.logs")
        .optional("page", IntegerParser.integerParser(1))
        .handler(this@LogsCommand::handleLogs)
    }
  }

  private fun handleLogs(context: CommandContext<Sender>) {
    val sender = context.sender()
    val page: Int = context.getOrDefault("page", 1)

    if (!configManager.config.getBoolean("history.enabled", false)) {
      MessageUtil.sendMessage(sender.nativeSender, Message.HISTORY_DISABLED)
      return
    }

    if (!databaseManager.isAvailable) {
      MessageUtil.sendMessage(sender.nativeSender, Message.STORAGE_DEGRADED)
    }

    scheduler.runAsync {
      val entriesPerPage = 10
      val violations: List<Violation> =
        databaseManager.database.getViolations(page, entriesPerPage, 0L)
      val totalLogs = databaseManager.database.getLogCount(0L)
      val maxPages =
        kotlin.math.max(1, kotlin.math.ceil(totalLogs.toDouble() / entriesPerPage).toInt())

      val header =
        MessageUtil.getMessage(
          Message.LOGS_HEADER,
          "page",
          page.toString(),
          "max_pages",
          maxPages.toString(),
        )

      val entries =
        violations.map { violation ->
          MessageUtil.getMessage(
            Message.LOGS_ENTRY,
            "server",
            violation.serverName,
            "player",
            violation.playerName,
            "check",
            violation.checkName,
            "vl",
            violation.vl.toString(),
            "verbose",
            violation.verbose,
            "timeago",
            TimeUtil.formatTimeAgo(violation.createdAt, localeManager),
          )
        }

      scheduler.runSync {
        sender.sendMessage(header)

        if (entries.isEmpty()) {
          MessageUtil.sendMessage(sender.nativeSender, Message.LOGS_NO_VIOLATIONS)
          return@runSync
        }

        for (entry in entries) {
          sender.sendMessage(entry)
        }
      }
    }
  }
}
