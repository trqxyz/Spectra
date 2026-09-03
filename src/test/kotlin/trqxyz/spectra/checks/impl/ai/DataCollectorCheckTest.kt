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

import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import java.util.logging.Logger
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.checks.CheckManager
import trqxyz.spectra.checks.impl.combat.AimProcessor
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.data.DataSession
import trqxyz.spectra.entity.CompensatedEntities
import trqxyz.spectra.entity.types.PacketEntitySelf
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.utils.data.PacketStateData

class DataCollectorCheckTest {

  @Test
  fun `duplicate flying packet does not enter data collection`() {
    val fixture = createFixture()
    fixture.packetStateData.lastPacketWasOnePointSeventeenDuplicate = true

    fixture.check.onPacketReceive(fixture.event)

    verify(exactly = 0) { fixture.session.addTick(any()) }
  }

  @Test
  fun `riding player does not enter data collection`() {
    val fixture = createFixture(riding = true)

    fixture.check.onPacketReceive(fixture.event)

    verify(exactly = 0) { fixture.session.addTick(any()) }
  }

  @Test
  fun `only complete non-zero rotation sample enters data collection`() {
    val fixture = createFixture()

    // The first packet only initializes rotation state and produces an all-zero feature row.
    fixture.check.onPacketReceive(fixture.event)
    fixture.check.onPacketReceive(fixture.event)

    verify(exactly = 1) { fixture.session.addTick(any()) }
  }

  private fun createFixture(riding: Boolean = false): Fixture {
    val logger = mockk<Logger>(relaxed = true)
    val plugin = mockk<SpectraPlugin>(relaxed = true)
    every { plugin.logger } returns logger

    val configManager = mockk<ConfigManager>(relaxed = true)
    every { configManager.aiContinuous } returns true

    val packetStateData = PacketStateData()
    val player = mockk<Player>(relaxed = true)
    every { player.name } returns "TestPlayer"

    val aimProcessor = mockk<AimProcessor>(relaxed = true)
    val checkManager = mockk<CheckManager>(relaxed = true)
    every { checkManager.getCheck(AimProcessor::class.java) } returns aimProcessor

    val self = mockk<PacketEntitySelf>(relaxed = true)
    every { self.riding } returns if (riding) mockk(relaxed = true) else null
    val compensatedEntities = mockk<CompensatedEntities>(relaxed = true)
    every { compensatedEntities.self } returns self

    val uuid = UUID.randomUUID()
    val spectraPlayer = mockk<SpectraPlayer>(relaxed = true)
    every { spectraPlayer.player } returns player
    every { spectraPlayer.uuid } returns uuid
    every { spectraPlayer.packetStateData } returns packetStateData
    every { spectraPlayer.checkManager } returns checkManager
    every { spectraPlayer.compensatedEntities } returns compensatedEntities
    every { spectraPlayer.movement.yaw } returnsMany listOf(0f, 1f)
    every { spectraPlayer.movement.pitch } returns 0f

    val session = mockk<DataSession>(relaxed = true)
    val dataCollectorManager = mockk<DataCollectorManager>(relaxed = true)
    every { dataCollectorManager.getSession(uuid) } returns session

    val event = mockk<PacketReceiveEvent>(relaxed = true)
    every { event.packetType } returns PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION

    val check =
      DataCollectorCheck(
        spectraPlayer = spectraPlayer,
        dataCollectorManager = dataCollectorManager,
        plugin = plugin,
        configManager = configManager,
      )

    return Fixture(check, session, event, packetStateData)
  }

  private data class Fixture(
    val check: DataCollectorCheck,
    val session: DataSession,
    val event: PacketReceiveEvent,
    val packetStateData: PacketStateData,
  )
}
