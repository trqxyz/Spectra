package trqxyz.spectra.relations

import java.sql.Connection
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.connect.CredentialsStore
import trqxyz.spectra.database.DatabaseManager
import trqxyz.spectra.database.OutboxHttpClient
import trqxyz.spectra.database.OutboxSyncService
import trqxyz.spectra.scheduler.SchedulerService

class RelationSyncService(
  plugin: SpectraPlugin,
  configManager: ConfigManager,
  credentialsStore: CredentialsStore,
  databaseManager: DatabaseManager,
  httpClient: OutboxHttpClient,
  scheduler: SchedulerService,
) :
  OutboxSyncService<RelationSyncService.StoredRelationEvent>(
    plugin,
    configManager,
    credentialsStore,
    databaseManager,
    httpClient,
    scheduler,
  ) {
  override val endpointPath = "/api/public/server/relations/batch"
  override val logTag = "Relations"

  override fun isEnabled(): Boolean = configManager.relationsEnabled

  override fun readBatch(connection: Connection, limit: Int): List<StoredRelationEvent> {
    connection
      .prepareStatement(
        """
        SELECT event_id, event_type, player_a_uuid, player_a_name, player_b_uuid,
               player_b_name, material, amount, context_json, occurred_at
        FROM relation_sync_outbox
        ORDER BY id
        LIMIT ?
        """
          .trimIndent()
      )
      .use { statement ->
        statement.setInt(1, limit)
        statement.executeQuery().use { result ->
          val events = ArrayList<StoredRelationEvent>()
          while (result.next()) {
            events +=
              StoredRelationEvent(
                eventId = result.getString("event_id"),
                type = result.getString("event_type"),
                playerAUuid = result.getString("player_a_uuid"),
                playerAName = result.getString("player_a_name"),
                playerBUuid = result.getString("player_b_uuid"),
                playerBName = result.getString("player_b_name"),
                material = result.getString("material"),
                amount = result.getObject("amount")?.let { result.getDouble("amount") },
                context = result.getString("context_json"),
                occurredAt = result.getLong("occurred_at"),
              )
          }
          return events
        }
      }
  }

  override fun eventId(event: StoredRelationEvent): String = event.eventId

  override fun deleteBatch(connection: Connection, eventIds: List<String>) {
    val placeholders = eventIds.joinToString(",") { "?" }
    connection
      .prepareStatement("DELETE FROM relation_sync_outbox WHERE event_id IN ($placeholders)")
      .use { statement ->
        eventIds.forEachIndexed { index, id -> statement.setString(index + 1, id) }
        statement.executeUpdate()
      }
  }

  data class StoredRelationEvent(
    val eventId: String,
    val type: String,
    val playerAUuid: String?,
    val playerAName: String?,
    val playerBUuid: String?,
    val playerBName: String?,
    val material: String?,
    val amount: Double?,
    val context: String?,
    val occurredAt: Long,
  )
}
