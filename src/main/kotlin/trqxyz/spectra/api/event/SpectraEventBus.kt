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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.api.event

fun interface SpectraEventListener<T : SpectraEvent> {
  fun handle(event: T)
}

interface SpectraEventBus {
  fun post(event: SpectraEvent)

  fun <T : SpectraEvent> subscribe(
    pluginContext: Any,
    eventType: Class<T>,
    listener: SpectraEventListener<T>,
  )

  fun <T : SpectraEvent> subscribe(
    pluginContext: Any,
    eventType: Class<T>,
    listener: SpectraEventListener<T>,
    priority: Int,
    ignoreCancelled: Boolean,
  )

  fun unregisterListener(pluginContext: Any, listener: SpectraEventListener<*>)

  fun unregisterAll(pluginContext: Any)
}
