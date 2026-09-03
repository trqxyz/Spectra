package trqxyz.spectra.integration

import java.util.UUID
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class GeyserUtilTest {
  @Test
  fun `missing optional bedrock integrations do not affect java players`() {
    assertFalse(GeyserUtil.isBedrockPlayer(UUID.fromString("00000000-0000-0000-0000-000000000001")))
  }
}
