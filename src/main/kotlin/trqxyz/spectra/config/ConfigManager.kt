/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 KaelusAI
 *
 * This file contains code derived from GrimAC.
 * The original authors of GrimAC are credited below.
 *
 * Copyright (c) 2021-2026 GrimAC, DefineOutside and contributors.
 *
 * Spectra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Spectra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.config

import java.io.File
import java.util.EnumSet
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import ru.vyarus.yaml.updater.YamlUpdater
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.connect.CredentialsStore
import trqxyz.spectra.debug.DebugCategory

class ConfigManager(
  private val plugin: SpectraPlugin,
  private val credentialsStore: CredentialsStore,
) {
  var config: ConfigView = ConfigView(CommentedConfigurationNode.root())
    private set

  var punishments: ConfigView = ConfigView(CommentedConfigurationNode.root())
    private set

  var monitorConfig: ConfigView = ConfigView(CommentedConfigurationNode.root())
    private set

  private var aiEnabled = false
  var aiEnforcementMode: AiEnforcementMode = AiEnforcementMode.SHADOW
    private set

  var aiServerUrl: String = ""
    private set

  var aiApiKey: String = ""
    private set

  var connectPanelUrl: String = ""
    private set

  val aiSequence: Int
    get() = STREAM_WINDOW

  val aiStep: Int
    get() = STREAM_WINDOW

  // Comma-separated models the server should run (sent as the X-Models header).
  // The plugin streams a fixed short, non-overlapping window and the server
  // buffers per player, so models no longer map to a client-side tick count.
  var aiModels: String = "flash,pro,night"
    private set

  var aiContinuous: Boolean = false
    private set

  var aiFlag: Double = 0.0
    private set

  var aiResetOnFlag: Double = 0.0
    private set

  var aiBufferMultiplier: Double = 0.0
    private set

  var aiBufferDecrease: Double = 0.0
    private set

  private var aiDamageReductionEnabled = false
  var aiDamageReductionProb: Double = 0.0
    private set

  var aiDamageReductionMultiplier: Double = 0.0
    private set

  private var aiWorldGuardEnabled = false
  var aiDisabledRegions: Map<String, List<String>> = emptyMap()
    private set

  private var bedrockExemptEnabled = false

  var persistentBufferEnabled: Boolean = false
    private set

  var persistentBufferTtlMillis: Long = 0L
    private set

  var persistentBufferCap: Double = 0.0
    private set

  var persistentBufferDecayPerHour: Double = 0.0
    private set

  var persistentBufferDisconnectWindowMillis: Long = 0L
    private set

  var persistentBufferSaveThreshold: Double = 0.0
    private set

  var batchEnabled: Boolean = true
    private set

  var batchMaxSize: Int = 0
    private set

  var batchMaxDelayMs: Long = 0L
    private set

  var batchMaxPendingItems: Int = 0
    private set

  var outboundMaxPendingPerPlayer: Int = 0
    private set

  var retryMaxAttempts: Int = 0
    private set

  var retryInitialDelayMs: Long = 0L
    private set

  var retryMaxDelayMs: Long = 0L
    private set

  var retryMultiplier: Double = 0.0
    private set

  var retryJitter: Double = 0.0
    private set

  private var ignoredClientPatterns: List<Pattern> = emptyList()
  private var disconnectBlacklistedForge = false

  var suspiciousAlertsBuffer: Double = 0.0
    private set

  var reportsEnabled: Boolean = false
    private set

  var reportAiEnabled: Boolean = true
    private set

  var historyEnabled: Boolean = true
    private set

  var relationsEnabled: Boolean = false
    private set

  var relationsWorldGuardEnabled: Boolean = false
    private set

  var relationsVaultEnabled: Boolean = false
    private set

  var relationsContainersEnabled: Boolean = false
    private set

  var relationsItemsEnabled: Boolean = false
    private set

  var relationsBanStatusEnabled: Boolean = false
    private set

  var relationsIpAddressesEnabled: Boolean = false
    private set

  var relationsBanProvider: String = "none"
    private set

  var reportCooldownMillis: Long = 0L
    private set

  var reportMaxOpenPerPlayer: Int = 0
    private set

  var reportMinReasonLength: Int = 0
    private set

  var reportMaxReasonLength: Int = 0
    private set

  var reportMaxStored: Int = 0
    private set

  var reportNotifyStaff: Boolean = true
    private set

  var cancelDuplicatePacket: Boolean = true
    private set

  var forceCancelDuplicatePacket: Boolean = false
    private set

  private var debugEnabled = false
  var enabledDebugCategories: Set<DebugCategory> = emptySet()
    private set

  init {
    loadConfigs()
  }

  fun reloadConfig() {
    loadConfigs()
  }

  fun isAiEnabled(): Boolean = aiEnabled

  fun isAiEnforcementEnabled(): Boolean = aiEnforcementMode == AiEnforcementMode.ENFORCE

  fun isAiDamageReductionEnabled(): Boolean = aiDamageReductionEnabled

  fun isAiWorldGuardEnabled(): Boolean = aiWorldGuardEnabled

  fun isBedrockExemptEnabled(): Boolean = bedrockExemptEnabled

  fun isDisconnectBlacklistedForge(): Boolean = disconnectBlacklistedForge

  fun isDebugEnabled(): Boolean = debugEnabled

  private fun loadConfigs() {
    if (!plugin.dataFolder.exists()) {
      plugin.dataFolder.mkdirs()
    }

    config = loadConfig("config.yml", migrate = true)
    punishments = loadConfig("punishments.yml")
    monitorConfig = loadConfig("monitor.yml")

    loadValues()
  }

  private fun loadConfig(fileName: String, migrate: Boolean = false): ConfigView {
    val file = File(plugin.dataFolder, fileName)
    if (!file.exists()) {
      plugin.saveResource(fileName, false)
    }

    if (migrate) {
      runMigration(file, fileName)
    }

    return try {
      val loader = YamlConfigurationLoader.builder().path(file.toPath()).build()
      ConfigView(loader.load())
    } catch (e: Exception) {
      plugin.logger.severe("Failed to load $fileName: ${e.message}")
      ConfigView(CommentedConfigurationNode.root())
    }
  }

  private fun runMigration(file: File, fileName: String) {
    val updateStream = javaClass.classLoader.getResourceAsStream(fileName) ?: return

    val currentVersion = ConfigMigrations.readVersion(file)
    val drops = ConfigMigrations.forcedDropsForUpgradeFrom(currentVersion)

    val report =
      runCatching {
          YamlUpdater.create(file, updateStream).backup(true).deleteProps(drops).update()
        }
        .onFailure {
          plugin.logger.warning("[Config] Migration of $fileName failed: ${it.message}")
        }
        .getOrNull()

    if (report != null && report.isConfigChanged) {
      val added = report.added.map { it.path }
      val removed = report.removed.map { it.path }
      if (added.isNotEmpty()) {
        plugin.logger.info(
          "[Config] Added ${added.size} key(s) to $fileName: ${added.joinToString(", ")}"
        )
      }
      if (removed.isNotEmpty()) {
        plugin.logger.info(
          "[Config] Removed ${removed.size} key(s) from $fileName: ${removed.joinToString(", ")}"
        )
      }
      report.backup?.let {
        plugin.logger.info("[Config] Backup saved to ${it.name} before migrating $fileName")
      }
    }
  }

  private fun normalizeModels(raw: String): String {
    val valid = setOf("flash", "pro", "night")
    val picked = raw.split(",").map { it.trim().lowercase() }.filter { it in valid }.distinct()
    return if (picked.isEmpty()) "flash" else picked.joinToString(",")
  }

  private fun loadValues() {
    aiEnabled = config.getBoolean("ai.enabled", false)
    val enforcementValue = config.getString("ai.enforcement-mode", "shadow")
    val parsedEnforcementMode = AiEnforcementMode.parse(enforcementValue)
    aiEnforcementMode = parsedEnforcementMode ?: AiEnforcementMode.SHADOW
    if (parsedEnforcementMode == null) {
      plugin.logger.warning(
        "[Config] Unknown ai.enforcement-mode '$enforcementValue'; using safe shadow mode."
      )
    }
    aiModels = normalizeModels(config.getString("ai.models", "flash,pro,night"))
    aiServerUrl = config.getString("ai.server", "")
    val configKey = config.getString("ai.api-key", "API-KEY")
    aiApiKey = configKey
    credentialsStore
      .read()
      ?.secretKey
      ?.takeIf { it.isNotBlank() }
      ?.let {
        aiApiKey = it
        if (configKey.isNotBlank() && configKey != "API-KEY") {
          plugin.logger.warning(
            "config.yml still has ai.api-key set, but this server is linked via /spectra connect - " +
              "the config key is ignored. Remove it from config.yml."
          )
        }
      }

    val configuredPanelUrl = config.getString("connect.panel-url", DEFAULT_PANEL_URL)
    connectPanelUrl =
      if (configuredPanelUrl == LEGACY_PANEL_URL) {
        plugin.logger.warning(
          "[Config] Replacing legacy panel.kaelus.dev default with $DEFAULT_PANEL_URL. " +
            "Set connect.panel-url only when your backend is remote."
        )
        DEFAULT_PANEL_URL
      } else {
        configuredPanelUrl
      }
    aiContinuous = config.getBoolean("ai.continuous", false)

    aiFlag = config.getDouble("ai.buffer.flag", 50.0)
    aiResetOnFlag = config.getDouble("ai.buffer.reset-on-flag", 25.0)
    aiBufferMultiplier = config.getDouble("ai.buffer.multiplier", 100.0)
    aiBufferDecrease = config.getDouble("ai.buffer.decrease", 0.25)

    aiDamageReductionEnabled = config.getBoolean("ai.damage-reduction.enabled", true)
    aiDamageReductionProb = config.getDouble("ai.damage-reduction.prob", 0.9)
    aiDamageReductionMultiplier = config.getDouble("ai.damage-reduction.multiplier", 1.0)

    aiWorldGuardEnabled = config.getBoolean("ai.worldguard.enabled", true)
    aiDisabledRegions = loadDisabledRegions()

    bedrockExemptEnabled = config.getBoolean("exemptions.bedrock", true)

    persistentBufferEnabled = config.getBoolean("ai.persistent-buffer.enabled", true)
    val ttlHours =
      config.getLong("ai.persistent-buffer.ttl-hours", DEFAULT_BUFFER_TTL_HOURS).also {
        if (it <= 0L) {
          plugin.logger.warning(
            "[Config] ai.persistent-buffer.ttl-hours=$it is invalid, using $DEFAULT_BUFFER_TTL_HOURS"
          )
        }
      }
    persistentBufferTtlMillis = ttlHours.coerceAtLeast(1L) * MILLIS_PER_HOUR
    persistentBufferCap =
      config.getDouble("ai.persistent-buffer.cap-on-restore", DEFAULT_BUFFER_CAP)
    persistentBufferDecayPerHour =
      config.getDouble("ai.persistent-buffer.decay-rate-per-hour", DEFAULT_BUFFER_DECAY)
    persistentBufferDisconnectWindowMillis =
      config.getLong(
        "ai.persistent-buffer.disconnect-window-seconds",
        DEFAULT_BUFFER_DISCONNECT_WINDOW_SECS,
      ) * MILLIS_PER_SEC
    persistentBufferSaveThreshold =
      config.getDouble("ai.persistent-buffer.save-threshold", DEFAULT_BUFFER_SAVE_THRESHOLD)

    batchEnabled = config.getBoolean("ai.batch.enabled", true)
    batchMaxSize = config.getInt("ai.batch.max-size", DEFAULT_BATCH_MAX_SIZE)
    batchMaxDelayMs = config.getLong("ai.batch.max-delay-ms", DEFAULT_BATCH_MAX_DELAY_MS)
    batchMaxPendingItems =
      config.getInt("ai.batch.max-pending-items", DEFAULT_BATCH_MAX_PENDING_ITEMS)
    outboundMaxPendingPerPlayer =
      config.getInt("ai.outbound.max-pending-per-player", DEFAULT_OUTBOUND_MAX_PENDING_PER_PLAYER)

    retryMaxAttempts = config.getInt("ai.retry.max-attempts", DEFAULT_RETRY_MAX_ATTEMPTS)
    retryInitialDelayMs =
      config.getLong("ai.retry.initial-delay-ms", DEFAULT_RETRY_INITIAL_DELAY_MS)
    retryMaxDelayMs = config.getLong("ai.retry.max-delay-ms", DEFAULT_RETRY_MAX_DELAY_MS)
    retryMultiplier = config.getDouble("ai.retry.multiplier", DEFAULT_RETRY_MULTIPLIER)
    retryJitter = config.getDouble("ai.retry.jitter", DEFAULT_RETRY_JITTER)

    val ignoredPatterns = ArrayList<Pattern>()
    for (pattern in config.getStringList("client-brand.ignored-clients")) {
      try {
        ignoredPatterns.add(Pattern.compile(pattern))
      } catch (e: PatternSyntaxException) {
        plugin.logger.warning("[ClientBrand] Invalid regex pattern in config: $pattern")
      }
    }
    ignoredClientPatterns = ignoredPatterns

    disconnectBlacklistedForge =
      config.getBoolean("client-brand.disconnect-blacklisted-forge-versions", true)

    suspiciousAlertsBuffer = config.getDouble("suspicious.alerts.buffer", 25.0)

    reportsEnabled = config.getBoolean("reports.enabled", false)
    reportAiEnabled = config.getBoolean("reports.ai-enabled", true)
    historyEnabled = config.getBoolean("history.enabled", true)
    relationsEnabled = config.getBoolean("relations-beta.enabled", false)
    relationsWorldGuardEnabled = config.getBoolean("relations-beta.sources.worldguard", false)
    relationsVaultEnabled = config.getBoolean("relations-beta.sources.vault", false)
    relationsContainersEnabled = config.getBoolean("relations-beta.sources.containers", false)
    relationsItemsEnabled = config.getBoolean("relations-beta.sources.items", false)
    relationsBanStatusEnabled = config.getBoolean("relations-beta.sources.ban-status", false)
    relationsIpAddressesEnabled = config.getBoolean("relations-beta.sources.ip-addresses", false)
    relationsBanProvider =
      config.getString("relations-beta.ban-provider", "none").lowercase().takeIf {
        it in BAN_PROVIDERS
      } ?: "none"
    reportCooldownMillis =
      config
        .getLong("reports.cooldown-seconds", DEFAULT_REPORT_COOLDOWN_SECONDS)
        .coerceIn(0L, MAX_REPORT_COOLDOWN_SECONDS) * MILLIS_PER_SEC
    reportMaxOpenPerPlayer =
      config.getInt("reports.max-open-per-player", DEFAULT_REPORT_MAX_OPEN).coerceIn(1, 20)
    reportMinReasonLength =
      config.getInt("reports.reason.min-length", DEFAULT_REPORT_MIN_REASON).coerceIn(1, 100)
    reportMaxReasonLength =
      config
        .getInt("reports.reason.max-length", DEFAULT_REPORT_MAX_REASON)
        .coerceIn(reportMinReasonLength, 500)
    reportMaxStored =
      config.getInt("reports.max-stored", DEFAULT_REPORT_MAX_STORED).coerceIn(100, 10_000)
    reportNotifyStaff = config.getBoolean("reports.notify-staff", true)

    cancelDuplicatePacket = config.getBoolean("cancel-duplicate-packet", true)
    forceCancelDuplicatePacket = config.getBoolean("force-cancel-duplicate-packet", false)

    debugEnabled = config.getBoolean("debug.enabled", false)
    val enabledCategories = EnumSet.noneOf(DebugCategory::class.java)
    for (category in DebugCategory.values()) {
      if (config.getBoolean("debug.categories.${category.configKey}", false)) {
        enabledCategories.add(category)
      }
    }
    enabledDebugCategories = enabledCategories
  }

  private fun loadDisabledRegions(): Map<String, List<String>> {
    val mapRegions = config.getStringListMap("ai.worldguard.disabled-regions")
    if (mapRegions.isNotEmpty()) {
      return mapRegions
        .mapKeys { it.key.lowercase() }
        .mapValues { entry -> entry.value.map { it.lowercase() } }
    }

    return parseLegacyDisabledRegions()
  }

  private fun parseLegacyDisabledRegions(): Map<String, List<String>> {
    val legacyList = config.getStringList("ai.worldguard.disabled-regions")
    if (legacyList.isEmpty()) return emptyMap()

    plugin.logger.warning(
      "[Config] ai.worldguard.disabled-regions uses deprecated " +
        "region:world format. Please migrate to the new map format."
    )
    val result = mutableMapOf<String, MutableList<String>>()
    for (entry in legacyList) {
      val lower = entry.lowercase()
      if (lower.contains(":")) {
        val parts = lower.split(":", limit = 2)
        val regionName = parts[0]
        val worldName = parts[1]
        result.getOrPut(worldName) { mutableListOf() }.add(regionName)
      } else {
        result.getOrPut("*") { mutableListOf() }.add(lower)
      }
    }
    return result
  }

  fun isClientIgnored(brand: String): Boolean {
    for (pattern in ignoredClientPatterns) {
      if (pattern.matcher(brand).find()) {
        return true
      }
    }
    return false
  }

  private companion object {
    val BAN_PROVIDERS = setOf("none", "vanilla", "litebans")
    const val STREAM_WINDOW = 40
    const val DEFAULT_PANEL_URL = "http://127.0.0.1:8000"
    const val LEGACY_PANEL_URL = "https://panel.kaelus.dev"

    const val MILLIS_PER_SEC = 1000L
    const val MILLIS_PER_HOUR = 3_600_000L
    const val DEFAULT_BUFFER_TTL_HOURS = 48L
    const val DEFAULT_BUFFER_CAP = 40.0
    const val DEFAULT_BUFFER_DECAY = 2.0
    const val DEFAULT_BUFFER_DISCONNECT_WINDOW_SECS = 300L
    const val DEFAULT_BUFFER_SAVE_THRESHOLD = 1.0

    const val DEFAULT_BATCH_MAX_SIZE = 32
    const val DEFAULT_BATCH_MAX_DELAY_MS = 50L
    const val DEFAULT_BATCH_MAX_PENDING_ITEMS = 128
    const val DEFAULT_OUTBOUND_MAX_PENDING_PER_PLAYER = 4

    const val DEFAULT_RETRY_MAX_ATTEMPTS = 3
    const val DEFAULT_RETRY_INITIAL_DELAY_MS = 500L
    const val DEFAULT_RETRY_MAX_DELAY_MS = 5000L
    const val DEFAULT_RETRY_MULTIPLIER = 2.0
    const val DEFAULT_RETRY_JITTER = 0.25

    const val DEFAULT_REPORT_COOLDOWN_SECONDS = 60L
    const val MAX_REPORT_COOLDOWN_SECONDS = 3600L
    const val DEFAULT_REPORT_MAX_OPEN = 3
    const val DEFAULT_REPORT_MIN_REASON = 4
    const val DEFAULT_REPORT_MAX_REASON = 160
    const val DEFAULT_REPORT_MAX_STORED = 1000
  }
}
