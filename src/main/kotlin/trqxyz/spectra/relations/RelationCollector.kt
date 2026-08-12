package trqxyz.spectra.relations

import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.platform.scheduler.TaskHandle
import trqxyz.spectra.scheduler.SchedulerService

class RelationCollector(
  private val plugin: SpectraPlugin,
  private val configManager: ConfigManager,
  private val eventStore: RelationEventStore,
  private val sources: RelationSources,
  private val scheduler: SchedulerService,
) : Listener {
  private val openContainers = ConcurrentHashMap<UUID, ContainerSnapshot>()
  private val deposits =
    object : LinkedHashMap<String, MutableMap<Material, ArrayDeque<Deposit>>>(128, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, MutableMap<Material, ArrayDeque<Deposit>>>?
      ): Boolean = size > MAX_CONTAINERS
    }
  private val droppedItems = DroppedItemRelationTracker()
  private var regionTask: TaskHandle? = null

  fun start() {
    plugin.server.pluginManager.registerEvents(this, plugin)
    sources.banStatus.start()
    regionTask =
      scheduler.runTimer(Runnable { scanRegions() }, REGION_FIRST_SCAN_TICKS, REGION_SCAN_TICKS)
  }

  fun stop() {
    sources.banStatus.stop()
    regionTask?.cancel()
    regionTask = null
    openContainers.clear()
    deposits.clear()
    droppedItems.clear()
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onJoin(event: PlayerJoinEvent) {
    if (!configManager.relationsEnabled) return
    if (configManager.relationsIpAddressesEnabled) sources.ip.observe(event.player)
    if (configManager.relationsBanStatusEnabled) sources.banStatus.observe(event.player)
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onCommand(event: PlayerCommandPreprocessEvent) {
    if (!configManager.relationsEnabled || !configManager.relationsVaultEnabled) return
    val parts = event.message.removePrefix("/").trim().split(Regex("\\s+"))
    if (parts.size < 3) return
    val label = parts[0].substringAfter(':').lowercase()
    if (label != "pay") return
    val amount = parts[2].replace(',', '.').toDoubleOrNull() ?: return
    sources.vault.verifyTransfer(event.player, parts[1], amount)
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onOpen(event: InventoryOpenEvent) {
    if (!configManager.relationsEnabled || !configManager.relationsContainersEnabled) return
    val player = event.player as? Player ?: return
    val inventory = event.inventory
    if (inventory.type !in CONTAINER_TYPES) return
    val location = inventory.location ?: return
    openContainers[player.uniqueId] =
      ContainerSnapshot(locationKey(location), countMaterials(inventory), location)
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onClose(event: InventoryCloseEvent) {
    if (!configManager.relationsEnabled || !configManager.relationsContainersEnabled) return
    val player = event.player as? Player ?: return
    val snapshot = openContainers.remove(player.uniqueId) ?: return
    val after = countMaterials(event.inventory)
    val materials = snapshot.materials.keys + after.keys
    val regionKeys =
      if (configManager.relationsWorldGuardEnabled) {
        sources.worldGuard.regionKeys(snapshot.location)
      } else {
        emptyList()
      }
    synchronized(deposits) {
      val ledger = deposits.getOrPut(snapshot.key) { HashMap() }
      for (material in materials) {
        val delta = after.getOrDefault(material, 0) - snapshot.materials.getOrDefault(material, 0)
        if (delta > 0) {
          val queue = ledger.getOrPut(material) { ArrayDeque() }
          while (queue.size >= MAX_DEPOSITS_PER_MATERIAL) queue.removeFirst()
          queue.addLast(Deposit(player.uniqueId, player.name, delta, regionKeys))
        } else if (delta < 0) {
          consumeDeposits(player, material, -delta, ledger.getOrPut(material) { ArrayDeque() })
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onDrop(event: PlayerDropItemEvent) {
    if (!configManager.relationsEnabled || !configManager.relationsItemsEnabled) return
    val stack = event.itemDrop.itemStack
    droppedItems.record(
      DroppedItemRelationTracker.DroppedItemSnapshot(
        event.itemDrop.uniqueId,
        event.player.uniqueId,
        event.player.name,
        stack.type,
        stack.amount,
        System.currentTimeMillis(),
      )
    )
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onPickup(event: EntityPickupItemEvent) {
    if (!configManager.relationsEnabled || !configManager.relationsItemsEnabled) return
    val player = event.entity as? Player ?: return
    val relation =
      droppedItems.consume(
        event.item.uniqueId,
        player.uniqueId,
        player.name,
        System.currentTimeMillis(),
      ) ?: return
    eventStore.enqueue(relation)
  }

  private fun consumeDeposits(
    receiver: Player,
    material: Material,
    requested: Int,
    queue: ArrayDeque<Deposit>,
  ) {
    var remaining = requested
    while (remaining > 0 && queue.isNotEmpty()) {
      val deposit = queue.removeFirst()
      val transferred = minOf(remaining, deposit.amount)
      remaining -= transferred
      if (deposit.amount > transferred) {
        queue.addFirst(deposit.copy(amount = deposit.amount - transferred))
      }
      if (deposit.playerUuid == receiver.uniqueId) continue
      eventStore.enqueue(
        RelationEvent(
          type = "container_transfer",
          playerAUuid = deposit.playerUuid.toString(),
          playerAName = deposit.playerName,
          playerBUuid = receiver.uniqueId.toString(),
          playerBName = receiver.name,
          material = material.name,
          amount = transferred.toDouble(),
          context = eventStore.context(mapOf("regions" to deposit.regionKeys)),
        )
      )
    }
  }

  private fun scanRegions() {
    if (!configManager.relationsEnabled || !configManager.relationsWorldGuardEnabled) return
    val snapshotId = UUID.randomUUID().toString()
    val events = ArrayList<RelationEvent>()
    for (roster in sources.worldGuard.rosters()) {
      for (member in roster.members) {
        events +=
          RelationEvent(
            type = "region_member",
            playerAUuid = member.uuid.toString(),
            playerAName = member.name,
            context =
              eventStore.context(
                mapOf(
                  "snapshotId" to snapshotId,
                  "region" to roster.region,
                  "world" to roster.world,
                  "role" to member.role,
                )
              ),
          )
      }
    }
    events +=
      RelationEvent(
        type = "region_snapshot_complete",
        playerAUuid = null,
        playerAName = null,
        context = eventStore.context(mapOf("snapshotId" to snapshotId)),
      )
    eventStore.enqueueAll(events)
  }

  private fun countMaterials(inventory: Inventory): Map<Material, Int> {
    val result = HashMap<Material, Int>()
    for (item in inventory.contents) {
      if (item == null || item.type == Material.AIR) continue
      result[item.type] = result.getOrDefault(item.type, 0) + item.amount
    }
    return result
  }

  private fun locationKey(location: Location): String {
    return "${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
  }

  private data class ContainerSnapshot(
    val key: String,
    val materials: Map<Material, Int>,
    val location: Location,
  )

  private data class Deposit(
    val playerUuid: UUID,
    val playerName: String,
    val amount: Int,
    val regionKeys: List<String>,
  )

  private companion object {
    val CONTAINER_TYPES =
      setOf(
        InventoryType.CHEST,
        InventoryType.BARREL,
        InventoryType.SHULKER_BOX,
        InventoryType.HOPPER,
        InventoryType.DROPPER,
      )
    const val MAX_CONTAINERS = 20_000
    const val MAX_DEPOSITS_PER_MATERIAL = 10_000
    const val REGION_FIRST_SCAN_TICKS = 100L
    const val REGION_SCAN_TICKS = 6000L
  }
}
