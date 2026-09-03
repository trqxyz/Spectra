/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package trqxyz.spectra.connect

import java.util.concurrent.atomic.AtomicLong
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.database.OutboxHttpClient
import trqxyz.spectra.platform.scheduler.TaskHandle
import trqxyz.spectra.scheduler.SchedulerService

class ServerHeartbeatService(
  private val plugin: SpectraPlugin,
  private val credentialsStore: CredentialsStore,
  private val httpClient: OutboxHttpClient,
  private val scheduler: SchedulerService,
) {
  private var timer: TaskHandle? = null
  private val lastFailureLogMillis = AtomicLong(0L)

  fun start() {
    if (timer != null) return
    timer = scheduler.runTimer(::collectAndSend, INITIAL_DELAY_TICKS, PERIOD_TICKS)
  }

  fun stop() {
    timer?.cancel()
    timer = null
  }

  private fun collectAndSend() {
    val credentials = credentialsStore.read() ?: return
    val onlinePlayers = plugin.server.onlinePlayers.size
    val version = plugin.pluginMeta.version
    scheduler.runAsync {
      val successful =
        runCatching {
            httpClient.sendJson(
              HEARTBEAT_PATH,
              mapOf("online_players" to onlinePlayers, "plugin_version" to version),
              credentials.secretKey,
            )
          }
          .getOrElse {
            logFailure("could not reach the backend: ${it.message}")
            false
          }
      if (!successful) {
        logFailure("backend rejected the heartbeat")
      }
    }
  }

  private fun logFailure(message: String) {
    val now = System.currentTimeMillis()
    val previous = lastFailureLogMillis.get()
    if (
      now - previous >= FAILURE_LOG_INTERVAL_MS && lastFailureLogMillis.compareAndSet(previous, now)
    ) {
      plugin.logger.warning("[Connect] $message")
    }
  }

  private companion object {
    const val HEARTBEAT_PATH = "/api/public/server/heartbeat"
    const val INITIAL_DELAY_TICKS = 20L
    const val PERIOD_TICKS = 20L * 30L
    const val FAILURE_LOG_INTERVAL_MS = 5L * 60L * 1000L
  }
}
