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
package trqxyz.spectra.command.commands.info

import org.incendo.cloud.CommandManager
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.kotlin.extension.buildAndRegister
import trqxyz.spectra.command.CommandRegister
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.command.requirements.PlayerSenderRequirement
import trqxyz.spectra.monitor.MonitorViewService
import trqxyz.spectra.monitor.VIEW_PERMISSION
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil

class ViewCommand(private val monitorViewService: MonitorViewService) : SpectraCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.buildAndRegister("spectra") {
      literal("view")
        .permission(VIEW_PERMISSION)
        .mutate { it.apply(CommandRegister.REQUIREMENT_FACTORY.create(PlayerSenderRequirement)) }
        .handler(this@ViewCommand::toggle)
    }
  }

  private fun toggle(context: CommandContext<Sender>) {
    val viewer = context.sender().player ?: return
    val enabled = monitorViewService.toggle(viewer)

    if (enabled) {
      MessageUtil.sendMessage(viewer, Message.VIEW_ENABLED)
    } else {
      MessageUtil.sendMessage(viewer, Message.VIEW_DISABLED)
    }
  }
}
