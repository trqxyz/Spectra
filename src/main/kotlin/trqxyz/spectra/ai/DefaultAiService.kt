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

import java.util.concurrent.CompletableFuture
import trqxyz.spectra.data.TickData
import trqxyz.spectra.server.AIServerProvider

class DefaultAiService(
  private val transportProvider: AIServerProvider,
  private val serializer: AiSerializer,
  private val parser: AiResponseParser,
) : AiService {
  override val isEnabled: Boolean
    get() = transportProvider.get() != null

  override fun request(
    ticks: Array<TickData>,
    count: Int,
    context: AiRequestContext?,
  ): CompletableFuture<AiResult> {
    val transport: AiTransport =
      transportProvider.get() ?: return CompletableFuture.completedFuture(AiResult.disabledResult())

    val payload = serializer.serialize(ticks, count)
    return transport.send(payload, context).thenApply(this::parse)
  }

  private fun parse(raw: String): AiResult {
    return try {
      AiResult(parser.parse(raw), raw, null, false)
    } catch (e: Exception) {
      AiResult(null, raw, e, false)
    }
  }
}
