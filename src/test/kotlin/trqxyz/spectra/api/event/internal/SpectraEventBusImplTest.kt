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
package trqxyz.spectra.api.event.internal

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import trqxyz.spectra.api.event.SpectraCancellableEvent
import trqxyz.spectra.api.event.SpectraEvent
import trqxyz.spectra.api.event.SpectraEventListener

class SpectraEventBusImplTest {

  private lateinit var bus: SpectraEventBusImpl
  private val context = Any()

  @BeforeEach
  fun setUp() {
    bus = SpectraEventBusImpl()
  }

  private class SimpleEvent : SpectraEvent

  private open class ParentEvent : SpectraEvent

  private class ChildEvent : ParentEvent()

  private class CancellableTestEvent : SpectraCancellableEvent {
    override var cancelled: Boolean = false
  }

  @Test
  fun `post delivers event to subscriber`() {
    val received = mutableListOf<SpectraEvent>()
    bus.subscribe(context, SimpleEvent::class.java, SpectraEventListener { received.add(it) })

    bus.post(SimpleEvent())
    assertEquals(1, received.size)
  }

  @Test
  fun `post with no subscribers does not throw`() {
    bus.post(SimpleEvent())
  }

  @Test
  fun `multiple subscribers all receive event`() {
    var count = 0
    bus.subscribe(context, SimpleEvent::class.java, SpectraEventListener { count++ })
    bus.subscribe(context, SimpleEvent::class.java, SpectraEventListener { count++ })
    bus.subscribe(context, SimpleEvent::class.java, SpectraEventListener { count++ })

    bus.post(SimpleEvent())
    assertEquals(3, count)
  }

  @Test
  fun `higher priority listeners execute first`() {
    val order = mutableListOf<String>()

    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { order.add("low") },
      priority = 1,
      ignoreCancelled = false,
    )
    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { order.add("high") },
      priority = 10,
      ignoreCancelled = false,
    )
    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { order.add("mid") },
      priority = 5,
      ignoreCancelled = false,
    )

    bus.post(SimpleEvent())
    assertEquals(listOf("high", "mid", "low"), order)
  }

  @Test
  fun `same priority listeners all execute`() {
    var count = 0
    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { count++ },
      priority = 5,
      ignoreCancelled = false,
    )
    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { count++ },
      priority = 5,
      ignoreCancelled = false,
    )

    bus.post(SimpleEvent())
    assertEquals(2, count)
  }

  @Test
  fun `cancelled event skips non-ignoreCancelled listeners`() {
    val received = mutableListOf<String>()

    bus.subscribe(
      context,
      CancellableTestEvent::class.java,
      SpectraEventListener {
        it.cancelled = true
        received.add("canceller")
      },
      priority = 10,
      ignoreCancelled = false,
    )

    bus.subscribe(
      context,
      CancellableTestEvent::class.java,
      SpectraEventListener { received.add("skipped") },
      priority = 1,
      ignoreCancelled = false,
    )

    bus.post(CancellableTestEvent())
    assertEquals(listOf("canceller"), received)
  }

  @Test
  fun `ignoreCancelled listener still receives cancelled event`() {
    val received = mutableListOf<String>()

    bus.subscribe(
      context,
      CancellableTestEvent::class.java,
      SpectraEventListener {
        it.cancelled = true
        received.add("canceller")
      },
      priority = 10,
      ignoreCancelled = false,
    )

    bus.subscribe(
      context,
      CancellableTestEvent::class.java,
      SpectraEventListener { received.add("monitor") },
      priority = 1,
      ignoreCancelled = true,
    )

    bus.post(CancellableTestEvent())
    assertEquals(listOf("canceller", "monitor"), received)
  }

  @Test
  fun `child event dispatched to parent subscriber`() {
    var parentReceived = false
    bus.subscribe(context, ParentEvent::class.java, SpectraEventListener { parentReceived = true })

    bus.post(ChildEvent())
    assertTrue(parentReceived)
  }

  @Test
  fun `child event dispatched to both child and parent subscribers`() {
    val received = mutableListOf<String>()
    bus.subscribe(context, ChildEvent::class.java, SpectraEventListener { received.add("child") })
    bus.subscribe(context, ParentEvent::class.java, SpectraEventListener { received.add("parent") })

    bus.post(ChildEvent())
    assertTrue(received.contains("child"))
    assertTrue(received.contains("parent"))
  }

  @Test
  fun `unregisterListener removes specific listener`() {
    var count = 0
    val listener = SpectraEventListener<SimpleEvent> { count++ }
    bus.subscribe(context, SimpleEvent::class.java, listener)

    bus.post(SimpleEvent())
    assertEquals(1, count)

    bus.unregisterListener(context, listener)
    bus.post(SimpleEvent())
    assertEquals(1, count)
  }

  @Test
  fun `unregisterAll removes all listeners for context`() {
    var count = 0
    val ctx1 = Any()
    val ctx2 = Any()
    bus.subscribe(ctx1, SimpleEvent::class.java, SpectraEventListener { count++ })
    bus.subscribe(ctx2, SimpleEvent::class.java, SpectraEventListener { count++ })

    bus.post(SimpleEvent())
    assertEquals(2, count)

    bus.unregisterAll(ctx1)
    bus.post(SimpleEvent())
    assertEquals(3, count)
  }

  @Test
  fun `unregisterAll with unknown context does not throw`() {
    bus.unregisterAll(Any())
  }

  @Test
  fun `listener exception does not prevent other listeners from running`() {
    val received = mutableListOf<String>()

    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { received.add("before") },
      priority = 10,
      ignoreCancelled = false,
    )

    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { throw IllegalStateException("boom") },
      priority = 5,
      ignoreCancelled = false,
    )

    bus.subscribe(
      context,
      SimpleEvent::class.java,
      SpectraEventListener { received.add("after") },
      priority = 1,
      ignoreCancelled = false,
    )

    bus.post(SimpleEvent())
    assertEquals(listOf("before", "after"), received)
  }
}
