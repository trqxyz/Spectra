package trqxyz.spectra.checks.impl.ai

import kotlin.math.max
import trqxyz.spectra.server.AIModelDecision

private const val CHEAT_PROBABILITY = 0.90
private const val LEGIT_PROBABILITY = 0.10

internal data class AiBufferPolicy(
  val enforcementEnabled: Boolean,
  val flagThreshold: Double,
  val resetOnFlag: Double,
  val multiplier: Double,
  val decrease: Double,
)

internal class AiViolationBuffer {
  private val modelValues = HashMap<String, Double>()

  var value: Double = 0.0
    private set

  fun restore(restored: Double) {
    value = max(value, max(0.0, restored))
  }

  fun updateModels(
    models: Map<String, AIModelDecision>,
    policy: AiBufferPolicy,
    onFlag: (String, Double, Double) -> Unit,
  ): Boolean {
    val initialValue = if (modelValues.isEmpty()) value else 0.0
    var flagged = false
    for ((model, decision) in models) {
      val probability = decision.calibratedProbability
      var modelValue = modelValues[model] ?: initialValue
      modelValue = adjusted(modelValue, decision, policy.multiplier, policy.decrease)
      if (
        policy.enforcementEnabled && decision.isActionableCheat && modelValue > policy.flagThreshold
      ) {
        flagged = true
        onFlag(model, probability, modelValue)
        modelValue = policy.resetOnFlag
      }
      modelValues[model] = modelValue
    }
    value = modelValues.values.maxOrNull() ?: 0.0
    return flagged
  }

  fun updatePrimary(
    decision: AIModelDecision,
    policy: AiBufferPolicy,
    onFlag: (Double, Double) -> Unit,
  ): Boolean {
    val probability = decision.calibratedProbability
    value = adjusted(value, decision, policy.multiplier, policy.decrease)
    if (
      !policy.enforcementEnabled || !decision.isActionableCheat || value <= policy.flagThreshold
    ) {
      return false
    }
    onFlag(probability, value)
    value = policy.resetOnFlag
    return true
  }

  fun modelValue(model: String): Double = modelValues[model] ?: 0.0

  private fun adjusted(
    current: Double,
    decision: AIModelDecision,
    multiplier: Double,
    decrease: Double,
  ): Double =
    when {
      decision.isAcceptedCheat && decision.calibratedProbability > CHEAT_PROBABILITY ->
        current + (decision.calibratedProbability - CHEAT_PROBABILITY) * multiplier
      decision.isAcceptedLegit && decision.calibratedProbability < LEGIT_PROBABILITY ->
        max(0.0, current - decrease)
      else -> current
    }
}
