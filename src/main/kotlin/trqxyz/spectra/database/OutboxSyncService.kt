package trqxyz.spectra.database

import java.io.IOException
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.connect.CredentialsStore
import trqxyz.spectra.platform.scheduler.TaskHandle
import trqxyz.spectra.scheduler.SchedulerService

abstract class OutboxSyncService<T>(
  private val plugin: SpectraPlugin,
  protected val configManager: ConfigManager,
  private val credentialsStore: CredentialsStore,
  private val databaseManager: DatabaseManager,
  private val httpClient: OutboxHttpClient,
  private val scheduler: SchedulerService,
) {
  private val running = AtomicBoolean(false)
  private val syncing = AtomicBoolean(false)
  private val nextAttemptAt = AtomicLong(0L)
  private val retryDelay = AtomicLong(FLUSH_INTERVAL_MS)
  private var task: TaskHandle? = null

  protected abstract val endpointPath: String
  protected abstract val logTag: String

  protected open fun isEnabled(): Boolean = true

  protected abstract fun readBatch(connection: Connection, limit: Int): List<T>

  protected abstract fun eventId(event: T): String

  protected abstract fun deleteBatch(connection: Connection, eventIds: List<String>)

  fun start() {
    if (!running.compareAndSet(false, true)) return
    task = scheduler.runTimerAsync(Runnable { flush() }, INITIAL_DELAY_MS, FLUSH_INTERVAL_MS)
  }

  fun stop() {
    running.set(false)
    task?.cancel()
    task = null
  }

  fun flush() {
    if (
      isEnabled() &&
        System.currentTimeMillis() >= nextAttemptAt.get() &&
        syncing.compareAndSet(false, true)
    ) {
      performFlush()
    }
  }

  private fun performFlush() {
    try {
      val dataSource = databaseManager.reportDataSource ?: return
      val credentials = credentialsStore.read() ?: return
      dataSource.connection.use { connection -> syncBatch(connection, credentials.secretKey) }
    } catch (error: IOException) {
      postpone()
      if (running.get()) plugin.logger.warning("[$logTag] ${error.message}")
    } catch (error: SQLException) {
      postpone()
      if (running.get()) plugin.logger.warning("[$logTag] ${error.message}")
    } catch (error: IllegalArgumentException) {
      postpone()
      if (running.get()) plugin.logger.warning("[$logTag] ${error.message}")
    } finally {
      syncing.set(false)
    }
  }

  private fun syncBatch(connection: Connection, secretKey: String) {
    val events = readBatch(connection, BATCH_SIZE)
    if (events.isEmpty()) return
    if (httpClient.send(endpointPath, events, secretKey)) {
      deleteBatch(connection, events.map(::eventId))
      retryDelay.set(FLUSH_INTERVAL_MS)
      nextAttemptAt.set(0L)
    } else {
      postpone()
    }
  }

  private fun postpone() {
    val delay = retryDelay.get().coerceIn(FLUSH_INTERVAL_MS, MAX_RETRY_DELAY_MS)
    nextAttemptAt.set(System.currentTimeMillis() + delay)
    retryDelay.set((delay * 2).coerceAtMost(MAX_RETRY_DELAY_MS))
  }

  private companion object {
    const val INITIAL_DELAY_MS = 5_000L
    const val FLUSH_INTERVAL_MS = 5_000L
    const val MAX_RETRY_DELAY_MS = 60_000L
    const val BATCH_SIZE = 200
  }
}
