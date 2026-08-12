package trqxyz.spectra.relations

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import trqxyz.spectra.database.DatabaseManager

class RelationEventOutboxWriter(private val databaseManager: DatabaseManager) {
  fun write(events: List<RelationEvent>) {
    val dataSource = databaseManager.reportDataSource ?: error("Relation database is unavailable")
    dataSource.connection.use { connection -> write(connection, events) }
  }

  private fun write(connection: Connection, events: List<RelationEvent>) {
    connection.autoCommit = false
    connection.prepareStatement(insertSql(connection)).use { statement ->
      events.forEach { event -> add(statement, event) }
      statement.executeBatch()
    }
    connection.commit()
  }

  private fun insertSql(connection: Connection): String {
    val insert =
      if (connection.metaData.databaseProductName.contains("SQLite", ignoreCase = true)) {
        "INSERT OR IGNORE"
      } else {
        "INSERT IGNORE"
      }
    return """
      $insert INTO relation_sync_outbox
          (event_id, event_type, player_a_uuid, player_a_name, player_b_uuid,
           player_b_name, material, amount, context_json, occurred_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """
      .trimIndent()
  }

  private fun add(statement: PreparedStatement, event: RelationEvent) {
    var index = 0
    statement.setString(++index, event.eventId)
    statement.setString(++index, event.type)
    statement.setString(++index, event.playerAUuid)
    statement.setString(++index, event.playerAName)
    statement.setString(++index, event.playerBUuid)
    statement.setString(++index, event.playerBName)
    statement.setString(++index, event.material)
    if (event.amount == null) statement.setNull(++index, Types.DOUBLE)
    else statement.setDouble(++index, event.amount)
    statement.setString(++index, event.context)
    statement.setLong(++index, event.occurredAt)
    statement.addBatch()
  }
}
