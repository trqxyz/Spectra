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

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class JacksonAiResponseParserTest {
  private val parser = JacksonAiResponseParser()

  @Test
  fun `parses numeric probability`() {
    val response = parser.parse("""{"probability":0.93}""")
    assertEquals(0.93, response.probability)
    assertEquals("unknown", response.verdict)
    assertFalse(response.accepted)
    assertTrue(response.primary.legacy)
  }

  @Test
  fun `parses textual probability`() {
    val response = parser.parse("""{"probability":"0.75"}""")
    assertEquals(0.75, response.probability)
    assertEquals("unknown", response.verdict)
    assertFalse(response.accepted)
  }

  @Test
  fun `incomplete contract cannot self-authorize an action`() {
    val response =
      parser.parse("""{"probability":0.99,"accepted":true,"verdict":"cheat","status":"ready"}""")

    assertEquals(0.99, response.probability)
    assertEquals("unknown", response.verdict)
    assertFalse(response.accepted)
    assertTrue(response.primary.legacy)
  }

  @Test
  fun `parses complete primary and per-model decision contracts`() {
    val response =
      parser.parse(
        """
        {
          "status":"ready",
          "risk_score":0.82,
          "calibrated_probability":0.94,
          "verdict":"cheat",
          "accepted":true,
          "confidence":0.88,
          "novelty":0.12,
          "model_version":"spectra-pro-v2",
          "window_ticks":120,
          "actionable":true,
          "models":{
            "flash":{
              "status":"ready",
              "risk_score":0.76,
              "calibrated_probability":0.91,
              "verdict":"cheat",
              "accepted":true,
              "confidence":0.84,
              "novelty":0.16,
              "model_version":"spectra-flash-v2",
              "window_ticks":40,
              "actionable":true
            },
            "night":{
              "status":"pending",
              "risk_score":0.0,
              "calibrated_probability":0.5,
              "verdict":"unknown",
              "accepted":false,
              "confidence":0.0,
              "novelty":0.0,
              "model_version":"spectra-night-v2",
              "window_ticks":500,
              "actionable":false
            }
          }
        }
        """
          .trimIndent()
      )

    assertEquals("ready", response.status)
    assertEquals(0.82, response.riskScore)
    assertEquals(0.94, response.calibratedProbability)
    assertEquals("cheat", response.verdict)
    assertTrue(response.accepted)
    assertEquals(0.88, response.confidence)
    assertEquals(0.12, response.novelty)
    assertEquals("spectra-pro-v2", response.modelVersion)
    assertEquals(120, response.windowTicks)
    assertTrue(response.primary.actionable)

    val flash = response.models.getValue("flash")
    assertEquals("ready", flash.status)
    assertEquals(0.76, flash.riskScore)
    assertEquals(0.91, flash.calibratedProbability)
    assertEquals("cheat", flash.verdict)
    assertTrue(flash.accepted)
    assertTrue(flash.actionable)
    assertEquals(40, flash.windowTicks)

    val night = response.models.getValue("night")
    assertEquals("pending", night.status)
    assertEquals("unknown", night.verdict)
    assertFalse(night.accepted)
    assertFalse(night.actionable)
  }

  @Test
  fun `derives display score when response only contains per-model contracts`() {
    val response =
      parser.parse(
        """
        {"models":{"flash":{
          "status":"ready",
          "risk_score":0.5,
          "calibrated_probability":0.73,
          "verdict":"unknown",
          "accepted":false,
          "confidence":0.4,
          "novelty":0.2,
          "model_version":"flash-v2",
          "window_ticks":40
        }}}
        """
          .trimIndent()
      )
    assertEquals(0.73, response.probability)
    assertFalse(response.accepted)
  }

  @Test
  fun `ignores pending model without probability when another model is ready`() {
    val response =
      parser.parse(
        """
        {
          "status":"ready",
          "risk_score":0.999998,
          "calibrated_probability":0.999998,
          "verdict":"cheat",
          "accepted":true,
          "confidence":0.999996,
          "novelty":0.0,
          "model_version":"spectra-pro-v2",
          "window_ticks":120,
          "models":{
            "pro":{
              "status":"ready",
              "risk_score":0.999998,
              "calibrated_probability":0.999998,
              "verdict":"cheat",
              "accepted":true,
              "confidence":0.999996,
              "novelty":0.0,
              "model_version":"spectra-pro-v2",
              "window_ticks":120
            },
            "night":{
              "status":"pending",
              "risk_score":null,
              "calibrated_probability":null,
              "verdict":"unknown",
              "accepted":false,
              "confidence":0.0,
              "novelty":0.0,
              "model_version":"spectra-night-v2",
              "window_ticks":500,
              "probability":null,
              "risk":null,
              "model_probability":null,
              "actionable":false
            }
          }
        }
        """
          .trimIndent()
      )

    assertEquals(0.999998, response.probability)
    assertTrue("pro" in response.models)
    assertFalse("night" in response.models)
  }

  @Test
  fun `rejects ready model without probability`() {
    assertFailsWith<IllegalArgumentException> {
      parser.parse(
        """
        {"probability":0.9,"models":{"night":{"status":"ready","probability":null}}}
        """
          .trimIndent()
      )
    }
  }

  @Test
  fun `complete accepted contract without server action permission stays non-actionable`() {
    val response =
      parser.parse(
        """
        {"status":"ready","risk_score":0.99,"calibrated_probability":0.99,
         "verdict":"cheat","accepted":true,"confidence":0.95,"novelty":0.0,
         "model_version":"v2","window_ticks":40}
        """
          .trimIndent()
      )

    assertTrue(response.accepted)
    assertFalse(response.primary.actionable)
    assertFalse(response.primary.isActionableCheat)
  }

  @Test
  fun `throws for missing probability`() {
    assertFailsWith<IllegalArgumentException> { parser.parse("""{"details":{"sequence":10}}""") }
  }

  @Test
  fun `throws for invalid probability type`() {
    assertFailsWith<IllegalArgumentException> { parser.parse("""{"probability":{"value":0.5}}""") }
  }
}
