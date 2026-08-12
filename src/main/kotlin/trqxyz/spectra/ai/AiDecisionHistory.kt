package trqxyz.spectra.ai

import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import trqxyz.spectra.server.AIModelDecision

data class AiDecisionRecord(
  val createdAt: Instant,
  val playerName: String,
  val model: String,
  val probability: Double,
  val verdict: String,
  val status: String,
  val accepted: Boolean,
  val actionable: Boolean,
  val confidence: Double,
  val novelty: Double,
  val modelVersion: String,
  val windowTicks: Int,
  val buffer: Double,
)

data class AiDecisionPage(val entries: List<AiDecisionRecord>, val page: Int, val maxPages: Int)

class AiDecisionHistory {
  private val histories = ConcurrentHashMap<UUID, ArrayDeque<AiDecisionRecord>>()

  fun record(
    playerId: UUID,
    playerName: String,
    model: String,
    decision: AIModelDecision,
    buffer: Double,
  ) {
    val history = histories.computeIfAbsent(playerId) { ArrayDeque() }
    val entry =
      AiDecisionRecord(
        Instant.now(),
        playerName,
        model,
        decision.calibratedProbability,
        decision.verdict,
        decision.status,
        decision.accepted,
        decision.actionable,
        decision.confidence,
        decision.novelty,
        decision.modelVersion,
        decision.windowTicks,
        buffer,
      )
    synchronized(history) {
      history.addFirst(entry)
      while (history.size > MAX_ENTRIES_PER_PLAYER) {
        history.removeLast()
      }
    }
  }

  fun page(playerId: UUID, requestedPage: Int, pageSize: Int): AiDecisionPage {
    val history = histories[playerId] ?: return AiDecisionPage(emptyList(), 1, 1)
    synchronized(history) {
      val maxPages = kotlin.math.max(1, (history.size + pageSize - 1) / pageSize)
      val page = requestedPage.coerceIn(1, maxPages)
      val entries = history.drop((page - 1) * pageSize).take(pageSize)
      return AiDecisionPage(entries, page, maxPages)
    }
  }

  private companion object {
    const val MAX_ENTRIES_PER_PLAYER = 100
  }
}
