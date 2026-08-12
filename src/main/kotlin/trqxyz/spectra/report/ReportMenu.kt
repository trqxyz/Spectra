package trqxyz.spectra.report

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import trqxyz.spectra.scheduler.SchedulerService

class ReportMenu(
  private val reportService: ReportService,
  private val scheduler: SchedulerService,
) : Listener {
  fun open(player: Player, requestedPage: Int = 1) {
    scheduler.runAsync {
      val page = reportService.page(requestedPage, PAGE_SIZE)
      scheduler.runSync(player) { show(player, page) }
    }
  }

  private fun show(player: Player, page: ReportPage) {
    val holder = Holder(page.page, mutableMapOf())
    val inventory =
      Bukkit.createInventory(holder, 54, "Spectra Reports · ${page.page}/${page.maxPages}")
    holder.menuInventory = inventory
    page.entries.forEachIndexed { index, report ->
      holder.reports[index] = report.id
      inventory.setItem(index, reportItem(report))
    }
    if (page.page > 1)
      inventory.setItem(45, item(Material.ARROW, "${ChatColor.WHITE}Previous page"))
    inventory.setItem(49, item(Material.BARRIER, "${ChatColor.WHITE}Close"))
    if (page.page < page.maxPages)
      inventory.setItem(53, item(Material.ARROW, "${ChatColor.WHITE}Next page"))
    player.openInventory(inventory)
  }

  @EventHandler
  fun click(event: InventoryClickEvent) {
    val holder = event.inventory.holder as? Holder ?: return
    event.isCancelled = true
    val player = event.whoClicked as? Player ?: return
    when (event.rawSlot) {
      45 -> if (holder.page > 1) open(player, holder.page - 1)
      49 -> player.closeInventory()
      53 -> open(player, holder.page + 1)
      else -> {
        val id = holder.reports[event.rawSlot] ?: return
        scheduler.runAsync {
          if (event.isRightClick || event.isShiftClick) reportService.close(id, player.name)
          else reportService.claim(id, player.name)
          scheduler.runSync(player) { open(player, holder.page) }
        }
      }
    }
  }

  private fun reportItem(report: PlayerReport): ItemStack {
    val stack =
      if (report.source == ReportSource.AI) ItemStack(Material.REDSTONE)
      else ItemStack(Material.PLAYER_HEAD)
    val meta = stack.itemMeta
    if (meta is SkullMeta) meta.owningPlayer = Bukkit.getOfflinePlayer(report.targetId)
    meta.setDisplayName("${ChatColor.WHITE}#${report.id} · ${report.targetName}")
    val lore =
      mutableListOf(
        "${ChatColor.GRAY}Source: ${ChatColor.WHITE}${report.source.name}",
        "${ChatColor.GRAY}Reporter: ${ChatColor.WHITE}${report.reporterName}",
        "${ChatColor.GRAY}Reason: ${ChatColor.WHITE}${report.reason}",
      )
    if (report.source == ReportSource.AI) {
      lore += ""
      lore += "${ChatColor.WHITE}Model results"
      for (result in report.modelResults) {
        val color =
          when {
            result.accepted && result.verdict.equals("cheat", true) -> ChatColor.RED
            result.accepted && result.verdict.equals("legit", true) -> ChatColor.GREEN
            else -> ChatColor.WHITE
          }
        lore +=
          "$color${result.model.uppercase()} · ${"%.1f".format(result.probability * 100.0)}% · ${result.verdict}"
        lore +=
          "${ChatColor.GRAY}Accepted: ${yesNo(result.accepted)} · Actionable: ${yesNo(result.actionable)} · Status: ${result.status}"
        lore +=
          "${ChatColor.GRAY}Confidence: ${"%.1f".format(result.confidence * 100.0)}% · Novelty: ${"%.1f".format(result.novelty * 100.0)}% · Buffer: ${"%.2f".format(result.buffer)}"
        lore += "${ChatColor.DARK_GRAY}${result.modelVersion}"
      }
    }
    lore += ""
    lore += "${ChatColor.GRAY}Status: ${ChatColor.WHITE}${report.status.name}"
    lore += "${ChatColor.WHITE}Left click to claim"
    lore += "${ChatColor.WHITE}Right or shift click to close"
    meta.lore = lore
    stack.itemMeta = meta
    return stack
  }

  private fun yesNo(value: Boolean): String =
    if (value) "${ChatColor.GREEN}yes" else "${ChatColor.RED}no"

  private fun item(material: Material, name: String): ItemStack {
    val stack = ItemStack(material)
    val meta = stack.itemMeta
    meta.setDisplayName(name)
    stack.itemMeta = meta
    return stack
  }

  private class Holder(val page: Int, val reports: MutableMap<Int, Long>) : InventoryHolder {
    lateinit var menuInventory: Inventory

    override fun getInventory(): Inventory = menuInventory
  }

  private companion object {
    const val PAGE_SIZE = 45
  }
}
