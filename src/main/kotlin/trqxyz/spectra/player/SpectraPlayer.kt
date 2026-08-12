/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 KaelusAI
 *
 * This file contains code derived from GrimAC.
 * The original authors of GrimAC are credited below.
 *
 * Copyright (c) 2021-2026 GrimAC, DefineOutside and contributors.
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.player

import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.util.Vector3d
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.api.event.SpectraEventBus
import trqxyz.spectra.checks.CheckManager
import trqxyz.spectra.checks.impl.ai.DataCollectorManager
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.entity.CompensatedEntities
import trqxyz.spectra.player.state.CombatState
import trqxyz.spectra.player.state.MovementState
import trqxyz.spectra.player.state.TransactionTracker
import trqxyz.spectra.punishment.PunishmentManager
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.server.AIServerProvider
import trqxyz.spectra.utils.data.HeadRotation
import trqxyz.spectra.utils.data.PacketStateData
import trqxyz.spectra.utils.latency.ILatencyUtils
import trqxyz.spectra.utils.latency.LatencyUtils
import trqxyz.spectra.utils.update.RotationUpdate

class SpectraPlayer
@Suppress("LongParameterList")
constructor(
  val player: Player,
  val user: User,
  private val plugin: SpectraPlugin,
  private val configManager: ConfigManager,
  aiSequence: Int,
  alertManager: AlertManager,
  dataCollectorManager: DataCollectorManager,
  aiServerProvider: AIServerProvider,
  val exemptManager: ExemptManager,
  private val scheduler: SchedulerService,
  checkManagerFactory: CheckManager.Factory,
  punishmentManagerFactory: PunishmentManager.Factory,
  val eventBus: SpectraEventBus,
) {
  val uuid: UUID = player.uniqueId
  val packetStateData: PacketStateData = PacketStateData()
  val rotationUpdate: RotationUpdate = RotationUpdate(HeadRotation(), HeadRotation(), 0f, 0f)
  val joinTime: Long = System.currentTimeMillis()

  var entityId: Int = 0
  var gameMode: GameMode = GameMode.SURVIVAL
  var brand: String = "vanilla"
  var isBedrock: Boolean = false

  val isBedrockExempt: Boolean
    get() = configManager.isBedrockExemptEnabled() && isBedrock

  val movement: MovementState = MovementState()
  val combat: CombatState = CombatState(aiSequence + 1)
  val transactions: TransactionTracker = TransactionTracker()

  val pendingTeleports: Queue<TeleportData> = ConcurrentLinkedQueue()
  val pendingRotations: Queue<RotationData> = ConcurrentLinkedQueue()

  val compensatedEntities: CompensatedEntities = CompensatedEntities(this)
  val latencyUtils: ILatencyUtils = LatencyUtils(this, plugin)
  val checkManager: CheckManager = checkManagerFactory.create(this)
  val punishmentManager: PunishmentManager = punishmentManagerFactory.create(this)

  private var cancelDuplicatePacket = true
  private var forceCancelDuplicatePacket = false

  init {
    refreshDuplicatePacketSettings()
  }

  fun isPointThree(): Boolean = user.clientVersion.isOlderThan(ClientVersion.V_1_18_2)

  fun getMovementThreshold(): Double = if (isPointThree()) 0.03 else 0.0002

  fun isCancelDuplicatePacket(): Boolean = cancelDuplicatePacket

  fun isForceCancelDuplicatePacket(): Boolean = forceCancelDuplicatePacket

  fun sendTransaction() {
    transactions.sendTransaction(user)
  }

  fun disconnect(reason: Component) {
    user.sendPacket(
      com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect(reason)
    )
    user.closeConnection()

    scheduler.runSync(player) { player.kick(reason) }
  }

  fun reload() {
    refreshDuplicatePacketSettings()
    punishmentManager.reload()
    checkManager.reloadChecks()
  }

  private fun refreshDuplicatePacketSettings() {
    cancelDuplicatePacket = configManager.cancelDuplicatePacket
    forceCancelDuplicatePacket = configManager.forceCancelDuplicatePacket
  }

  class TeleportData(val location: Vector3d, val flags: RelativeFlag, val transactionId: Int) {
    fun isRelativeX(): Boolean = flags.has(RelativeFlag.X)

    fun isRelativeY(): Boolean = flags.has(RelativeFlag.Y)

    fun isRelativeZ(): Boolean = flags.has(RelativeFlag.Z)
  }

  class RotationData(
    val yaw: Float,
    val pitch: Float,
    val relativeYaw: Boolean,
    val relativePitch: Boolean,
    val transactionId: Int,
  )
}
