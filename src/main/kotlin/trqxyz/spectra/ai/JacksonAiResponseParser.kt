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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import trqxyz.spectra.server.AIModelDecision
import trqxyz.spectra.server.AIResponse

class JacksonAiResponseParser : AiResponseParser {
  override fun parse(response: String): AIResponse {
    val root = OBJECT_MAPPER.readTree(response)
    if (!root.isObject) throw IllegalArgumentException("AI response must be a JSON object")

    val models = parseModels(root.get("models"))
    val primary =
      if (hasScore(root)) parseDecision(root, "response")
      else
        models.values.maxByOrNull(AIModelDecision::calibratedProbability)
          ?: throw IllegalArgumentException("AI response does not contain a valid probability")
    return AIResponse(primary, models)
  }

  private fun parseModels(node: JsonNode?): Map<String, AIModelDecision> {
    if (node == null || node.isNull) return emptyMap()
    if (!node.isObject) throw IllegalArgumentException("AI response models must be an object")
    val models = LinkedHashMap<String, AIModelDecision>()
    val fields = node.fields()
    while (fields.hasNext()) {
      val (name, value) = fields.next()
      // Pending/unavailable tiers intentionally have no score until their window is complete.
      // They are metadata, not decisions, and must not invalidate ready tiers in the same response.
      val unscoredStatus =
        if (value.isObject && !hasScore(value)) readText(value.get("status"))?.lowercase() else null
      if (unscoredStatus != null && unscoredStatus != AIModelDecision.STATUS_READY) {
        require(unscoredStatus in VALID_STATUSES) {
          "model '$name' contains unsupported status '$unscoredStatus'"
        }
        val accepted = readBoolean(value.get("accepted"), "model '$name' accepted") ?: false
        require(!accepted) { "model '$name' cannot be accepted with status '$unscoredStatus'" }
        continue
      }
      models[name] = parseDecision(value, "model '$name'")
    }
    return models
  }

  private fun parseDecision(node: JsonNode, label: String): AIModelDecision {
    if (node.isNumber || node.isTextual) {
      return AIModelDecision.legacy(requireUnit(readNumber(node), "$label probability"))
    }
    if (!node.isObject) throw IllegalArgumentException("$label decision must be an object")

    val probability =
      readNumber(node.get("calibrated_probability"))
        ?: readNumber(node.get("probability"))
        ?: throw IllegalArgumentException("$label does not contain a valid probability")
    val calibratedProbability = requireUnit(probability, "$label calibrated_probability")
    // Older endpoints occasionally return a mixture of modern-looking fields
    // and a raw probability.  It has neither calibration/OOD provenance nor a
    // trustworthy acceptance decision, so preserve the score for display only.
    if (!hasCompleteContract(node)) {
      return AIModelDecision.legacy(calibratedProbability)
    }
    val pending = readBoolean(node.get("pending"), "$label pending") ?: false
    val status =
      (readText(node.get("status"))
          ?: if (pending) AIModelDecision.STATUS_PENDING else AIModelDecision.STATUS_READY)
        .lowercase()
    if (status !in VALID_STATUSES) {
      throw IllegalArgumentException("$label contains unsupported status '$status'")
    }

    val legacy = AIModelDecision.legacy(calibratedProbability)
    val explicitVerdict = readText(node.get("verdict"))?.lowercase()
    val verdict =
      explicitVerdict
        ?: if (status == AIModelDecision.STATUS_READY) legacy.verdict
        else AIModelDecision.VERDICT_UNKNOWN
    if (verdict !in VALID_VERDICTS) {
      throw IllegalArgumentException("$label contains unsupported verdict '$verdict'")
    }
    val explicitAccepted = readBoolean(node.get("accepted"), "$label accepted")
    val accepted =
      (explicitAccepted
        ?: (status == AIModelDecision.STATUS_READY && explicitVerdict == null && legacy.accepted))
    if (accepted && status != AIModelDecision.STATUS_READY) {
      throw IllegalArgumentException("$label cannot be accepted with status '$status'")
    }

    val riskScore =
      requireUnit(
        readNumber(node.get("risk_score")) ?: readNumber(node.get("risk")) ?: calibratedProbability,
        "$label risk_score",
      )
    val confidence =
      requireUnit(readNumber(node.get("confidence")) ?: legacy.confidence, "$label confidence")
    val novelty = requireUnit(readNumber(node.get("novelty")) ?: 0.0, "$label novelty")
    val modelVersion = readText(node.get("model_version")) ?: "legacy"
    val windowTicks = readInteger(node.get("window_ticks"), "$label window_ticks") ?: 0
    if (windowTicks < 0) throw IllegalArgumentException("$label window_ticks must be non-negative")
    // The server's shadow policy is authoritative. Missing this explicit field
    // on an old otherwise-complete response is safe: it remains observable,
    // but the plugin cannot turn it into a punishment.
    val actionable = accepted && (readBoolean(node.get("actionable"), "$label actionable") ?: false)

    return AIModelDecision(
      status = status,
      riskScore = riskScore,
      calibratedProbability = calibratedProbability,
      verdict = verdict,
      accepted = accepted,
      confidence = confidence,
      novelty = novelty,
      modelVersion = modelVersion,
      windowTicks = windowTicks,
      actionable = actionable,
      legacy = false,
    )
  }

  private fun hasScore(node: JsonNode): Boolean =
    readNumber(node.get("calibrated_probability")) != null ||
      readNumber(node.get("probability")) != null

  private fun hasCompleteContract(node: JsonNode): Boolean =
    REQUIRED_CONTRACT_FIELDS.all { field -> node.hasNonNull(field) }

  private fun readNumber(node: JsonNode?): Double? =
    when {
      node == null || node.isNull -> null
      node.isNumber -> node.doubleValue()
      node.isTextual -> node.textValue().toDoubleOrNull()
      else -> null
    }

  private fun readInteger(node: JsonNode?, label: String): Int? {
    if (node == null || node.isNull) return null
    if (!node.isIntegralNumber || !node.canConvertToInt()) {
      throw IllegalArgumentException("$label must be an integer")
    }
    return node.intValue()
  }

  private fun readBoolean(node: JsonNode?, label: String): Boolean? {
    if (node == null || node.isNull) return null
    if (!node.isBoolean) throw IllegalArgumentException("$label must be a boolean")
    return node.booleanValue()
  }

  private fun readText(node: JsonNode?): String? {
    if (node == null || node.isNull || !node.isTextual) return null
    return node.textValue().takeIf(String::isNotBlank)
  }

  private fun requireUnit(value: Double?, label: String): Double {
    if (value == null || !value.isFinite() || value !in 0.0..1.0) {
      throw IllegalArgumentException("$label must be a finite number in [0, 1]")
    }
    return value
  }

  companion object {
    private val OBJECT_MAPPER = ObjectMapper()
    private val VALID_STATUSES = setOf("ready", "pending", "unavailable", "invalid", "error")
    private val VALID_VERDICTS = setOf("cheat", "legit", "unknown")
    private val REQUIRED_CONTRACT_FIELDS =
      setOf(
        "status",
        "risk_score",
        "calibrated_probability",
        "verdict",
        "accepted",
        "confidence",
        "novelty",
        "model_version",
        "window_ticks",
      )
  }
}
