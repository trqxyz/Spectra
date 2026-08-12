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
package trqxyz.spectra.api

import trqxyz.spectra.api.event.SpectraEventBus
import trqxyz.spectra.api.service.AiApi
import trqxyz.spectra.api.service.CheckApi
import trqxyz.spectra.api.service.MonitorApi
import trqxyz.spectra.api.service.PunishmentApi

/**
 * Public API surface for SpectraPlugin.
 *
 * Obtain an instance via [trqxyz.spectra.api.SpectraApiProvider.get].
 */
interface SpectraApi {
  /** AI-related data access and status. */
  fun ai(): AiApi

  /** Check metadata and per-player check listing. */
  fun checks(): CheckApi

  /** Violation and punishment accessors. */
  fun punishments(): PunishmentApi

  /** Current monitor snapshot data (probability/buffer/ping/dmg). */
  fun monitor(): MonitorApi

  /** Spectra event bus for subscribing to internal events. */
  fun events(): SpectraEventBus
}
