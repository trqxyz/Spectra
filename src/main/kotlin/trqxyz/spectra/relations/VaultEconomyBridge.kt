package trqxyz.spectra.relations

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.scheduler.SchedulerService

class VaultEconomyBridge(
  private val configManager: ConfigManager,
  private val scheduler: SchedulerService,
  private val eventStore: RelationEventStore,
) {
  fun verifyTransfer(payer: Player, targetName: String, requestedAmount: Double) {
    if (!requestedAmount.isFinite() || requestedAmount <= 0.0) return
    val provider = provider() ?: return
    val target = Bukkit.getOfflinePlayer(targetName)
    if (target.uniqueId == payer.uniqueId) return
    val payerBefore = balance(provider, payer) ?: return
    val targetBefore = balance(provider, target) ?: return
    scheduler.runLater(
      payer,
      Runnable {
        val activeProvider = provider() ?: return@Runnable
        val payerAfter = balance(activeProvider, payer) ?: return@Runnable
        val targetAfter = balance(activeProvider, target) ?: return@Runnable
        val debited = payerBefore - payerAfter
        val credited = targetAfter - targetBefore
        if (debited <= MIN_BALANCE_CHANGE || credited <= MIN_BALANCE_CHANGE) return@Runnable
        eventStore.enqueue(
          RelationEvent(
            type = "vault_transfer",
            playerAUuid = payer.uniqueId.toString(),
            playerAName = payer.name,
            playerBUuid = target.uniqueId.toString(),
            playerBName = target.name ?: targetName,
            amount = minOf(debited, credited),
          )
        )
      },
      VERIFY_DELAY_TICKS,
    )
  }

  private fun provider(): Any? {
    if (!configManager.relationsEnabled || !configManager.relationsVaultEnabled) return null
    if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null
    return runCatching {
        val type = Class.forName(ECONOMY_CLASS)
        @Suppress("UNCHECKED_CAST")
        Bukkit.getServicesManager().getRegistration(type as Class<Any>)?.provider
      }
      .getOrNull()
  }

  private fun balance(provider: Any, player: OfflinePlayer): Double? {
    return runCatching {
        val method =
          provider.javaClass.methods.first {
            it.name == "getBalance" &&
              it.parameterCount == 1 &&
              OfflinePlayer::class.java.isAssignableFrom(it.parameterTypes[0])
          }
        (method.invoke(provider, player) as Number).toDouble()
      }
      .getOrNull()
  }

  private companion object {
    const val ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy"
    const val VERIFY_DELAY_TICKS = 20L
    const val MIN_BALANCE_CHANGE = 0.000001
  }
}
