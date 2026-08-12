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
package trqxyz.spectra.ai

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-request player context. [playerUuid], [streamId] and [chunkSequence] identify an ordered
 * in-game session. A batch must not mix player streams (see BatchingAiTransport).
 *
 * The stream identity is deliberately built from Bukkit's UUID only. Network addresses and player
 * names are never used as an inference identity.
 */
data class AiRequestContext(
  val playerName: String?,
  val models: String?,
  val playerUuid: String? = null,
  val streamId: String? = null,
  val chunkSequence: Long? = null,
) {
  val isPlayerStream: Boolean
    get() = playerUuid != null || streamId != null
}

/**
 * Request identity for one in-game player session. A new [PlayerRequestStream] is created with each
 * [trqxyz.spectra.checks.impl.ai.AiCheck], so reconnecting produces a fresh stream id while retries
 * retain the same request context and chunk sequence.
 */
class PlayerRequestStream(
  private val playerUuid: String,
  val streamId: String = UUID.randomUUID().toString(),
  initialSequence: Long = 0,
) {
  private val nextSequence = AtomicLong(initialSequence)
  private var pending: AiRequestContext? = null

  init {
    require(playerUuid.isNotBlank()) { "playerUuid must not be blank" }
    require(streamId.isNotBlank()) { "streamId must not be blank" }
    require(initialSequence >= 0) { "initialSequence must be non-negative" }
  }

  @Synchronized
  fun next(playerName: String?, models: String?): AiRequestContext {
    pending?.let {
      return it
    }
    return AiRequestContext(
        playerName = playerName,
        models = models,
        playerUuid = playerUuid,
        streamId = streamId,
        chunkSequence = nextSequence.get(),
      )
      .also { pending = it }
  }

  @Synchronized
  fun acknowledge(context: AiRequestContext) {
    if (pending != context) return
    nextSequence.incrementAndGet()
    pending = null
  }
}
