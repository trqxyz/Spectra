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
package trqxyz.spectra.checks.impl.ai

import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.reflect.Field
import java.util.ArrayDeque
import java.util.logging.Logger
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiService
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.damage.DamageProcessor
import trqxyz.spectra.data.TickData
import trqxyz.spectra.debug.DebugCategory
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.region.RegionProvider
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.utils.data.PacketStateData

class AiCheckDuplicatePacketTest {

  @Test
  fun `duplicate flying packet is ignored before ai request`() {
    val fixture = createFixture()

    fixture.check.onPacketReceive(fixture.event)

    verify(exactly = 0) { fixture.aiService.request(any(), any(), any()) }
  }

  @Test
  fun `duplicate flying packet does not log to console when debug is disabled`() {
    val fixture = createFixture()

    fixture.check.onPacketReceive(fixture.event)

    verify(exactly = 0) { fixture.logger.info(any<String>()) }
  }

  @Test
  fun `duplicate flying packet does not enter ai tick sequence`() {
    val fixture = createFixture()

    fixture.check.onPacketReceive(fixture.event)

    assertEquals(0, fixture.tickSequenceSize())
  }

  @Test
  fun `duplicate flying packet does not produce duplication debug log`() {
    val fixture =
      createFixture(
        debugEnabled = true,
        enabledCategories = setOf(DebugCategory.PACKET_DUPLICATION),
      )

    fixture.check.onPacketReceive(fixture.event)

    verify(exactly = 0) { fixture.logger.info(any<String>()) }
  }

  private fun createFixture(
    debugEnabled: Boolean = false,
    enabledCategories: Set<DebugCategory> = emptySet(),
  ): Fixture {
    val logger = mockk<Logger>(relaxed = true)
    val plugin = mockk<SpectraPlugin>(relaxed = true)
    every { plugin.logger } returns logger

    val aiService = mockk<AiService>(relaxed = true)
    every { aiService.isEnabled } returns true

    val configManager = mockk<ConfigManager>(relaxed = true)
    every { configManager.aiSequence } returns 40
    every { configManager.aiStep } returns 1
    every { configManager.aiFlag } returns 1.0
    every { configManager.aiResetOnFlag } returns 0.0
    every { configManager.aiBufferMultiplier } returns 1.0
    every { configManager.aiBufferDecrease } returns 0.25
    every { configManager.suspiciousAlertsBuffer } returns 25.0
    every { configManager.enabledDebugCategories } returns enabledCategories
    every { configManager.isAiEnabled() } returns true
    every { configManager.isDebugEnabled() } returns debugEnabled

    val packetStateData = PacketStateData().apply { lastPacketWasOnePointSeventeenDuplicate = true }
    val player = mockk<Player>(relaxed = true)
    every { player.name } returns "TestPlayer"

    val spectraPlayer = mockk<SpectraPlayer>(relaxed = true)
    every { spectraPlayer.player } returns player
    every { spectraPlayer.packetStateData } returns packetStateData
    every { spectraPlayer.compensatedEntities.self.riding } returns null

    val event = mockk<PacketReceiveEvent>(relaxed = true)
    every { event.packetType } returns PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION

    val debugManager = DebugManager(plugin, configManager)
    val check =
      AiCheck(
        spectraPlayer = spectraPlayer,
        plugin = plugin,
        aiService = aiService,
        configManager = configManager,
        regionProvider = mockk<RegionProvider>(relaxed = true),
        alertManager = mockk<AlertManager>(relaxed = true),
        damageProcessor = mockk<DamageProcessor>(relaxed = true),
        debugManager = debugManager,
        scheduler = mockk<SchedulerService>(relaxed = true),
        decisionHistory = mockk(relaxed = true),
        reportService = mockk(relaxed = true),
      )

    return Fixture(check, aiService, event, logger)
  }

  private data class Fixture(
    val check: AiCheck,
    val aiService: AiService,
    val event: PacketReceiveEvent,
    val logger: Logger,
  ) {
    fun tickSequenceSize(): Int {
      @Suppress("UNCHECKED_CAST") val ticks = ticksField.get(check) as ArrayDeque<TickData>
      return ticks.size
    }
  }

  private companion object {
    val ticksField: Field =
      AiCheck::class.java.getDeclaredField("ticks").apply { isAccessible = true }
  }
}
