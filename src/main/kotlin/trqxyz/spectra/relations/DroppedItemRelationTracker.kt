package trqxyz.spectra.relations

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import org.bukkit.Material

internal class DroppedItemRelationTracker {
  private val drops = ConcurrentHashMap<UUID, Drop>()
  private val order = ConcurrentLinkedQueue<UUID>()
  private var insertions = 0

  fun record(item: DroppedItemSnapshot) {
    drops[item.entityId] =
      Drop(item.playerId, item.playerName, item.material, item.amount, item.occurredAt)
    order.add(item.entityId)
    insertions++
    if (insertions >= ORDER_COMPACT_INTERVAL) {
      order.removeIf { !drops.containsKey(it) }
      insertions = 0
    }
    while (drops.size > MAX_DROPS) {
      val oldest = order.poll() ?: break
      drops.remove(oldest)
    }
  }

  data class DroppedItemSnapshot(
    val entityId: UUID,
    val playerId: UUID,
    val playerName: String,
    val material: Material,
    val amount: Int,
    val occurredAt: Long,
  )

  fun consume(
    entityId: UUID,
    receiverId: UUID,
    receiverName: String,
    occurredAt: Long,
  ): RelationEvent? {
    val drop = drops.remove(entityId)
    val elapsed = drop?.let { occurredAt - it.occurredAt }
    return when {
      drop == null -> null
      drop.playerUuid == receiverId -> null
      elapsed == null || elapsed !in 0..DROP_TTL_MS -> null
      else ->
        RelationEvent(
          type = if (elapsed <= DIRECT_TRANSFER_MS) "item_transfer" else "item_dead_drop",
          playerAUuid = drop.playerUuid.toString(),
          playerAName = drop.playerName,
          playerBUuid = receiverId.toString(),
          playerBName = receiverName,
          material = drop.material.name,
          amount = drop.amount.toDouble(),
          occurredAt = occurredAt,
        )
    }
  }

  fun clear() {
    drops.clear()
    order.clear()
    insertions = 0
  }

  private data class Drop(
    val playerUuid: UUID,
    val playerName: String,
    val material: Material,
    val amount: Int,
    val occurredAt: Long,
  )

  private companion object {
    const val MAX_DROPS = 50_000
    const val ORDER_COMPACT_INTERVAL = 1_024
    const val DIRECT_TRANSFER_MS = 5_000L
    const val DROP_TTL_MS = 120_000L
  }
}
