package trqxyz.spectra.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PanelUriTest {
  @Test
  fun `accepts https and local development urls`() {
    assertEquals("https://panel.example", panelUri("https://panel.example").toString())
    assertEquals("http://127.0.0.1:8000", panelUri("http://127.0.0.1:8000").toString())
  }

  @Test
  fun `rejects insecure or credential-bearing panel urls`() {
    assertFailsWith<IllegalArgumentException> { panelUri("http://panel.example") }
    assertFailsWith<IllegalArgumentException> { panelUri("https://key@panel.example") }
  }

  @Test
  fun `retains an optional reverse proxy base path`() {
    assertEquals(
      "https://panel.example/spectra",
      panelUri("https://panel.example/spectra").toString(),
    )
  }
}
