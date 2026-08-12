/*
 * This file is part of GrimAC - https://github.com/GrimAnticheat/Grim
 * Copyright (C) 2021-2026 GrimAC, DefineOutside and contributors
 *
 * GrimAC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GrimAC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.checks.impl.misc

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.manager.server.ServerVersion
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage
import java.nio.charset.StandardCharsets
import net.kyori.adventure.text.Component
import trqxyz.spectra.alert.AlertManager
import trqxyz.spectra.alert.AlertType
import trqxyz.spectra.checks.AbstractCheck
import trqxyz.spectra.checks.CheckData
import trqxyz.spectra.checks.CheckFactory
import trqxyz.spectra.checks.type.PacketCheck
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.utils.ChatUtil
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil

@CheckData(name = "ClientBrand_Internal")
class ClientBrand(
  spectraPlayer: SpectraPlayer,
  private val configManager: ConfigManager,
  private val alertManager: AlertManager,
) : AbstractCheck(spectraPlayer), PacketCheck {
  companion object {
    private val CHANNEL =
      if (PacketEvents.getAPI().serverManager.version.isNewerThanOrEquals(ServerVersion.V_1_13)) {
        "minecraft:brand"
      } else {
        "MC|Brand"
      }
  }

  var brand: String = "vanilla"
    private set

  private var hasBrand = false

  interface Factory : CheckFactory {
    override fun create(player: SpectraPlayer): ClientBrand
  }

  override fun onPacketReceive(event: PacketReceiveEvent) {
    if (event.packetType == PacketType.Play.Client.PLUGIN_MESSAGE) {
      val packet = WrapperPlayClientPluginMessage(event)
      handle(packet.channelName, packet.data)
    } else if (event.packetType == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
      val packet = WrapperConfigClientPluginMessage(event)
      handle(packet.channelName, packet.data)
    }
  }

  private fun handle(channel: String, data: ByteArray) {
    if (channel != CHANNEL || hasBrand) {
      return
    }

    val spectraPlayer = spectraPlayer
    hasBrand = true

    if (data.size > 64 || data.isEmpty()) {
      brand = "invalid (${data.size} bytes)"
    } else {
      val brandBytes = ByteArray(data.size - 1)
      System.arraycopy(data, 1, brandBytes, 0, brandBytes.size)

      brand = String(brandBytes, StandardCharsets.UTF_8).replace(" (Velocity)", "")
      brand = ChatUtil.stripColor(brand) ?: brand
    }

    spectraPlayer.brand = brand

    if (!configManager.isClientIgnored(brand)) {
      val component: Component =
        MessageUtil.getMessage(
          Message.BRAND_NOTIFICATION,
          "player",
          spectraPlayer.player.name,
          "brand",
          brand,
        )
      alertManager.send(component, AlertType.BRAND)
    }

    val hasReachExploit =
      brand.contains("forge") &&
        spectraPlayer.user.clientVersion.isNewerThanOrEquals(ClientVersion.V_1_18_2) &&
        spectraPlayer.user.clientVersion.isOlderThan(ClientVersion.V_1_19_4)

    if (hasReachExploit && configManager.isDisconnectBlacklistedForge()) {
      spectraPlayer.disconnect(MessageUtil.getMessage(Message.BRAND_DISCONNECT_FORGE))
    }
  }
}
