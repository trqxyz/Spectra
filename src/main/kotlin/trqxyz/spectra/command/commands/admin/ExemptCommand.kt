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
package trqxyz.spectra.command.commands.admin

import org.bukkit.entity.Player
import org.incendo.cloud.CommandManager
import org.incendo.cloud.bukkit.parser.PlayerParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.parser.standard.StringParser
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.config.LocaleManager
import trqxyz.spectra.player.ExemptManager
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil
import trqxyz.spectra.utils.TimeUtil

class ExemptCommand(
  private val exemptManager: ExemptManager,
  private val localeManager: LocaleManager,
) : SpectraCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.buildAndRegister("spectra") {
      literal("exempt")
        .permission("spectra.exempt.manage")
        .required("target", PlayerParser.playerParser())
        .optional("duration", StringParser.stringParser())
        .handler(this@ExemptCommand::handleExempt)
    }

    manager.buildAndRegister("spectra") {
      literal("exempt")
        .permission("spectra.exempt.manage")
        .literal("remove")
        .required("target", PlayerParser.playerParser())
        .handler(this@ExemptCommand::handleRemoveExempt)
    }

    manager.buildAndRegister("spectra") {
      literal("exempt")
        .permission("spectra.exempt.manage")
        .literal("status")
        .required("target", PlayerParser.playerParser())
        .handler(this@ExemptCommand::handleStatus)
    }
  }

  private fun handleExempt(context: CommandContext<Sender>) {
    val sender = context.sender()
    val target: Player = context["target"]
    val durationStr: String = context.getOrDefault("duration", "5m")

    val durationMillis = TimeUtil.parseDuration(durationStr)
    if (durationMillis == 0L) {
      MessageUtil.sendMessage(sender.nativeSender, Message.EXEMPT_INVALID_DURATION)
      return
    }

    exemptManager.addExemption(target.uniqueId, durationMillis)

    if (durationMillis == -1L) {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_SUCCESS_PERM,
        "player",
        target.name,
      )
    } else {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_SUCCESS_TEMP,
        "player",
        target.name,
        "duration",
        durationStr,
      )
    }
  }

  private fun handleRemoveExempt(context: CommandContext<Sender>) {
    val sender = context.sender()
    val target: Player = context["target"]

    if (exemptManager.removeExemption(target.uniqueId)) {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_REMOVE_SUCCESS,
        "player",
        target.name,
      )
    } else {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_REMOVE_FAIL,
        "player",
        target.name,
      )
    }
  }

  private fun handleStatus(context: CommandContext<Sender>) {
    val sender = context.sender()
    val target: Player = context["target"]

    if (target.hasPermission("spectra.exempt")) {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_STATUS_PERM_PERMISSION,
        "player",
        target.name,
      )
      return
    }

    val expiryTime = exemptManager.getExpiryTime(target.uniqueId)
    if (expiryTime == null) {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_STATUS_NOT_EXEMPT,
        "player",
        target.name,
      )
    } else if (expiryTime == -1L) {
      MessageUtil.sendMessage(
        sender.nativeSender,
        Message.EXEMPT_STATUS_PERM_COMMAND,
        "player",
        target.name,
      )
    } else {
      val remaining = expiryTime - System.currentTimeMillis()
      if (remaining <= 0) {
        MessageUtil.sendMessage(
          sender.nativeSender,
          Message.EXEMPT_STATUS_EXPIRED,
          "player",
          target.name,
        )
        exemptManager.removeExemption(target.uniqueId)
      } else {
        val remainingStr = TimeUtil.formatDuration(remaining, localeManager)
        MessageUtil.sendMessage(
          sender.nativeSender,
          Message.EXEMPT_STATUS_TEMP,
          "player",
          target.name,
          "duration",
          remainingStr,
        )
      }
    }
  }
}
