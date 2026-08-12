package trqxyz.spectra.database

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI
import trqxyz.spectra.config.ConfigManager

class OutboxHttpClient(
  private val configManager: ConfigManager,
  private val objectMapper: ObjectMapper,
) {
  fun send(endpointPath: String, events: List<*>, secretKey: String): Boolean {
    val base = configManager.connectPanelUrl.trimEnd('/')
    val connection = URI.create("$base$endpointPath").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = REQUEST_TIMEOUT_MS
    connection.readTimeout = REQUEST_TIMEOUT_MS
    connection.doOutput = true
    connection.useCaches = false
    connection.setRequestProperty("Authorization", "Bearer $secretKey")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    return try {
      connection.outputStream.use { output ->
        objectMapper.writeValue(output, mapOf("events" to events))
      }
      connection.responseCode in MIN_SUCCESS_STATUS..MAX_SUCCESS_STATUS
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val REQUEST_TIMEOUT_MS = 5_000
    const val MIN_SUCCESS_STATUS = 200
    const val MAX_SUCCESS_STATUS = 299
  }
}
