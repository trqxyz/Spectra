package trqxyz.spectra.database

import java.sql.Connection
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.connect.CredentialsStore
import trqxyz.spectra.scheduler.SchedulerService

class ViolationSyncService(
  plugin: SpectraPlugin,
  configManager: ConfigManager,
  credentialsStore: CredentialsStore,
  databaseManager: DatabaseManager,
  httpClient: OutboxHttpClient,
  scheduler: SchedulerService,
) :
  OutboxSyncService<ViolationSyncService.SyncViolation>(
    plugin,
    configManager,
    credentialsStore,
    databaseManager,
    httpClient,
    scheduler,
  ) {
  override val endpointPath = "/api/public/server/violations/batch"
  override val logTag = "StorageSync"

  override fun readBatch(connection: Connection, limit: Int): List<SyncViolation> {
    connection
      .prepareStatement(
        """
        SELECT event_id, player_uuid, player_name, check_name, verbose, vl, created_at
        FROM violation_sync_outbox
        ORDER BY created_at, event_id
        LIMIT ?
        """
          .trimIndent()
      )
      .use { statement ->
        statement.setInt(1, limit)
        statement.executeQuery().use { result ->
          val events = ArrayList<SyncViolation>()
          while (result.next()) {
            events +=
              SyncViolation(
                eventId = result.getString("event_id"),
                playerUuid = result.getString("player_uuid"),
                playerName = result.getString("player_name"),
                checkName = result.getString("check_name"),
                verbose = result.getString("verbose"),
                vl = result.getInt("vl"),
                createdAt = result.getLong("created_at"),
              )
          }
          return events
        }
      }
  }

  override fun eventId(event: SyncViolation): String = event.eventId

  override fun deleteBatch(connection: Connection, eventIds: List<String>) {
    if (eventIds.isEmpty()) return
    val placeholders = eventIds.joinToString(",") { "?" }
    connection
      .prepareStatement("DELETE FROM violation_sync_outbox WHERE event_id IN ($placeholders)")
      .use { statement ->
        eventIds.forEachIndexed { index, eventId -> statement.setString(index + 1, eventId) }
        statement.executeUpdate()
      }
  }

  data class SyncViolation(
    val eventId: String,
    val playerUuid: String,
    val playerName: String,
    val checkName: String,
    val verbose: String,
    val vl: Int,
    val createdAt: Long,
  )
}
