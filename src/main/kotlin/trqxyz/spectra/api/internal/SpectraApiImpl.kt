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
package trqxyz.spectra.api.internal

import trqxyz.spectra.api.SpectraApi
import trqxyz.spectra.api.event.SpectraEventBus
import trqxyz.spectra.api.service.AiApi
import trqxyz.spectra.api.service.CheckApi
import trqxyz.spectra.api.service.MonitorApi
import trqxyz.spectra.api.service.PunishmentApi

class SpectraApiImpl(
  private val aiApi: AiApi,
  private val checkApi: CheckApi,
  private val punishmentApi: PunishmentApi,
  private val monitorApi: MonitorApi,
  private val eventBus: SpectraEventBus,
) : SpectraApi {
  override fun ai(): AiApi = aiApi

  override fun checks(): CheckApi = checkApi

  override fun punishments(): PunishmentApi = punishmentApi

  override fun monitor(): MonitorApi = monitorApi

  override fun events(): SpectraEventBus = eventBus
}
