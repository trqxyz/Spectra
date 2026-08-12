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
package trqxyz.spectra.ai

import com.google.flatbuffers.FlatBufferBuilder
import trqxyz.spectra.data.TickData
import trqxyz.spectra.flatbuffers.TickDataSequence

class FlatBuffersAiSerializer : AiSerializer {
  override fun serialize(ticks: Array<TickData>, count: Int): ByteArray {
    require(count in 1..ticks.size) { "count must be between 1 and ticks.size" }
    val builder = BUILDER.get()
    builder.clear()

    // FlatBuffers writes backwards. Each TickData v2 struct is eight inline
    // float32 values, and the vector is therefore one contiguous memory range.
    builder.startVector(TICK_STRUCT_BYTES, count, Float.SIZE_BYTES)
    for (i in count - 1 downTo 0) {
      val tick = ticks[i]
      builder.putFloat(tick.gcdErrorPitch)
      builder.putFloat(tick.gcdErrorYaw)
      builder.putFloat(tick.jerkPitch)
      builder.putFloat(tick.jerkYaw)
      builder.putFloat(tick.accelPitch)
      builder.putFloat(tick.accelYaw)
      builder.putFloat(tick.deltaPitch)
      builder.putFloat(tick.deltaYaw)
    }
    val ticksVector = builder.endVector()

    TickDataSequence.startTickDataSequence(builder)
    TickDataSequence.addTicks(builder, ticksVector)
    val sequenceOffset = TickDataSequence.endTickDataSequence(builder)
    builder.finish(sequenceOffset, FILE_IDENTIFIER)

    return builder.sizedByteArray()
  }

  companion object {
    internal const val FILE_IDENTIFIER = "SPV2"
    internal const val TICK_STRUCT_BYTES = 8 * Float.SIZE_BYTES
    private val BUILDER: ThreadLocal<FlatBufferBuilder> =
      ThreadLocal.withInitial { FlatBufferBuilder(4096) }
  }
}
