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
package trqxyz.spectra.server

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ApiCooldownTest {

  @Test
  fun `initially not waiting`() {
    val cooldown = ApiCooldown(5, 60, 2.0)
    assertFalse(cooldown.isWaiting())
  }

  @Test
  fun `after failure enters waiting state`() {
    val cooldown = ApiCooldown(5, 60, 2.0)
    cooldown.recordFailure()
    assertTrue(cooldown.isWaiting())
  }

  @Test
  fun `after success resets waiting state`() {
    val cooldown = ApiCooldown(5, 60, 2.0)
    cooldown.recordFailure()
    assertTrue(cooldown.isWaiting())

    cooldown.recordSuccess()
    assertFalse(cooldown.isWaiting())
  }

  @Test
  fun `backoff increases with each failure`() {
    val cooldown = ApiCooldown(1, 10, 2.0)

    cooldown.recordFailure()
    assertTrue(cooldown.isWaiting())

    cooldown.recordSuccess()
    assertFalse(cooldown.isWaiting())

    cooldown.recordFailure()
    cooldown.recordFailure()
    assertTrue(cooldown.isWaiting())
  }

  @Test
  fun `backoff does not exceed max duration`() {
    val cooldown = ApiCooldown(1, 2, 10.0)

    cooldown.recordFailure()
    cooldown.recordFailure()
    cooldown.recordFailure()

    assertTrue(cooldown.isWaiting())
  }

  @Test
  fun `success after multiple failures fully resets`() {
    val cooldown = ApiCooldown(1, 60, 2.0)
    cooldown.recordFailure()
    cooldown.recordFailure()
    cooldown.recordFailure()
    assertTrue(cooldown.isWaiting())

    cooldown.recordSuccess()
    assertFalse(cooldown.isWaiting())

    cooldown.recordFailure()
    assertTrue(cooldown.isWaiting())
  }
}
