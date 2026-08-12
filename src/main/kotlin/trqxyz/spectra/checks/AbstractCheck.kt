/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 KaelusAI
 *
 * This file contains code derived from GrimAC.
 * The original authors of GrimAC are credited below.
 *
 * Copyright (c) 2021-2026 GrimAC, DefineOutside and contributors.
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
package trqxyz.spectra.checks

import trqxyz.spectra.api.event.CheckFlagEvent
import trqxyz.spectra.player.SpectraPlayer

abstract class AbstractCheck(protected val spectraPlayer: SpectraPlayer) : ICheck {
  private val checkNameInternal: String
  val configName: String

  override val checkName: String
    get() = checkNameInternal

  init {
    val data = javaClass.getAnnotation(CheckData::class.java)
    checkNameInternal = data.name
    configName = if (data.configName == "DEFAULT") data.name else data.configName
  }

  protected fun flag(debug: String) {
    val event =
      CheckFlagEvent(spectraPlayer.uuid, spectraPlayer.player.name, checkName, configName, debug)
    spectraPlayer.eventBus.post(event)
    if (event.cancelled) {
      return
    }
    spectraPlayer.punishmentManager.handleFlag(this, debug)
  }
}
