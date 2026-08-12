package trqxyz.spectra.relations

import io.papermc.paper.ban.BanListType
import java.nio.charset.StandardCharsets
import java.util.UUID
import litebans.api.Database
import litebans.api.Entry
import litebans.api.Events
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.ban.ProfileBanList
import org.bukkit.entity.Player
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.platform.scheduler.TaskHandle
import trqxyz.spectra.scheduler.SchedulerService

class BanStatusBridge(
  private val plugin: SpectraPlugin,
  private val configManager: ConfigManager,
  private val eventStore: RelationEventStore,
  private val scheduler: SchedulerService,
) {
  private var vanillaTask: TaskHandle? = null
  private var liteBansListener: Any? = null

  fun start() {
    vanillaTask =
      scheduler.runTimer(
        Runnable { scanVanilla() },
        VANILLA_SCAN_INITIAL_TICKS,
        VANILLA_SCAN_PERIOD_TICKS,
      )
    registerLiteBans()
  }

  fun stop() {
    vanillaTask?.cancel()
    vanillaTask = null
    liteBansListener = null
  }

  fun observe(player: Player) {
    if (!enabled()) return
    when (configManager.relationsBanProvider) {
      "vanilla" -> observeVanilla(player)
      "litebans" -> observeLiteBans(player)
    }
  }

  private fun enabled(): Boolean {
    return configManager.relationsEnabled && configManager.relationsBanStatusEnabled
  }

  private fun scanVanilla() {
    if (!enabled() || configManager.relationsBanProvider != "vanilla") return
    val snapshotId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    val events = ArrayList<RelationEvent>()
    val banList: ProfileBanList = Bukkit.getBanList(BanListType.PROFILE)
    for (player in Bukkit.getBannedPlayers()) {
      val profile = Bukkit.createProfile(player.uniqueId, player.name)
      val entry = banList.getBanEntry(profile) ?: continue
      events +=
        banEvent(
          player = player,
          active = true,
          provider = "vanilla",
          providerRef = stableReference("vanilla", player.uniqueId, entry.created?.time ?: 0L),
          startedAt = entry.created?.time,
          expiresAt = entry.expiration?.time,
          reason = entry.reason,
          actor = entry.source,
          snapshotId = snapshotId,
          occurredAt = now,
        )
    }
    events +=
      RelationEvent(
        type = "ban_snapshot_complete",
        playerAUuid = null,
        playerAName = null,
        context = eventStore.context(mapOf("provider" to "vanilla", "snapshotId" to snapshotId)),
        occurredAt = now,
      )
    eventStore.enqueueAll(events)
  }

  private fun observeVanilla(player: Player) {
    val banList: ProfileBanList = Bukkit.getBanList(BanListType.PROFILE)
    val entry = banList.getBanEntry(player.playerProfile) ?: return
    eventStore.enqueue(
      banEvent(
        player = player,
        active = true,
        provider = "vanilla",
        providerRef = stableReference("vanilla", player.uniqueId, entry.created?.time ?: 0L),
        startedAt = entry.created?.time,
        expiresAt = entry.expiration?.time,
        reason = entry.reason,
        actor = entry.source,
      )
    )
  }

  private fun registerLiteBans() {
    if (plugin.server.pluginManager.getPlugin("LiteBans") == null) return
    runCatching {
        val listener =
          object : Events.Listener() {
            override fun entryAdded(entry: Entry) {
              acceptLiteBansEntry(entry, true)
            }

            override fun entryRemoved(entry: Entry) {
              acceptLiteBansEntry(entry, false)
            }
          }
        Events.get().register(listener)
        liteBansListener = listener
      }
      .onFailure { plugin.logger.warning("[Relations] LiteBans API is unavailable: ${it.message}") }
  }

  private fun observeLiteBans(player: Player) {
    if (plugin.server.pluginManager.getPlugin("LiteBans") == null) return
    scheduler.runAsync {
      runCatching {
        val active = Database.get().isPlayerBanned(player.uniqueId, null)
        if (!active) {
          eventStore.enqueue(
            RelationEvent(
              type = "ban_state",
              playerAUuid = player.uniqueId.toString(),
              playerAName = player.name,
              context = eventStore.context(mapOf("provider" to "litebans", "active" to false)),
            )
          )
        }
      }
    }
  }

  private fun acceptLiteBansEntry(entry: Entry, active: Boolean) {
    if (!enabled() || configManager.relationsBanProvider != "litebans") return
    if (entry.type.lowercase() != "ban") return
    val uuidText = entry.uuid ?: return
    val normalizedUuid =
      if (uuidText.length == 32) {
        uuidText.replaceFirst(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "\$1-\$2-\$3-\$4-\$5")
      } else {
        uuidText
      }
    val uuid = runCatching { UUID.fromString(normalizedUuid) }.getOrNull() ?: return
    val player = Bukkit.getOfflinePlayer(uuid)
    val playerName = player.name
    val startedAt = entry.dateStart
    val expiresAt = entry.dateEnd.takeIf { it > 0L }
    val providerRef = entry.id.toString()
    eventStore.enqueue(
      banEvent(
        player = player,
        playerName = playerName,
        active = active,
        provider = "litebans",
        providerRef = providerRef,
        startedAt = startedAt,
        expiresAt = expiresAt,
        reason = entry.reason,
        actor = entry.executorName,
      )
    )
  }

  private fun banEvent(
    player: OfflinePlayer,
    playerName: String? = null,
    active: Boolean,
    provider: String,
    providerRef: String,
    startedAt: Long? = null,
    expiresAt: Long? = null,
    reason: String? = null,
    actor: String? = null,
    snapshotId: String? = null,
    occurredAt: Long = System.currentTimeMillis(),
  ): RelationEvent {
    return RelationEvent(
      eventId =
        UUID.nameUUIDFromBytes(
            "$provider:$providerRef:$active:$occurredAt".toByteArray(StandardCharsets.UTF_8)
          )
          .toString(),
      type = "ban_state",
      playerAUuid = player.uniqueId.toString(),
      playerAName = playerName ?: player.name ?: player.uniqueId.toString(),
      context =
        eventStore.context(
          mapOf(
            "provider" to provider,
            "providerRef" to providerRef,
            "active" to active,
            "startedAt" to startedAt,
            "expiresAt" to expiresAt,
            "reason" to reason,
            "actor" to actor,
            "snapshotId" to snapshotId,
          )
        ),
      occurredAt = occurredAt,
    )
  }

  private fun stableReference(provider: String, uuid: UUID, startedAt: Long): String {
    return UUID.nameUUIDFromBytes("$provider:$uuid:$startedAt".toByteArray(StandardCharsets.UTF_8))
      .toString()
  }

  private companion object {
    const val VANILLA_SCAN_INITIAL_TICKS = 100L
    const val VANILLA_SCAN_PERIOD_TICKS = 1200L
  }
}
