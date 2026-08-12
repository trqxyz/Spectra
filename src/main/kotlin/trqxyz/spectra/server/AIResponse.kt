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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.server

/** One model's complete decision contract. */
data class AIModelDecision(
  val status: String,
  val riskScore: Double,
  val calibratedProbability: Double,
  val verdict: String,
  val accepted: Boolean,
  val confidence: Double,
  val novelty: Double,
  val modelVersion: String,
  val windowTicks: Int,
  /** True only when the backend has explicitly allowed an accepted result to act. */
  val actionable: Boolean = false,
  val legacy: Boolean = false,
) {
  val probability: Double
    get() = calibratedProbability

  val isAcceptedCheat: Boolean
    get() = accepted && verdict.equals(VERDICT_CHEAT, ignoreCase = true)

  val isAcceptedLegit: Boolean
    get() = accepted && verdict.equals(VERDICT_LEGIT, ignoreCase = true)

  /** Backend policy gate in addition to the model's own accepted verdict. */
  val isActionableCheat: Boolean
    get() = actionable && isAcceptedCheat

  companion object {
    const val STATUS_READY = "ready"
    const val STATUS_PENDING = "pending"
    const val VERDICT_CHEAT = "cheat"
    const val VERDICT_LEGIT = "legit"
    const val VERDICT_UNKNOWN = "unknown"

    /**
     * Preserve a display score from an old probability-only server, but never turn it into evidence
     * for an action. The modern contract carries the calibration, OOD and explicit acceptance
     * decision needed for enforcement.
     */
    fun legacy(probability: Double): AIModelDecision {
      return AIModelDecision(
        status = "unavailable",
        riskScore = probability,
        calibratedProbability = probability,
        verdict = VERDICT_UNKNOWN,
        accepted = false,
        confidence = 0.0,
        novelty = 1.0,
        modelVersion = "legacy-contract-unsupported",
        windowTicks = 0,
        actionable = false,
        legacy = true,
      )
    }
  }
}

/**
 * The primary decision is retained for single-model/legacy servers. [models] contains independent
 * decisions for every tier; callers must gate actions per model and may only aggregate their score
 * for display.
 */
data class AIResponse(
  val primary: AIModelDecision,
  val models: Map<String, AIModelDecision> = emptyMap(),
) {
  constructor(probability: Double) : this(AIModelDecision.legacy(probability))

  constructor(
    probability: Double,
    models: Map<String, Double>,
  ) : this(
    AIModelDecision.legacy(probability),
    models.mapValues { (_, modelProbability) -> AIModelDecision.legacy(modelProbability) },
  )

  val probability: Double
    get() = primary.probability

  val status: String
    get() = primary.status

  val riskScore: Double
    get() = primary.riskScore

  val calibratedProbability: Double
    get() = primary.calibratedProbability

  val verdict: String
    get() = primary.verdict

  val accepted: Boolean
    get() = primary.accepted

  val confidence: Double
    get() = primary.confidence

  val novelty: Double
    get() = primary.novelty

  val modelVersion: String
    get() = primary.modelVersion

  val windowTicks: Int
    get() = primary.windowTicks
}
