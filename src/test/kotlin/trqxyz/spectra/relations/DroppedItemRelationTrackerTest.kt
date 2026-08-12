package trqxyz.spectra.relations

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.bukkit.Material
import org.junit.jupiter.api.Test

class DroppedItemRelationTrackerTest {
  private val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
  private val receiver = UUID.fromString("00000000-0000-0000-0000-000000000002")

  @Test
  fun `classifies direct and delayed item transfers`() {
    val tracker = DroppedItemRelationTracker()
    val directEntity = UUID.randomUUID()
    tracker.record(item(directEntity, Material.DIAMOND, 3, 1_000L))

    val direct = tracker.consume(directEntity, receiver, "Bravo", 5_000L)

    assertEquals("item_transfer", direct?.type)
    assertEquals(3.0, direct?.amount)

    val delayedEntity = UUID.randomUUID()
    tracker.record(item(delayedEntity, Material.DIAMOND, 2, 1_000L))

    val delayed = tracker.consume(delayedEntity, receiver, "Bravo", 20_000L)

    assertEquals("item_dead_drop", delayed?.type)
  }

  @Test
  fun `ignores own pickup and expired drop`() {
    val tracker = DroppedItemRelationTracker()
    val ownEntity = UUID.randomUUID()
    tracker.record(item(ownEntity, Material.STONE, 1, 1_000L))
    assertNull(tracker.consume(ownEntity, owner, "Alpha", 2_000L))

    val expiredEntity = UUID.randomUUID()
    tracker.record(item(expiredEntity, Material.STONE, 1, 1_000L))
    assertNull(tracker.consume(expiredEntity, receiver, "Bravo", 122_000L))
  }

  private fun item(entityId: UUID, material: Material, amount: Int, occurredAt: Long) =
    DroppedItemRelationTracker.DroppedItemSnapshot(
      entityId,
      owner,
      "Alpha",
      material,
      amount,
      occurredAt,
    )
}
