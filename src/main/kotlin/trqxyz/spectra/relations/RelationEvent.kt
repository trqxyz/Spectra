package trqxyz.spectra.relations

import java.util.UUID

data class RelationEvent(
  val eventId: String = UUID.randomUUID().toString(),
  val type: String,
  val playerAUuid: String?,
  val playerAName: String?,
  val playerBUuid: String? = null,
  val playerBName: String? = null,
  val material: String? = null,
  val amount: Double? = null,
  val context: String? = null,
  val occurredAt: Long = System.currentTimeMillis(),
)
