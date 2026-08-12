package trqxyz.spectra.relations

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bukkit.entity.Player
import trqxyz.spectra.connect.CredentialsStore

class RelationIpCollector(
  private val credentialsStore: CredentialsStore,
  private val eventStore: RelationEventStore,
) {
  fun observe(player: Player) {
    val address = player.address?.address?.hostAddress ?: return
    val secret = credentialsStore.read()?.secretKey ?: return
    eventStore.enqueue(
      RelationEvent(
        type = "ip_observed",
        playerAUuid = player.uniqueId.toString(),
        playerAName = player.name,
        context = eventStore.context(mapOf("ipHash" to hash(address, secret))),
      )
    )
  }

  internal fun hash(address: String, secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(address.toByteArray(Charsets.UTF_8)).joinToString("") {
      "%02x".format(it.toInt() and BYTE_MASK)
    }
  }

  private companion object {
    const val BYTE_MASK = 0xff
  }
}
