/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 KaelusAI
 *
 * Spectra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package trqxyz.spectra.server

import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.plugin.configuration.PluginMeta
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiRequestContext

class AIServerRequestHeadersTest {

  @Test
  fun `http authentication status codes retain their meanings`() {
    assertEquals(AIServer.ResponseCode.UNAUTHORIZED, AIServer.ResponseCode.fromStatusCode(401))
    assertEquals(AIServer.ResponseCode.FORBIDDEN, AIServer.ResponseCode.fromStatusCode(403))
  }

  @Test
  fun `request sends bearer and player stream identity headers`() {
    val plugin = mockk<SpectraPlugin>()
    val pluginMeta = mockk<PluginMeta>()
    every { plugin.pluginMeta } returns pluginMeta
    every { pluginMeta.version } returns "test-version"
    val server =
      AIServer(plugin, "https://ai.example.test/v1/inference", API_KEY, ApiCooldown(1, 2, 2.0))
    val context =
      AiRequestContext(
        playerName = "TestPlayer",
        models = "flash,pro,night",
        playerUuid = "00000000-0000-0000-0000-000000000001",
        streamId = "00000000-0000-0000-0000-000000000002",
        chunkSequence = 17,
      )

    val request = server.buildRequest(byteArrayOf(1, 2, 3), batch = false, context = context)

    assertEquals("Bearer $API_KEY", request.headers().firstValue("Authorization").orElseThrow())
    assertFalse(request.headers().firstValue("X-API-Key").isPresent)
    assertEquals(context.playerUuid, request.headers().firstValue("X-Player-UUID").orElseThrow())
    assertEquals(context.streamId, request.headers().firstValue("X-Stream-ID").orElseThrow())
    assertEquals("17", request.headers().firstValue("X-Chunk-Sequence").orElseThrow())
    assertTrue(request.headers().firstValue("User-Agent").orElseThrow().startsWith("Spectra/"))
    assertFalse(request.headers().firstValue("X-Player-Id").isPresent)
    assertFalse(request.uri().toString().contains(API_KEY))
  }

  @Test
  fun `plain http is restricted to loopback inference servers`() {
    assertEquals("localhost", AIServer.validateServerUri("http://localhost:8000/v1/inference").host)
    assertEquals("127.0.0.1", AIServer.validateServerUri("http://127.0.0.1:8000/v1/inference").host)
    assertFailsWith<IllegalArgumentException> {
      AIServer.validateServerUri("http://ai.example.test/v1/inference")
    }
    assertFailsWith<IllegalArgumentException> {
      AIServer.validateServerUri("https://user:secret@ai.example.test/v1/inference")
    }
  }

  @Test
  fun `invalid batches fail as futures before any network request`() {
    val plugin = mockk<SpectraPlugin>()
    val pluginMeta = mockk<PluginMeta>()
    every { plugin.pluginMeta } returns pluginMeta
    every { pluginMeta.version } returns "test-version"
    val server =
      AIServer(plugin, "https://ai.example.test/v1/inference", API_KEY, ApiCooldown(1, 2, 2.0))

    val error =
      assertFailsWith<ExecutionException> {
          server.sendBatch(List(AIServer.BATCH_MAX_ITEMS + 1) { byteArrayOf(1) }).get()
        }
        .cause as AIServer.RequestException

    assertEquals(AIServer.ResponseCode.INVALID_SEQUENCE, error.code)
  }

  private companion object {
    const val API_KEY = "spectra_test_api_key_1234567890"
  }
}
