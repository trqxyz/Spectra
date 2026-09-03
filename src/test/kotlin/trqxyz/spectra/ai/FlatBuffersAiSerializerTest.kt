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
package trqxyz.spectra.ai

import io.mockk.every
import io.mockk.mockk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import trqxyz.spectra.data.TickData

class FlatBuffersAiSerializerTest {

  private val serializer = FlatBuffersAiSerializer()

  private data class TickParams(
    val dYaw: Float = 0f,
    val dPitch: Float = 0f,
    val aYaw: Float = 0f,
    val aPitch: Float = 0f,
    val jYaw: Float = 0f,
    val jPitch: Float = 0f,
    val gYaw: Float = 0f,
    val gPitch: Float = 0f,
  )

  private fun mockTick(params: TickParams = TickParams()): TickData = mockk {
    every { deltaYaw } returns params.dYaw
    every { deltaPitch } returns params.dPitch
    every { accelYaw } returns params.aYaw
    every { accelPitch } returns params.aPitch
    every { jerkYaw } returns params.jYaw
    every { jerkPitch } returns params.jPitch
    every { gcdErrorYaw } returns params.gYaw
    every { gcdErrorPitch } returns params.gPitch
  }

  private data class DecodedSequence(val count: Int, val values: List<List<Float>>)

  private fun decodeV2(payload: ByteArray): DecodedSequence {
    assertEquals("SPV2", payload.copyOfRange(4, 8).decodeToString())
    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val root = buffer.getInt(0)
    val vtable = root - buffer.getInt(root)
    val ticksField = buffer.getShort(vtable + 4).toInt() and 0xffff
    val vectorRef = root + ticksField
    val vector = vectorRef + buffer.getInt(vectorRef)
    val count = buffer.getInt(vector)
    val data = vector + Int.SIZE_BYTES
    return DecodedSequence(
      count,
      List(count) { row ->
        List(8) { column ->
          buffer.getFloat(data + row * FlatBuffersAiSerializer.TICK_STRUCT_BYTES + column * 4)
        }
      },
    )
  }

  @Test
  fun `serialize single tick produces valid flatbuffer`() {
    val tick = mockTick(TickParams(dYaw = 1.5f, dPitch = -2.0f, aYaw = 0.1f, aPitch = 0.2f))
    val ticks = arrayOf(tick)

    val payload = serializer.serialize(ticks, 1)
    assertTrue(payload.isNotEmpty())

    val decoded = decodeV2(payload)
    assertEquals(1, decoded.count)
    assertEquals(1.5f, decoded.values[0][0])
    assertEquals(-2.0f, decoded.values[0][1])
    assertEquals(0.1f, decoded.values[0][2])
    assertEquals(0.2f, decoded.values[0][3])
  }

  @Test
  fun `serialize multiple ticks preserves order and values`() {
    val tick0 = mockTick(TickParams(dYaw = 1.0f, dPitch = 2.0f))
    val tick1 = mockTick(TickParams(dYaw = 3.0f, dPitch = 4.0f))
    val tick2 = mockTick(TickParams(dYaw = 5.0f, dPitch = 6.0f))
    val ticks = arrayOf(tick0, tick1, tick2)

    val sequence = decodeV2(serializer.serialize(ticks, 3))
    assertEquals(3, sequence.count)
    assertEquals(1.0f, sequence.values[0][0])
    assertEquals(2.0f, sequence.values[0][1])
    assertEquals(3.0f, sequence.values[1][0])
    assertEquals(4.0f, sequence.values[1][1])
    assertEquals(5.0f, sequence.values[2][0])
    assertEquals(6.0f, sequence.values[2][1])
  }

  @Test
  fun `serialize uses count not array length`() {
    val tick0 = mockTick(TickParams(dYaw = 10.0f))
    val tick1 = mockTick(TickParams(dYaw = 20.0f))
    val tick2 = mockTick(TickParams(dYaw = 30.0f))
    val ticks = arrayOf(tick0, tick1, tick2)

    val sequence = decodeV2(serializer.serialize(ticks, 2))
    assertEquals(2, sequence.count)
    assertEquals(10.0f, sequence.values[0][0])
    assertEquals(20.0f, sequence.values[1][0])
  }

  @Test
  fun `serialize all 8 fields correctly`() {
    val tick =
      mockTick(
        TickParams(
          dYaw = 1.0f,
          dPitch = 2.0f,
          aYaw = 3.0f,
          aPitch = 4.0f,
          jYaw = 5.0f,
          jPitch = 6.0f,
          gYaw = 7.0f,
          gPitch = 8.0f,
        )
      )
    val decoded = decodeV2(serializer.serialize(arrayOf(tick), 1)).values[0]

    assertEquals(1.0f, decoded[0])
    assertEquals(2.0f, decoded[1])
    assertEquals(3.0f, decoded[2])
    assertEquals(4.0f, decoded[3])
    assertEquals(5.0f, decoded[4])
    assertEquals(6.0f, decoded[5])
    assertEquals(7.0f, decoded[6])
    assertEquals(8.0f, decoded[7])
  }

  @Test
  fun `serialize is reusable across calls`() {
    val tick1 = mockTick(TickParams(dYaw = 100.0f))
    val tick2 = mockTick(TickParams(dYaw = 200.0f))

    val buf1 = serializer.serialize(arrayOf(tick1), 1)
    val buf2 = serializer.serialize(arrayOf(tick2), 1)

    val seq1 = decodeV2(buf1)
    val seq2 = decodeV2(buf2)

    assertEquals(100.0f, seq1.values[0][0])
    assertEquals(200.0f, seq2.values[0][0])
  }

  @Test
  fun `serialize rejects invalid count`() {
    val ticks = arrayOf(mockTick())
    assertFailsWith<IllegalArgumentException> { serializer.serialize(ticks, 0) }
    assertFailsWith<IllegalArgumentException> { serializer.serialize(ticks, 2) }
  }
}
