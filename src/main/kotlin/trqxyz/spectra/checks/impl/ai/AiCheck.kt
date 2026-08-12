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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying
import java.util.ArrayDeque
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiDecisionHistory
import trqxyz.spectra.ai.AiResult
import trqxyz.spectra.ai.AiService
import trqxyz.spectra.ai.PlayerRequestStream
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.alert.AlertType
import trqxyz.spectra.api.event.AiPredictionEvent
import trqxyz.spectra.checks.AbstractCheck
import trqxyz.spectra.checks.CheckData
import trqxyz.spectra.checks.CheckFactory
import trqxyz.spectra.checks.Reloadable
import trqxyz.spectra.checks.type.PacketCheck
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.connect.RemoteConfigService
import trqxyz.spectra.damage.DamageProcessor
import trqxyz.spectra.data.AiRotationState
import trqxyz.spectra.data.TickData
import trqxyz.spectra.debug.DebugCategory
import trqxyz.spectra.debug.DebugManager
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.region.RegionProvider
import trqxyz.spectra.report.ReportModelResult
import trqxyz.spectra.report.ReportService
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.server.AIServer
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil

@CheckData(name = "AI (Aim)")
class AiCheck(
  spectraPlayer: SpectraPlayer,
  private val plugin: SpectraPlugin,
  private val aiService: AiService,
  private val configManager: ConfigManager,
  private val regionProvider: RegionProvider,
  private val alertManager: AlertManager,
  private val damageProcessor: DamageProcessor,
  private val debugManager: DebugManager,
  private val scheduler: SchedulerService,
  private val decisionHistory: AiDecisionHistory,
  private val remoteConfigService: RemoteConfigService,
  private val reportService: ReportService,
) : AbstractCheck(spectraPlayer), PacketCheck, Reloadable {
  private var step: Int = 0
  private var aiEnabled = false
  private var ticks: ArrayDeque<TickData> = ArrayDeque()
  private val snapshotBuffer: AtomicReference<Array<TickData?>?> = AtomicReference()
  private var requestTail: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
  private val pendingRequests = AtomicInteger()
  private val resetStreamOnNextRequest = AtomicBoolean()
  @Volatile private var requestStream = PlayerRequestStream(spectraPlayer.uuid.toString())
  private var ticksStep = 0

  private val violationBuffer = AiViolationBuffer()

  val buffer: Double
    get() = violationBuffer.value

  fun restoreBuffer(value: Double) = violationBuffer.restore(value)

  var lastProbability: Double = 0.0
    private set

  var prob90: Int = 0

  // Canonical AI feature computer, byte-identical to the training data collector
  // (wrapped yaw delta etc.). Updated continuously so features never drift.
  private val rotationState = AiRotationState()

  private var flag = 0.0
  private var bufferResetOnFlag = 0.0
  private var bufferMultiplier = 0.0
  private var bufferDecrease = 0.0
  private var suspiciousAlertBuffer = 0.0

  init {
    reload()
  }

  interface Factory : CheckFactory {
    override fun create(player: SpectraPlayer): AiCheck
  }

  override fun reload() {
    aiEnabled = aiService.isEnabled

    if (ticks.isEmpty() || ticks.size != configManager.aiSequence) {
      ticks = ArrayDeque(configManager.aiSequence)
    }

    step = configManager.aiStep
    flag = configManager.aiFlag
    bufferResetOnFlag = configManager.aiResetOnFlag
    bufferMultiplier = configManager.aiBufferMultiplier
    bufferDecrease = configManager.aiBufferDecrease
    suspiciousAlertBuffer = configManager.suspiciousAlertsBuffer
  }

  override fun onPacketReceive(event: PacketReceiveEvent) {
    if (!aiEnabled || !configManager.isAiEnabled()) return
    if (!WrapperPlayClientPlayerFlying.isFlying(event.packetType)) return
    val spectraPlayer = spectraPlayer

    val sequence = configManager.aiSequence

    if (spectraPlayer.compensatedEntities.self.riding != null) {
      // Vehicle rotation is not the player's aim; the collector skips it too, so
      // do not advance the feature state here.
      ticks.clear()
      ticksStep = 0
      return
    }

    // Advance the wrapped feature state on every rotation (matching the collector)
    // so deltas/accel/mode stay consistent across combat gaps.
    val tick = rotationState.update(spectraPlayer.movement.yaw, spectraPlayer.movement.pitch)

    if (
      spectraPlayer.packetStateData.lastPacketWasTeleport ||
        spectraPlayer.packetStateData.lastPacketWasServerRotation
    ) {
      return
    }

    if (!configManager.aiContinuous && spectraPlayer.combat.ticksSinceAttack > sequence) {
      if (ticks.isNotEmpty()) {
        ticks.clear()
      }
      ticksStep = 0
      return
    }

    ticks.addLast(tick)
    ticksStep++

    while (ticks.size > sequence) {
      ticks.removeFirst()
    }

    if (ticks.size == sequence && ticksStep >= step) {
      trySendWindow()
      ticksStep = 0
    }
  }

  private fun trySendWindow() {
    if (
      configManager.isAiWorldGuardEnabled() &&
        regionProvider.isPlayerInDisabledRegion(spectraPlayer.player)
    ) {
      debugManager.log(
        DebugCategory.WORLDGUARD,
        "Player ${spectraPlayer.player.name} is in a disabled region. Skipping AI check.",
      )
      return
    }
    sendData()
  }

  private fun sendData() {
    if (ticks.isEmpty() || !aiEnabled || !configManager.isAiEnabled()) {
      return
    }

    if (!reserveOutboundSlot()) return

    val spectraPlayer = spectraPlayer
    val count = ticks.size
    val snapshot = copyTicksToSnapshot(count)
    val resetStream = resetStreamOnNextRequest.getAndSet(false)

    val player = spectraPlayer.player
    val playerName = player.name
    requestTail =
      requestTail
        .handle { _, _ -> null }
        .thenComposeAsync(
          {
            try {
              remoteConfigService.refreshIfDue()
              @Suppress("UNCHECKED_CAST") val requestTicks = snapshot as Array<TickData>
              if (resetStream) {
                requestStream = PlayerRequestStream(spectraPlayer.uuid.toString())
              }
              val context = requestStream.next(playerName, configManager.aiModels)
              aiService
                .request(requestTicks, count, context)
                .thenAcceptAsync({ parsed ->
                  requestStream.acknowledge(context)
                  onResponse(parsed)
                }) { runnable ->
                  scheduler.runSync(player, runnable)
                }
                .exceptionally { error ->
                  val cause = (error as? java.util.concurrent.CompletionException)?.cause ?: error
                  if (
                    cause is AIServer.RequestException &&
                      cause.code == AIServer.ResponseCode.WAITING
                  ) {
                    requestStream.acknowledge(context)
                  } else {
                    requestStream = PlayerRequestStream(spectraPlayer.uuid.toString())
                  }
                  scheduler.runSync(player, Runnable { onError(error) })
                  null
                }
            } catch (e: Exception) {
              plugin.logger.warning("[AiCheck] Failed to send data for $playerName: ${e.message}")
              CompletableFuture.completedFuture(null)
            }
          },
          { runnable -> scheduler.runAsync(runnable) },
        )
        .whenComplete { _, _ ->
          releaseSnapshot(snapshot, count)
          pendingRequests.decrementAndGet()
        }
  }

  private fun reserveOutboundSlot(): Boolean {
    val maxPending = configManager.outboundMaxPendingPerPlayer.coerceAtLeast(1)
    if (pendingRequests.incrementAndGet() <= maxPending) return true

    pendingRequests.decrementAndGet()
    // A dropped window creates a gap. The next admitted window starts a new
    // stream instead of pretending it is consecutive with older chunks.
    resetStreamOnNextRequest.set(true)
    debugManager.log(
      DebugCategory.AI_API_SERVICE_UNAVAILABLE,
      "[AiCheck] Outbound queue full for ${spectraPlayer.player.name}; dropping window.",
    )
    return false
  }

  private fun copyTicksToSnapshot(count: Int): Array<TickData?> {
    val snapshot = borrowSnapshot(count)
    var index = 0
    for (tick in ticks) snapshot[index++] = tick
    return snapshot
  }

  private fun onResponse(parsed: AiResult) {
    val spectraPlayer = spectraPlayer
    if (parsed.disabled) {
      lastProbability = 0.0
      damageProcessor.reset(spectraPlayer)
      return
    }

    if (parsed.hasParseError()) {
      plugin.logger.warning(
        "[AiCheck] Error parsing API response: ${parsed.parseError?.message}. Response Body: ${parsed.raw}"
      )
      lastProbability = 0.0
      damageProcessor.reset(spectraPlayer)
      return
    }

    val apiResponse = parsed.response

    if (apiResponse == null) {
      plugin.logger.warning(
        "[AiCheck] API response is missing probability. Response: ${parsed.raw}"
      )
      lastProbability = 0.0
      damageProcessor.reset(spectraPlayer)
      return
    }

    val models = apiResponse.models
    val probability =
      if (models.isNotEmpty()) {
        models.values.maxOfOrNull { it.calibratedProbability } ?: apiResponse.probability
      } else {
        apiResponse.calibratedProbability
      }
    lastProbability = probability

    val enforcementEnabled = configManager.isAiEnforcementEnabled()
    val actionableProbability =
      if (models.isNotEmpty()) {
        models.values.filter { it.isActionableCheat }.maxOfOrNull { it.calibratedProbability }
      } else {
        apiResponse.primary.takeIf { it.isActionableCheat }?.calibratedProbability
      }
    if (enforcementEnabled && actionableProbability != null) {
      damageProcessor.applyProbability(spectraPlayer, actionableProbability)
    } else {
      damageProcessor.reset(spectraPlayer)
    }

    if (probability > 0.9) {
      prob90++
    }

    val oldBuffer = buffer
    val flag = configManager.aiFlag
    val bufferResetOnFlag = configManager.aiResetOnFlag
    val bufferMultiplier = configManager.aiBufferMultiplier
    val bufferDecrease = configManager.aiBufferDecrease
    val suspiciousAlertBuffer = configManager.suspiciousAlertsBuffer
    val bufferPolicy =
      AiBufferPolicy(enforcementEnabled, flag, bufferResetOnFlag, bufferMultiplier, bufferDecrease)
    val flagged =
      if (models.isNotEmpty()) {
        violationBuffer.updateModels(models, bufferPolicy) { model, modelProbability, modelBuffer ->
          flag("[${model.uppercase()}] " + buildAiFlagDebug(modelProbability, modelBuffer))
        }
      } else {
        violationBuffer.updatePrimary(apiResponse.primary, bufferPolicy, ::flagPrimary)
      }

    if (models.isNotEmpty()) {
      for ((model, decision) in models) {
        decisionHistory.record(
          spectraPlayer.uuid,
          spectraPlayer.player.name,
          model,
          decision,
          violationBuffer.modelValue(model),
        )
      }
    } else {
      decisionHistory.record(
        spectraPlayer.uuid,
        spectraPlayer.player.name,
        "primary",
        apiResponse.primary,
        buffer,
      )
    }

    if (flagged && configManager.reportsEnabled && configManager.reportAiEnabled) {
      val reportModels =
        if (models.isNotEmpty()) {
          models.map { (model, decision) ->
            ReportModelResult(
              model,
              decision.status,
              decision.calibratedProbability,
              decision.verdict,
              decision.accepted,
              decision.actionable,
              decision.confidence,
              decision.novelty,
              violationBuffer.modelValue(model),
              decision.modelVersion,
            )
          }
        } else {
          val decision = apiResponse.primary
          listOf(
            ReportModelResult(
              "primary",
              decision.status,
              decision.calibratedProbability,
              decision.verdict,
              decision.accepted,
              decision.actionable,
              decision.confidence,
              decision.novelty,
              buffer,
              decision.modelVersion,
            )
          )
        }
      scheduler.runAsync {
        reportService.createAi(spectraPlayer.uuid, spectraPlayer.player.name, reportModels)
      }
    }

    if (buffer > suspiciousAlertBuffer && oldBuffer <= suspiciousAlertBuffer) {
      alertManager.send(
        MessageUtil.getMessage(
          Message.SUSPICIOUS_ALERT_TRIGGERED,
          "player",
          spectraPlayer.player.name,
          "buffer",
          formatAiBuffer(buffer),
        ),
        AlertType.SUSPICIOUS,
      )
    }

    if (debugManager.isEnabled(DebugCategory.AI_PROBABILITY)) {
      debugManager.log(
        DebugCategory.AI_PROBABILITY,
        buildAiProbabilityDebugMessage(
          playerName =
            "${spectraPlayer.player.name} | ${spectraPlayer.user.clientVersion.releaseName}",
          probability = probability,
          oldBuffer = oldBuffer,
          newBuffer = buffer,
          damageMultiplier = spectraPlayer.combat.damageMultiplier,
        ),
      )
    }

    spectraPlayer.eventBus.post(
      AiPredictionEvent(
        spectraPlayer.uuid,
        spectraPlayer.player.name,
        checkName,
        probability,
        oldBuffer,
        buffer,
        spectraPlayer.combat.damageMultiplier,
        prob90,
        flagged,
      )
    )
  }

  private fun flagPrimary(probability: Double, currentBuffer: Double) {
    flag(buildAiFlagDebug(probability, currentBuffer))
  }

  private fun onError(error: Throwable): Void? {
    lastProbability = 0.0
    val spectraPlayer = spectraPlayer
    damageProcessor.reset(spectraPlayer)

    val cause = (error as? java.util.concurrent.CompletionException)?.cause ?: error

    if (cause is AIServer.RequestException) {
      if (cause.code == AIServer.ResponseCode.CHUNK_SEQUENCE_MISMATCH) {
        ticks.clear()
        ticksStep = 0
        return null
      }

      if (cause.code == AIServer.ResponseCode.WAITING) {
        return null
      }

      val logMessage =
        "[AiCheck] API Error ${cause.code} for player ${spectraPlayer.player.name}: ${cause.message}"

      val transientCategory = transientCategoryFor(cause.code)
      if (transientCategory != null) {
        debugManager.log(transientCategory, logMessage)
      } else {
        plugin.logger.warning(logMessage)
      }
    } else {
      plugin.logger.warning(
        "[AiCheck] Unknown API Error for ${spectraPlayer.player.name}: ${cause.message}"
      )
    }
    return null
  }

  private fun transientCategoryFor(code: AIServer.ResponseCode): DebugCategory? =
    when (code) {
      AIServer.ResponseCode.TIMEOUT -> DebugCategory.AI_API_TIMEOUT
      AIServer.ResponseCode.NETWORK_ERROR -> DebugCategory.AI_API_NETWORK
      AIServer.ResponseCode.RATE_LIMITED -> DebugCategory.AI_API_RATE_LIMITED
      AIServer.ResponseCode.SERVICE_UNAVAILABLE -> DebugCategory.AI_API_SERVICE_UNAVAILABLE
      else -> null
    }

  private fun borrowSnapshot(size: Int): Array<TickData?> {
    val buffer = snapshotBuffer.getAndSet(null)
    if (buffer == null || buffer.size < size) {
      return arrayOfNulls(size)
    }
    return buffer
  }

  private fun releaseSnapshot(buffer: Array<TickData?>, used: Int) {
    Arrays.fill(buffer, 0, used, null)
    snapshotBuffer.set(buffer)
  }
}
