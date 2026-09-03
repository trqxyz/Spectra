/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
 *
 * Spectra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package trqxyz.spectra.ai

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

class PlayerRequestStreamTest {

  @Test
  fun `one game session keeps stream id and increments chunk sequence`() {
    val stream =
      PlayerRequestStream(
        playerUuid = "00000000-0000-0000-0000-000000000001",
        streamId = "stream-a",
      )

    val first = stream.next("Player", "flash,pro")
    stream.acknowledge(first)
    val second = stream.next("Player", "flash,pro")

    assertEquals("00000000-0000-0000-0000-000000000001", first.playerUuid)
    assertEquals("stream-a", first.streamId)
    assertEquals(0L, first.chunkSequence)
    assertEquals("Player", first.playerName)
    assertEquals("stream-a", second.streamId)
    assertEquals(1L, second.chunkSequence)
  }

  @Test
  fun `new game session gets a fresh stream id`() {
    val uuid = "00000000-0000-0000-0000-000000000001"
    assertNotEquals(PlayerRequestStream(uuid).streamId, PlayerRequestStream(uuid).streamId)
  }
}
