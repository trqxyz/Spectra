package trqxyz.spectra.relations

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.database.DatabaseManager
import trqxyz.spectra.scheduler.SchedulerService

class RelationEventStoreTest {
  @Test
  fun `shutdown flush persists queued events and duplicate ids are idempotent`() {
    val dataSource = createDataSource()

    val plugin = mockk<SpectraPlugin>(relaxed = true)
    val databaseManager = mockk<DatabaseManager>()
    every { databaseManager.reportDataSource } returns dataSource
    val scheduler = mockk<SchedulerService>()
    every { scheduler.runAsync(any()) } returns mockk(relaxed = true)
    val store =
      RelationEventStore(
        plugin,
        RelationEventOutboxWriter(databaseManager),
        ObjectMapper(),
        scheduler,
      )
    val event =
      RelationEvent(
        eventId = "same-event",
        type = "item_transfer",
        playerAUuid = "a",
        playerAName = "Alpha",
        playerBUuid = "b",
        playerBName = "Bravo",
      )

    store.enqueueAll(listOf(event, event))

    assertEquals(2, store.stats().pending)
    assertTrue(store.flushAndStop())
    assertEquals(0, store.stats().pending)
    assertEquals(2, store.stats().persisted)
    dataSource.connection.use { connection ->
      val count =
        connection.createStatement().use { statement ->
          statement.executeQuery("SELECT COUNT(*) FROM relation_sync_outbox").use { result ->
            result.next()
            result.getInt(1)
          }
        }
      assertEquals(1, count)
    }
    dataSource.close()
  }

  private fun createDataSource(): HikariDataSource {
    val databaseFile = Files.createTempFile("spectra-relation-outbox-", ".db").toFile()
    databaseFile.deleteOnExit()
    val dataSource =
      HikariDataSource(
        HikariConfig().apply {
          jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
          maximumPoolSize = 1
        }
      )
    dataSource.connection.use { connection ->
      connection.createStatement().use {
        it.execute(
          """
          CREATE TABLE relation_sync_outbox (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              event_id TEXT NOT NULL UNIQUE,
              event_type TEXT NOT NULL,
              player_a_uuid TEXT,
              player_a_name TEXT,
              player_b_uuid TEXT,
              player_b_name TEXT,
              material TEXT,
              amount REAL,
              context_json TEXT,
              occurred_at INTEGER NOT NULL
          )
          """
            .trimIndent()
        )
      }
    }
    return dataSource
  }
}
