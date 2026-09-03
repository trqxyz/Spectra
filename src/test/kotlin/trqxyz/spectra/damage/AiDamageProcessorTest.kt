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
package trqxyz.spectra.damage

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AiDamageProcessorTest {

  @Test
  fun `below threshold returns multiplier 1`() {
    assertEquals(1.0, AiDamageProcessor.computeMultiplier(0.5, 0.9, 1.0))
  }

  @Test
  fun `at threshold returns multiplier 1`() {
    assertEquals(1.0, AiDamageProcessor.computeMultiplier(0.9, 0.9, 1.0), 1e-9)
  }

  @Test
  fun `above threshold reduces damage proportionally`() {
    assertEquals(0.5, AiDamageProcessor.computeMultiplier(0.95, 0.9, 1.0), 1e-9)
  }

  @Test
  fun `probability 1_0 with multiplier 1 gives zero damage`() {
    assertEquals(0.0, AiDamageProcessor.computeMultiplier(1.0, 0.9, 1.0), 1e-9)
  }

  @Test
  fun `reduction capped at 1 when multiplier is high`() {
    assertEquals(0.0, AiDamageProcessor.computeMultiplier(0.95, 0.9, 5.0), 1e-9)
  }

  @Test
  fun `low multiplier gives partial reduction`() {
    assertEquals(0.75, AiDamageProcessor.computeMultiplier(0.9, 0.8, 0.5), 1e-9)
  }

  @Test
  fun `zero probability returns multiplier 1`() {
    assertEquals(1.0, AiDamageProcessor.computeMultiplier(0.0, 0.9, 1.0))
  }

  @Test
  fun `threshold 0 means any probability reduces damage`() {
    assertEquals(0.5, AiDamageProcessor.computeMultiplier(0.5, 0.0, 1.0), 1e-9)
  }
}
