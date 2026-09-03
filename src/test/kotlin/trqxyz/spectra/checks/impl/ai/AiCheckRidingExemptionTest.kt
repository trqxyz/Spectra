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
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiResult
import trqxyz.spectra.ai.AiService
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.checks.CheckManager
import trqxyz.spectra.checks.impl.combat.AimProcessor
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.damage.DamageProcessor
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.entity.CompensatedEntities
import trqxyz.spectra.entity.PacketEntity
import trqxyz.spectra.entity.types.PacketEntitySelf
import trqxyz.spectra.platform.scheduler.TaskHandle
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.player.state.CombatState
import trqxyz.spectra.player.state.MovementState
import trqxyz.spectra.region.RegionProvider
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.utils.data.PacketStateData

class AiCheckRidingExemptionTest {

  @Test
  fun `riding player is not sent to inference`() {
    val fixture = createFixture(riding = true)

    repeat(SEQUENCE) { fixture.check.onPacketReceive(fixture.event) }

    verify(exactly = 0) { fixture.aiService.request(any(), any(), any()) }
  }

  @Test
  fun `player on foot is sent to inference`() {
    val fixture = createFixture(riding = false)

    sendMovements(fixture, SEQUENCE + 1)

    verify(exactly = 1) { fixture.aiService.request(any(), any(), any()) }
  }

  @Test
  fun `online player starts inference when transport becomes available after pairing`() {
    val fixture = createFixture(riding = false, aiInitiallyEnabled = false)

    sendMovements(fixture, SEQUENCE + 1)
    verify(exactly = 0) { fixture.aiService.request(any(), any(), any()) }

    fixture.aiEnabled[0] = true
    sendMovements(fixture, SEQUENCE + 1)

    verify(exactly = 1) { fixture.aiService.request(any(), any(), any()) }
  }

  @Test
  fun `mount mid-window clears accumulated ticks`() {
    val fixture = createFixture(riding = false)

    sendMovements(fixture, 1)
    sendMovements(fixture, SEQUENCE - 1)
    fixture.ridingHolder[0] = mockk(relaxed = true)
    sendMovements(fixture, 1)
    fixture.ridingHolder[0] = null
    sendMovements(fixture, SEQUENCE - 1)

    verify(exactly = 0) { fixture.aiService.request(any(), any(), any()) }

    sendMovements(fixture, 1)

    verify(exactly = 1) { fixture.aiService.request(any(), any(), any()) }
  }

  @Suppress("LongMethod")
  private fun createFixture(riding: Boolean, aiInitiallyEnabled: Boolean = true): Fixture {
    val logger = mockk<Logger>(relaxed = true)
    val plugin = mockk<SpectraPlugin>(relaxed = true)
    every { plugin.logger } returns logger

    val aiService = mockk<AiService>(relaxed = true)
    val aiEnabled = booleanArrayOf(aiInitiallyEnabled)
    every { aiService.isEnabled } answers { aiEnabled[0] }
    every { aiService.request(any(), any(), any()) } returns CompletableFuture<AiResult>()

    val configManager = mockk<ConfigManager>(relaxed = true)
    every { configManager.aiSequence } returns SEQUENCE
    every { configManager.aiStep } returns 1
    every { configManager.aiContinuous } returns false
    every { configManager.isAiEnabled() } returns true
    every { configManager.isAiWorldGuardEnabled() } returns false
    every { configManager.isDebugEnabled() } returns false

    val packetStateData = PacketStateData()
    val player = mockk<Player>(relaxed = true)
    every { player.name } returns "TestPlayer"

    val aimProcessor = mockk<AimProcessor>(relaxed = true)
    val checkManager = mockk<CheckManager>(relaxed = true)
    every { checkManager.getCheck(AimProcessor::class.java) } returns aimProcessor

    val ridingHolder = arrayOfNulls<PacketEntity>(1)
    if (riding) {
      ridingHolder[0] = mockk(relaxed = true)
    }
    val self = mockk<PacketEntitySelf>(relaxed = true)
    every { self.riding } answers { ridingHolder[0] }
    val compensatedEntities = mockk<CompensatedEntities>(relaxed = true)
    every { compensatedEntities.self } returns self

    val spectraPlayer = mockk<SpectraPlayer>(relaxed = true)
    every { spectraPlayer.player } returns player
    every { spectraPlayer.packetStateData } returns packetStateData
    every { spectraPlayer.checkManager } returns checkManager
    every { spectraPlayer.compensatedEntities } returns compensatedEntities
    val movement = MovementState()
    every { spectraPlayer.movement } returns movement
    every { spectraPlayer.combat } returns CombatState(0)

    val scheduler = mockk<SchedulerService>(relaxed = true)
    every { scheduler.runAsync(any()) } answers
      {
        firstArg<Runnable>().run()
        mockk<TaskHandle>(relaxed = true)
      }

    val event = mockk<PacketReceiveEvent>(relaxed = true)
    every { event.packetType } returns PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION

    val check =
      AiCheck(
        spectraPlayer = spectraPlayer,
        plugin = plugin,
        aiService = aiService,
        configManager = configManager,
        regionProvider = mockk<RegionProvider>(relaxed = true),
        alertManager = mockk<AlertManager>(relaxed = true),
        damageProcessor = mockk<DamageProcessor>(relaxed = true),
        debugManager = DebugManager(plugin, configManager),
        scheduler = scheduler,
        decisionHistory = mockk(relaxed = true),
        reportService = mockk(relaxed = true),
      )

    return Fixture(check, aiService, event, ridingHolder, aiEnabled, movement)
  }

  private fun sendMovements(fixture: Fixture, count: Int) {
    repeat(count) {
      fixture.movement.yaw += 1f
      fixture.check.onPacketReceive(fixture.event)
    }
  }

  private data class Fixture(
    val check: AiCheck,
    val aiService: AiService,
    val event: PacketReceiveEvent,
    val ridingHolder: Array<PacketEntity?>,
    val aiEnabled: BooleanArray,
    val movement: MovementState,
  )

  private companion object {
    private const val SEQUENCE = 4
  }
}
