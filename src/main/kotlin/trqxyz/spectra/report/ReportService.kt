package trqxyz.spectra.report

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.database.DatabaseManager

enum class ReportStatus {
  OPEN,
  CLAIMED,
  CLOSED,
}

enum class ReportSource {
  PLAYER,
  AI,
}

@Suppress("LongParameterList")
data class ReportModelResult(
  val model: String,
  val status: String,
  val probability: Double,
  val verdict: String,
  val accepted: Boolean,
  val actionable: Boolean,
  val confidence: Double,
  val novelty: Double,
  val buffer: Double,
  val modelVersion: String,
)

@Suppress("LongParameterList")
data class PlayerReport(
  val id: Long,
  val source: ReportSource,
  val reporterId: UUID,
  val reporterName: String,
  val targetId: UUID,
  val targetName: String,
  val reason: String,
  val createdAt: Instant,
  val status: ReportStatus,
  val handledBy: String?,
  val updatedAt: Instant,
  val model: String? = null,
  val probability: Double? = null,
  val verdict: String? = null,
  val confidence: Double? = null,
  val novelty: Double? = null,
  val buffer: Double? = null,
  val modelVersion: String? = null,
  val modelResults: List<ReportModelResult> = emptyList(),
)

sealed interface CreateReportResult {
  data class Created(val report: PlayerReport) : CreateReportResult

  data class Cooldown(val remainingSeconds: Long) : CreateReportResult

  data object LimitReached : CreateReportResult
}

enum class ReportMutationResult {
  UPDATED,
  NOT_FOUND,
  ALREADY_CLOSED,
}

data class ReportPage(val entries: List<PlayerReport>, val page: Int, val maxPages: Int)

@Suppress("TooManyFunctions")
class ReportService(
  private val databaseManager: DatabaseManager,
  private val configManager: ConfigManager,
) {
  private val memory = CopyOnWriteArrayList<PlayerReport>()
  private val ids = AtomicLong(System.currentTimeMillis())

  fun create(
    reporterId: UUID,
    reporterName: String,
    targetId: UUID,
    targetName: String,
    reason: String,
  ): CreateReportResult {
    val now = Instant.now()
    val latest = latestByReporter(reporterId)
    if (latest != null) {
      val remaining =
        configManager.reportCooldownMillis - (now.toEpochMilli() - latest.createdAt.toEpochMilli())
      if (remaining > 0L) return CreateReportResult.Cooldown((remaining + 999L) / 1000L)
    }
    if (openByReporter(reporterId) >= configManager.reportMaxOpenPerPlayer) {
      return CreateReportResult.LimitReached
    }
    val report =
      PlayerReport(
        ids.incrementAndGet(),
        ReportSource.PLAYER,
        reporterId,
        reporterName,
        targetId,
        targetName,
        reason,
        now,
        ReportStatus.OPEN,
        null,
        now,
      )
    insert(report)
    return CreateReportResult.Created(report)
  }

  fun createAi(
    targetId: UUID,
    targetName: String,
    modelResults: List<ReportModelResult>,
  ): PlayerReport {
    require(modelResults.isNotEmpty())
    val now = Instant.now()
    val primary = modelResults.maxBy { it.probability }
    val report =
      PlayerReport(
        ids.incrementAndGet(),
        ReportSource.AI,
        AI_REPORTER_UUID,
        "Spectra AI",
        targetId,
        targetName,
        "AI analysis: ${modelResults.size} model(s), maximum ${"%.2f".format(primary.probability * 100.0)}%",
        now,
        ReportStatus.OPEN,
        null,
        now,
        primary.model,
        primary.probability,
        primary.verdict,
        primary.confidence,
        primary.novelty,
        primary.buffer,
        primary.modelVersion,
        modelResults.sortedByDescending { it.probability },
      )
    insert(report)
    return report
  }

  fun page(requestedPage: Int, pageSize: Int): ReportPage {
    val dataSource = databaseManager.reportDataSource
    if (dataSource == null) {
      val open =
        memory.filter { it.status != ReportStatus.CLOSED }.sortedByDescending { it.createdAt }
      val maxPages = kotlin.math.max(1, (open.size + pageSize - 1) / pageSize)
      val page = requestedPage.coerceIn(1, maxPages)
      return ReportPage(open.drop((page - 1) * pageSize).take(pageSize), page, maxPages)
    }
    dataSource.connection.use { connection ->
      val count =
        connection.prepareStatement("SELECT COUNT(*) FROM reports WHERE status <> 'CLOSED'").use {
          statement ->
          statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
          }
        }
      val maxPages = kotlin.math.max(1, (count + pageSize - 1) / pageSize)
      val page = requestedPage.coerceIn(1, maxPages)
      val entries =
        connection
          .prepareStatement(
            "SELECT * FROM reports WHERE status <> 'CLOSED' ORDER BY created_at DESC LIMIT ? OFFSET ?"
          )
          .use { statement ->
            statement.setInt(1, pageSize)
            statement.setInt(2, (page - 1) * pageSize)
            statement.executeQuery().use { result ->
              buildList { while (result.next()) add(read(result)) }
            }
          }
          .map { report ->
            report.copy(modelResults = loadModelResults(connection, report.id, report))
          }
      return ReportPage(entries, page, maxPages)
    }
  }

  fun claim(id: Long, staffName: String): ReportMutationResult =
    mutate(id, staffName, ReportStatus.CLAIMED)

  fun close(id: Long, staffName: String): ReportMutationResult =
    mutate(id, staffName, ReportStatus.CLOSED)

  private fun latestByReporter(reporterId: UUID): PlayerReport? {
    val dataSource =
      databaseManager.reportDataSource
        ?: return memory
          .filter { it.source == ReportSource.PLAYER && it.reporterId == reporterId }
          .maxByOrNull { it.createdAt }
    dataSource.connection.use { connection ->
      return connection
        .prepareStatement(
          "SELECT * FROM reports WHERE source = 'PLAYER' AND reporter_uuid = ? ORDER BY created_at DESC LIMIT 1"
        )
        .use { statement ->
          statement.setString(1, reporterId.toString())
          statement.executeQuery().use { result -> if (result.next()) read(result) else null }
        }
    }
  }

  private fun openByReporter(reporterId: UUID): Int {
    val dataSource =
      databaseManager.reportDataSource
        ?: return memory.count {
          it.source == ReportSource.PLAYER &&
            it.reporterId == reporterId &&
            it.status != ReportStatus.CLOSED
        }
    dataSource.connection.use { connection ->
      return connection
        .prepareStatement(
          "SELECT COUNT(*) FROM reports WHERE source = 'PLAYER' AND reporter_uuid = ? AND status <> 'CLOSED'"
        )
        .use { statement ->
          statement.setString(1, reporterId.toString())
          statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
          }
        }
    }
  }

  private fun insert(report: PlayerReport) {
    val dataSource = databaseManager.reportDataSource
    if (dataSource == null) {
      memory += report
      while (memory.size > configManager.reportMaxStored) memory.removeAt(0)
      return
    }
    dataSource.connection.use { connection ->
      val autoCommit = connection.autoCommit
      connection.autoCommit = false
      try {
        insertReport(connection, report)
        insertModelResults(connection, report)
        trim(connection)
        connection.commit()
      } catch (error: Exception) {
        connection.rollback()
        throw error
      } finally {
        connection.autoCommit = autoCommit
      }
    }
  }

  private fun insertReport(connection: Connection, report: PlayerReport) {
    connection
      .prepareStatement(
        "INSERT INTO reports (id, source, reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, status, handled_by, updated_at, model, probability, verdict, confidence, novelty, buffer_value, model_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      )
      .use { statement ->
        statement.setLong(1, report.id)
        statement.setString(2, report.source.name)
        statement.setString(3, report.reporterId.toString())
        statement.setString(4, report.reporterName)
        statement.setString(5, report.targetId.toString())
        statement.setString(6, report.targetName)
        statement.setString(7, report.reason)
        statement.setLong(8, report.createdAt.toEpochMilli())
        statement.setString(9, report.status.name)
        statement.setString(10, report.handledBy)
        statement.setLong(11, report.updatedAt.toEpochMilli())
        statement.setString(12, report.model)
        statement.setObject(13, report.probability)
        statement.setString(14, report.verdict)
        statement.setObject(15, report.confidence)
        statement.setObject(16, report.novelty)
        statement.setObject(17, report.buffer)
        statement.setString(18, report.modelVersion)
        statement.executeUpdate()
      }
  }

  private fun insertModelResults(connection: Connection, report: PlayerReport) {
    if (report.modelResults.isEmpty()) return
    connection
      .prepareStatement(
        "INSERT INTO report_model_results (report_id, model, status, probability, verdict, accepted, actionable, confidence, novelty, buffer_value, model_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      )
      .use { statement ->
        for (result in report.modelResults) {
          statement.setLong(1, report.id)
          statement.setString(2, result.model)
          statement.setString(3, result.status)
          statement.setDouble(4, result.probability)
          statement.setString(5, result.verdict)
          statement.setInt(6, if (result.accepted) 1 else 0)
          statement.setInt(7, if (result.actionable) 1 else 0)
          statement.setDouble(8, result.confidence)
          statement.setDouble(9, result.novelty)
          statement.setDouble(10, result.buffer)
          statement.setString(11, result.modelVersion)
          statement.addBatch()
        }
        statement.executeBatch()
      }
  }

  private fun trim(connection: Connection) {
    val staleIds =
      connection
        .prepareStatement("SELECT id FROM reports ORDER BY created_at DESC LIMIT ? OFFSET ?")
        .use { statement ->
          statement.setInt(1, 1000000)
          statement.setInt(2, configManager.reportMaxStored)
          statement.executeQuery().use { result ->
            buildList { while (result.next()) add(result.getLong(1)) }
          }
        }
    if (staleIds.isEmpty()) return
    connection.prepareStatement("DELETE FROM reports WHERE id = ?").use { statement ->
      staleIds.forEach { id ->
        statement.setLong(1, id)
        statement.addBatch()
      }
      statement.executeBatch()
    }
  }

  private fun mutate(id: Long, staffName: String, status: ReportStatus): ReportMutationResult {
    val dataSource = databaseManager.reportDataSource
    if (dataSource == null) {
      val index = memory.indexOfFirst { it.id == id }
      if (index < 0) return ReportMutationResult.NOT_FOUND
      if (memory[index].status == ReportStatus.CLOSED) {
        return ReportMutationResult.ALREADY_CLOSED
      }
      memory[index] =
        memory[index].copy(status = status, handledBy = staffName, updatedAt = Instant.now())
      return ReportMutationResult.UPDATED
    }
    dataSource.connection.use { connection ->
      val current =
        connection.prepareStatement("SELECT status FROM reports WHERE id = ?").use { statement ->
          statement.setLong(1, id)
          statement.executeQuery().use { result ->
            if (result.next()) result.getString(1) else null
          }
        } ?: return ReportMutationResult.NOT_FOUND
      if (current == ReportStatus.CLOSED.name) return ReportMutationResult.ALREADY_CLOSED
      connection
        .prepareStatement(
          "UPDATE reports SET status = ?, handled_by = ?, updated_at = ? WHERE id = ?"
        )
        .use { statement ->
          statement.setString(1, status.name)
          statement.setString(2, staffName)
          statement.setLong(3, System.currentTimeMillis())
          statement.setLong(4, id)
          statement.executeUpdate()
        }
      return ReportMutationResult.UPDATED
    }
  }

  private fun loadModelResults(
    connection: Connection,
    reportId: Long,
    report: PlayerReport,
  ): List<ReportModelResult> {
    val results =
      connection
        .prepareStatement(
          "SELECT * FROM report_model_results WHERE report_id = ? ORDER BY probability DESC"
        )
        .use { statement ->
          statement.setLong(1, reportId)
          statement.executeQuery().use { result ->
            buildList {
              while (result.next()) {
                add(
                  ReportModelResult(
                    result.getString("model"),
                    result.getString("status"),
                    result.getDouble("probability"),
                    result.getString("verdict"),
                    result.getInt("accepted") != 0,
                    result.getInt("actionable") != 0,
                    result.getDouble("confidence"),
                    result.getDouble("novelty"),
                    result.getDouble("buffer_value"),
                    result.getString("model_version"),
                  )
                )
              }
            }
          }
        }
    if (results.isNotEmpty() || report.source != ReportSource.AI || report.model == null) {
      return results
    }
    return listOf(
      ReportModelResult(
        report.model,
        "legacy",
        report.probability ?: 0.0,
        report.verdict ?: "unknown",
        false,
        false,
        report.confidence ?: 0.0,
        report.novelty ?: 0.0,
        report.buffer ?: 0.0,
        report.modelVersion ?: "unknown",
      )
    )
  }

  private fun read(result: ResultSet): PlayerReport =
    PlayerReport(
      result.getLong("id"),
      ReportSource.valueOf(result.getString("source")),
      UUID.fromString(result.getString("reporter_uuid")),
      result.getString("reporter_name"),
      UUID.fromString(result.getString("target_uuid")),
      result.getString("target_name"),
      result.getString("reason"),
      Instant.ofEpochMilli(result.getLong("created_at")),
      ReportStatus.valueOf(result.getString("status")),
      result.getString("handled_by"),
      Instant.ofEpochMilli(result.getLong("updated_at")),
      result.getString("model"),
      result.getObject("probability")?.let { result.getDouble("probability") },
      result.getString("verdict"),
      result.getObject("confidence")?.let { result.getDouble("confidence") },
      result.getObject("novelty")?.let { result.getDouble("novelty") },
      result.getObject("buffer_value")?.let { result.getDouble("buffer_value") },
      result.getString("model_version"),
    )

  private companion object {
    val AI_REPORTER_UUID: UUID = UUID(0L, 0L)
  }
}
