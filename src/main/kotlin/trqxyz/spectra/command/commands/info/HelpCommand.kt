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
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil

class HelpCommand : SpectraCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.buildAndRegister("spectra") {
      permission("spectra.help")
      handler(this@HelpCommand::help)
    }
    manager.buildAndRegister("spectra") {
      permission("spectra.help")
      literal("help").handler(this@HelpCommand::help)
    }
  }

  private fun help(context: CommandContext<Sender>) {
    val sender = context.sender()
    MessageUtil.sendMessageList(sender.nativeSender, Message.HELP_MESSAGE, "command", "spectra")
  }
}
