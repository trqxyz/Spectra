package trqxyz.spectra.relations

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.SQLException
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.scheduler.SchedulerService

class RelationEventStore(
  private val plugin: SpectraPlugin,
  private val outboxWriter: RelationEventOutboxWriter,
  private val objectMapper: ObjectMapper,
  private val scheduler: SchedulerService,
) {
  private val pending = ArrayBlockingQueue<RelationEvent>(MAX_PENDING_EVENTS)
  private val retryBatch = ArrayDeque<RelationEvent>()
  private val drainMonitor = Any()
  private val draining = AtomicBoolean(false)
  private val accepting = AtomicBoolean(true)
  private val accepted = AtomicLong()
  private val persisted = AtomicLong()
  private val dropped = AtomicLong()
  private val failures = AtomicLong()
  private val lastDropWarningAt = AtomicLong()

  fun enqueue(event: RelationEvent) {
    enqueueAll(listOf(event))
  }

  fun enqueueAll(events: List<RelationEvent>) {
    if (events.isEmpty() || !accepting.get()) return
    var rejected = 0L
    for (event in events) {
      if (pending.offer(event)) accepted.incrementAndGet() else rejected++
    }
    if (rejected > 0L) {
      dropped.addAndGet(rejected)
      warnAboutDroppedEvents(rejected)
    }
    scheduleDrain()
  }

  private fun scheduleDrain(delayMillis: Long = 0L) {
    if (!accepting.get()) return
    if (!draining.compareAndSet(false, true)) return
    val task = Runnable { drain() }
    if (delayMillis > 0L) scheduler.runLaterAsync(task, delayMillis) else scheduler.runAsync(task)
  }

  private fun drain() {
    val retry = synchronized(drainMonitor) { drainOnce() }
    draining.set(false)
    if (accepting.get() && pendingCount() > 0) {
      scheduleDrain(if (retry) RETRY_DELAY_MS else NEXT_BATCH_DELAY_MS)
    }
  }

  fun flushAndStop(timeoutMillis: Long = SHUTDOWN_TIMEOUT_MS): Boolean {
    accepting.set(false)
    val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
    synchronized(drainMonitor) {
      while (pendingCount() > 0 && System.nanoTime() < deadline) {
        val retry = drainOnce()
        if (retry && System.nanoTime() < deadline) {
          try {
            Thread.sleep(SHUTDOWN_RETRY_DELAY_MS)
          } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            break
          }
        }
      }
    }
    val complete = pendingCount() == 0
    if (!complete) {
      plugin.logger.warning(
        "[Relations] Shutdown timed out with ${pendingCount()} event(s) not persisted"
      )
    }
    return complete
  }

  fun stats(): RelationEventStoreStats =
    RelationEventStoreStats(
      pending = pendingCount(),
      accepted = accepted.get(),
      persisted = persisted.get(),
      dropped = dropped.get(),
      failures = failures.get(),
    )

  private fun drainOnce(): Boolean {
    val batch = ArrayList<RelationEvent>(WRITE_BATCH_SIZE)
    while (retryBatch.isNotEmpty() && batch.size < WRITE_BATCH_SIZE) {
      batch += retryBatch.removeFirst()
    }
    pending.drainTo(batch, WRITE_BATCH_SIZE - batch.size)
    if (batch.isEmpty()) return false
    return try {
      outboxWriter.write(batch)
      persisted.addAndGet(batch.size.toLong())
      false
    } catch (error: SQLException) {
      retryBatch.addAll(batch)
      failures.incrementAndGet()
      plugin.logger.warning("[Relations] Failed to persist event batch: ${error.message}")
      true
    } catch (error: IllegalStateException) {
      retryBatch.addAll(batch)
      failures.incrementAndGet()
      plugin.logger.warning("[Relations] Failed to persist event batch: ${error.message}")
      true
    }
  }

  private fun pendingCount(): Int = synchronized(drainMonitor) { retryBatch.size + pending.size }

  private fun warnAboutDroppedEvents(count: Long) {
    val now = System.currentTimeMillis()
    val previous = lastDropWarningAt.get()
    if (
      now - previous >= DROP_WARNING_INTERVAL_MS && lastDropWarningAt.compareAndSet(previous, now)
    ) {
      plugin.logger.warning(
        "[Relations] Event queue is full; dropped $count event(s), ${dropped.get()} total"
      )
    }
  }

  fun context(values: Map<String, Any?>): String = objectMapper.writeValueAsString(values)

  private companion object {
    const val MAX_PENDING_EVENTS = 250_000
    const val WRITE_BATCH_SIZE = 2_000
    const val NEXT_BATCH_DELAY_MS = 10L
    const val RETRY_DELAY_MS = 1_000L
    const val SHUTDOWN_TIMEOUT_MS = 5_000L
    const val SHUTDOWN_RETRY_DELAY_MS = 50L
    const val DROP_WARNING_INTERVAL_MS = 30_000L
    const val NANOS_PER_MILLI = 1_000_000L
  }
}

data class RelationEventStoreStats(
  val pending: Int,
  val accepted: Long,
  val persisted: Long,
  val dropped: Long,
  val failures: Long,
)
