package trqxyz.spectra.utils

import kotlin.test.assertTrue
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Test

class MessageUtilTest {
  @Test
  fun `accepts legacy ampersand colors alongside MiniMessage`() {
    val component = MessageUtil.deserializeRaw("&cRed &lBold &r<#39ff14>Green")
    val json = GsonComponentSerializer.gson().serialize(component)

    assertTrue(json.contains("\"color\":\"red\""))
    assertTrue(json.contains("\"bold\":true"))
    assertTrue(json.contains("Green"))
  }
}
