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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.api.event

/** Functional listener for [SpectraEventBus] subscriptions. */
fun interface SpectraEventListener<T : SpectraEvent> {
  fun handle(event: T)
}

/**
 * Lightweight event bus for SpectraPlugin events.
 *
 * Events are dispatched on the thread that calls [post].
 */
interface SpectraEventBus {
  /** Post an event to all registered listeners. */
  fun post(event: SpectraEvent)

  /** Subscribe with default priority (0) and ignoreCancelled=false. */
  fun <T : SpectraEvent> subscribe(
    pluginContext: Any,
    eventType: Class<T>,
    listener: SpectraEventListener<T>,
  )

  /** Subscribe with explicit priority and ignoreCancelled. */
  fun <T : SpectraEvent> subscribe(
    pluginContext: Any,
    eventType: Class<T>,
    listener: SpectraEventListener<T>,
    priority: Int,
    ignoreCancelled: Boolean,
  )

  /** Unregister a specific listener for a context. */
  fun unregisterListener(pluginContext: Any, listener: SpectraEventListener<*>)

  /** Unregister all listeners for a context. */
  fun unregisterAll(pluginContext: Any)
}
