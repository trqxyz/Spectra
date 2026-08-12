package trqxyz.spectra.relations

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.domains.DefaultDomain
import java.util.UUID
import java.util.logging.Logger
import org.bukkit.Bukkit
import org.bukkit.Location

class WorldGuardRelationProvider(private val logger: Logger) {
  private val available = Bukkit.getPluginManager().isPluginEnabled("WorldGuard")

  fun rosters(): List<RegionRoster> {
    if (!available) return emptyList()
    return runCatching {
        val result = ArrayList<RegionRoster>()
        val container = WorldGuard.getInstance().platform.regionContainer
        for (world in Bukkit.getWorlds()) {
          val manager = container.get(BukkitAdapter.adapt(world)) ?: continue
          for (region in manager.regions.values) {
            val members = LinkedHashMap<UUID, RegionMember>()
            addDomain(members, region.members, "member")
            addDomain(members, region.owners, "owner")
            if (members.isNotEmpty()) {
              result += RegionRoster(world.name, region.id, members.values.toList())
            }
          }
        }
        result
      }
      .onFailure { logger.warning("[Relations] WorldGuard roster scan failed: ${it.message}") }
      .getOrDefault(emptyList())
  }

  fun regionKeys(location: Location): List<String> {
    if (!available) return emptyList()
    return runCatching {
        val world = location.world ?: return@runCatching emptyList()
        val manager =
          WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
            ?: return@runCatching emptyList()
        manager
          .getApplicableRegions(BukkitAdapter.asBlockVector(location))
          .regions
          .filterNot { it.id == "__global__" }
          .map { "${world.name}|${it.id}" }
      }
      .getOrDefault(emptyList())
  }

  private fun addDomain(
    target: MutableMap<UUID, RegionMember>,
    domain: DefaultDomain,
    role: String,
  ) {
    for (uuid in domain.uniqueIds) {
      val name = Bukkit.getOfflinePlayer(uuid).name ?: continue
      target[uuid] = RegionMember(uuid, name, role)
    }
    for (name in domain.players) {
      @Suppress("DEPRECATION") val player = Bukkit.getOfflinePlayer(name)
      target[player.uniqueId] = RegionMember(player.uniqueId, name, role)
    }
  }
}

data class RegionRoster(val world: String, val region: String, val members: List<RegionMember>)

data class RegionMember(val uuid: UUID, val name: String, val role: String)
