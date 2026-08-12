package trqxyz.spectra.connect

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager

class RemoteConfigService(
  private val plugin: SpectraPlugin,
  private val configManager: ConfigManager,
  private val credentialsStore: CredentialsStore,
  private val objectMapper: ObjectMapper,
) {
  private val loading = AtomicBoolean(false)
  private val lastAttempt = AtomicLong(0L)
  private val lastRevision = AtomicLong(-1L)
  private val listeners = CopyOnWriteArrayList<() -> Unit>()

  fun onApplied(listener: () -> Unit) {
    listeners += listener
  }

  fun refreshIfDue(): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastAttempt.get() < REFRESH_INTERVAL_MS) return false
    return refresh()
  }

  fun refresh(force: Boolean = false): Boolean {
    val now = System.currentTimeMillis()
    if (!force && now - lastAttempt.get() < REFRESH_INTERVAL_MS) return false
    if (!loading.compareAndSet(false, true)) return false
    lastAttempt.set(now)
    return try {
      val credentials = credentialsStore.read() ?: return false
      val base = configManager.connectPanelUrl.trimEnd('/')
      val connection =
        URI.create("$base/api/public/server/config").toURL().openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 5000
      connection.readTimeout = 5000
      connection.setRequestProperty("Authorization", "Bearer ${credentials.secretKey}")
      connection.setRequestProperty("Accept", "application/json")
      connection.useCaches = false
      try {
        if (connection.responseCode !in 200..299) return false
        connection.inputStream.use { input ->
          val root = objectMapper.readTree(input)
          val revision = root.path("revision").asLong(-1L)
          if (!force && revision >= 0L && revision == lastRevision.get()) return false
          if (!configManager.applyRemoteConfig(root.path("config"))) return false
          lastRevision.set(revision)
          listeners.forEach { listener ->
            runCatching(listener).onFailure {
              plugin.logger.warning(
                "[RemoteConfig] Failed to apply runtime settings: ${it.message}"
              )
            }
          }
        }
        true
      } finally {
        connection.disconnect()
      }
    } catch (error: Exception) {
      plugin.logger.warning("[RemoteConfig] ${error.message}")
      false
    } finally {
      loading.set(false)
    }
  }

  private companion object {
    const val REFRESH_INTERVAL_MS = 30_000L
  }
}
