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
package trqxyz.spectra.checks.impl.ai

import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.protocol.player.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiResult
import trqxyz.spectra.ai.AiService
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.api.event.AiPredictionEvent
import trqxyz.spectra.api.event.SpectraEventBus
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.damage.DamageProcessor
import trqxyz.spectra.debug.DebugCategory
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.player.state.CombatState
import trqxyz.spectra.punishment.PunishmentManager
import trqxyz.spectra.region.RegionProvider
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.server.AIModelDecision
import trqxyz.spectra.server.AIResponse

class AiCheckResponseTest {

  @Test
  fun `probability debug log uses fixed formatting only when enabled`() {
    val fixture =
      createFixture(debugEnabled = true, enabledCategories = setOf(DebugCategory.AI_PROBABILITY))

    fixture.invokeOnResponse(0.42)

    verify(exactly = 1) {
      fixture.logger.info(
        "[DEBUG | AI_PROBABILITY] [TestPlayer | 1.21.4] Prob: 0.4200 | Buffer: 0.00 -> 0.00 | Damage Multiplier: 1.00"
      )
    }
  }

  @Test
  fun `flag debug string uses compact fixed formatting`() {
    val fixture = createFixture(aiFlag = 0.5, bufferMultiplier = 20.0)

    fixture.invokeOnResponse(decision(0.95, verdict = "cheat", accepted = true))

    verify(exactly = 1) {
      fixture.punishmentManager.handleFlag(fixture.check, "prob=0.95 buffer=1.0")
    }
    assertEquals(0.0, fixture.check.buffer)
  }

  @Test
  fun `ai formatting helpers use stable decimal output`() {
    assertEquals("1.0", formatAiBuffer(1.04))
    assertEquals("0.95", formatAiProbability(0.945))
    assertEquals("0.4200", formatAiProbabilityVerbose(0.42))
  }

  @Test
  fun `unknown decision never changes buffer or triggers an action`() {
    val fixture = createFixture(aiFlag = 0.5, bufferMultiplier = 20.0, enforcementEnabled = true)

    fixture.invokeOnResponse(decision(0.99, verdict = "unknown", accepted = false))

    assertEquals(0.0, fixture.check.buffer)
    verify(exactly = 0) { fixture.punishmentManager.handleFlag(any(), any()) }
    verify(exactly = 0) { fixture.damageProcessor.applyProbability(any(), any()) }
    verify(exactly = 1) { fixture.damageProcessor.reset(any()) }
  }

  @Test
  fun `legacy probability is display only even in enforce mode`() {
    val fixture = createFixture(aiFlag = 0.5, bufferMultiplier = 20.0, enforcementEnabled = true)

    fixture.invokeOnResponse(0.99)

    assertEquals(0.0, fixture.check.buffer)
    verify(exactly = 0) { fixture.punishmentManager.handleFlag(any(), any()) }
    verify(exactly = 0) { fixture.damageProcessor.applyProbability(any(), any()) }
    verify(exactly = 1) { fixture.damageProcessor.reset(any()) }
  }

  @Test
  fun `accepted cheat remains observable but cannot punish in shadow mode`() {
    val fixture = createFixture(aiFlag = 0.5, bufferMultiplier = 20.0, enforcementEnabled = false)

    fixture.invokeOnResponse(decision(0.95, verdict = "cheat", accepted = true))

    assertEquals(1.0, fixture.check.buffer, 1e-9)
    verify(exactly = 0) { fixture.punishmentManager.handleFlag(any(), any()) }
    verify(exactly = 0) { fixture.damageProcessor.applyProbability(any(), any()) }
    verify(exactly = 1) { fixture.damageProcessor.reset(any()) }
    verify(exactly = 1) { fixture.eventBus.post(any<AiPredictionEvent>()) }
  }

  @Test
  fun `backend shadow permission blocks action even when plugin is set to enforce`() {
    val fixture = createFixture(aiFlag = 0.5, bufferMultiplier = 20.0, enforcementEnabled = true)

    fixture.invokeOnResponse(decision(0.95, verdict = "cheat", accepted = true, actionable = false))

    assertEquals(1.0, fixture.check.buffer, 1e-9)
    verify(exactly = 0) { fixture.punishmentManager.handleFlag(any(), any()) }
    verify(exactly = 0) { fixture.damageProcessor.applyProbability(any(), any()) }
    verify(exactly = 1) { fixture.damageProcessor.reset(any()) }
  }

  @Test
  fun `per-model pending score is display-only and cannot increase model buffer`() {
    val fixture = createFixture(aiFlag = 10.0, bufferMultiplier = 20.0, enforcementEnabled = true)
    val flash = decision(0.95, verdict = "cheat", accepted = true).primary
    val pendingNight =
      decision(0.99, status = "pending", verdict = "unknown", accepted = false).primary
    val response =
      AIResponse(primary = pendingNight, models = mapOf("flash" to flash, "night" to pendingNight))

    fixture.invokeOnResponse(response)

    assertEquals(0.99, fixture.check.lastProbability)
    assertEquals(1.0, fixture.check.buffer, 1e-9)
    verify(exactly = 0) { fixture.punishmentManager.handleFlag(any(), any()) }
    verify(exactly = 1) { fixture.damageProcessor.applyProbability(any(), 0.95) }
  }

  @Test
  fun `restored persistent buffer seeds every model on first multi-model response`() {
    val fixture = createFixture(aiFlag = 10.0, bufferMultiplier = 20.0, enforcementEnabled = false)
    fixture.check.restoreBuffer(4.0)
    val acceptedLegit = decision(0.01, verdict = "legit", accepted = true).primary
    val response =
      AIResponse(
        primary = acceptedLegit,
        models = mapOf("flash" to acceptedLegit, "night" to acceptedLegit),
      )

    fixture.invokeOnResponse(response)

    assertEquals(3.75, fixture.check.buffer, 1e-9)
  }

  private fun createFixture(
    debugEnabled: Boolean = false,
    enabledCategories: Set<DebugCategory> = emptySet(),
    aiFlag: Double = 10.0,
    bufferMultiplier: Double = 1.0,
    enforcementEnabled: Boolean = true,
  ): Fixture {
    val logger = mockk<Logger>(relaxed = true)
    val plugin = mockk<SpectraPlugin>(relaxed = true)
    every { plugin.logger } returns logger

    val aiService = mockk<AiService>(relaxed = true)
    every { aiService.isEnabled } returns true

    val configManager = mockk<ConfigManager>(relaxed = true)
    every { configManager.aiSequence } returns 40
    every { configManager.aiStep } returns 1
    every { configManager.aiFlag } returns aiFlag
    every { configManager.aiResetOnFlag } returns 0.0
    every { configManager.aiBufferMultiplier } returns bufferMultiplier
    every { configManager.aiBufferDecrease } returns 0.25
    every { configManager.suspiciousAlertsBuffer } returns 25.0
    every { configManager.isAiEnforcementEnabled() } returns enforcementEnabled
    every { configManager.enabledDebugCategories } returns enabledCategories
    every { configManager.isDebugEnabled() } returns debugEnabled

    val player = mockk<Player>(relaxed = true)
    every { player.name } returns "TestPlayer"
    every { player.uniqueId } returns UUID.fromString("00000000-0000-0000-0000-000000000001")

    val eventBus = mockk<SpectraEventBus>(relaxed = true)
    val punishmentManager = mockk<PunishmentManager>(relaxed = true)
    val damageProcessor = mockk<DamageProcessor>(relaxed = true)
    val combat = CombatState(0)

    val user = mockk<User>(relaxed = true)
    every { user.clientVersion } returns ClientVersion.V_1_21_4

    val spectraPlayer = mockk<SpectraPlayer>(relaxed = true)
    every { spectraPlayer.player } returns player
    every { spectraPlayer.uuid } returns player.uniqueId
    every { spectraPlayer.eventBus } returns eventBus
    every { spectraPlayer.punishmentManager } returns punishmentManager
    every { spectraPlayer.combat } returns combat
    every { spectraPlayer.user } returns user

    val check =
      AiCheck(
        spectraPlayer = spectraPlayer,
        plugin = plugin,
        aiService = aiService,
        configManager = configManager,
        regionProvider = mockk<RegionProvider>(relaxed = true),
        alertManager = mockk<AlertManager>(relaxed = true),
        damageProcessor = damageProcessor,
        debugManager = DebugManager(plugin, configManager),
        scheduler = mockk<SchedulerService>(relaxed = true),
        decisionHistory = mockk(relaxed = true),
        reportService = mockk(relaxed = true),
      )

    return Fixture(check, logger, punishmentManager, damageProcessor, eventBus)
  }

  private data class Fixture(
    val check: AiCheck,
    val logger: Logger,
    val punishmentManager: PunishmentManager,
    val damageProcessor: DamageProcessor,
    val eventBus: SpectraEventBus,
  ) {
    fun invokeOnResponse(probability: Double) {
      invokeOnResponse(AIResponse(probability), """{"probability":$probability}""")
    }

    fun invokeOnResponse(response: AIResponse) {
      invokeOnResponse(response, "{}")
    }

    private fun invokeOnResponse(response: AIResponse, raw: String) {
      onResponseMethod.invoke(check, AiResult(response, raw, null, false))
    }
  }

  private companion object {
    val onResponseMethod: Method =
      AiCheck::class.java.getDeclaredMethod("onResponse", AiResult::class.java).apply {
        isAccessible = true
      }

    fun decision(
      probability: Double,
      status: String = "ready",
      verdict: String,
      accepted: Boolean,
      actionable: Boolean = true,
    ): AIResponse =
      AIResponse(
        AIModelDecision(
          status = status,
          riskScore = probability,
          calibratedProbability = probability,
          verdict = verdict,
          accepted = accepted,
          confidence = if (accepted) 0.95 else 0.0,
          novelty = 0.0,
          modelVersion = "test-v2",
          windowTicks = 40,
          actionable = actionable,
        )
      )
  }
}
