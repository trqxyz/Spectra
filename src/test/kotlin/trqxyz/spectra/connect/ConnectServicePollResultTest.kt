/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
 *
 * Spectra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package trqxyz.spectra.connect

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager

class ConnectServicePollResultTest {
  private val service = ConnectService(mockk<SpectraPlugin>(), mockk<ConfigManager>())
  private val mapper = ObjectMapper()

  @Test
  fun `approved device response accepts new api_key field`() {
    val result =
      service.parsePollResult(
        200,
        mapper.readTree(
          """{"status":"approved","api_key":"spectra_new","server":{"id":"id-1","name":"Node"}}"""
        ),
      )

    val approved = assertIs<PollResult.Approved>(result)
    assertEquals("spectra_new", approved.secretKey)
    assertEquals("id-1", approved.serverId)
  }

  @Test
  fun `approved device response keeps secret_key compatibility`() {
    val result =
      service.parsePollResult(
        200,
        mapper.readTree("""{"status":"approved","secret_key":"spectra_legacy"}"""),
      )

    assertEquals("spectra_legacy", assertIs<PollResult.Approved>(result).secretKey)
  }
}
