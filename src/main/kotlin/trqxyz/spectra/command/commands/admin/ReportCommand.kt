package trqxyz.spectra.command.commands.admin

import org.bukkit.entity.Player
import org.incendo.cloud.CommandManager
import org.incendo.cloud.bukkit.parser.PlayerParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.description.Description
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.parser.standard.IntegerParser
import org.incendo.cloud.parser.standard.StringParser
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.config.ConfigManager
import trqxyz.spectra.config.LocaleManager
import trqxyz.spectra.report.CreateReportResult
import trqxyz.spectra.report.ReportMenu
import trqxyz.spectra.report.ReportMutationResult
import trqxyz.spectra.report.ReportService
import trqxyz.spectra.scheduler.SchedulerService
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil
import trqxyz.spectra.utils.TimeUtil

class ReportCommand(
  private val plugin: SpectraPlugin,
  private val reportService: ReportService,
  private val reportMenu: ReportMenu,
  private val configManager: ConfigManager,
  private val localeManager: LocaleManager,
  private val scheduler: SchedulerService,
) : SpectraCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.buildAndRegister("spectra") {
      literal("report", Description.empty())
        .permission("spectra.report")
        .required("target", PlayerParser.playerParser())
        .required("reason", StringParser.greedyStringParser())
        .handler(this@ReportCommand::create)
    }
    manager.buildAndRegister("spectra") {
      literal("reports", Description.empty())
        .permission("spectra.reports")
        .optional("page", IntegerParser.integerParser(1))
        .handler(this@ReportCommand::list)
    }
    manager.buildAndRegister("spectra") {
      literal("reports", Description.empty())
        .literal("claim")
        .permission("spectra.reports")
        .required("id", IntegerParser.integerParser(1))
        .handler(this@ReportCommand::claim)
    }
    manager.buildAndRegister("spectra") {
      literal("reports", Description.empty())
        .literal("close")
        .permission("spectra.reports")
        .required("id", IntegerParser.integerParser(1))
        .handler(this@ReportCommand::close)
    }
  }

  private fun create(context: CommandContext<Sender>) {
    val sender = context.sender().nativeSender
    if (!configManager.reportsEnabled) {
      MessageUtil.sendMessage(sender, Message.REPORTS_DISABLED)
      return
    }
    val reporter = sender as? Player
    if (reporter == null) {
      MessageUtil.sendMessage(sender, Message.RUN_AS_PLAYER)
      return
    }
    val target: Player = context["target"]
    if (reporter.uniqueId == target.uniqueId) {
      MessageUtil.sendMessage(sender, Message.REPORTS_SELF)
      return
    }
    val reason = context.get<String>("reason").trim().replace(Regex("\\s+"), " ")
    if (reason.length < configManager.reportMinReasonLength) {
      MessageUtil.sendMessage(
        sender,
        Message.REPORTS_REASON_SHORT,
        "min",
        configManager.reportMinReasonLength.toString(),
      )
      return
    }
    if (reason.length > configManager.reportMaxReasonLength) {
      MessageUtil.sendMessage(
        sender,
        Message.REPORTS_REASON_LONG,
        "max",
        configManager.reportMaxReasonLength.toString(),
      )
      return
    }

    val reporterId = reporter.uniqueId
    val reporterName = reporter.name
    val targetId = target.uniqueId
    val targetName = target.name
    scheduler.runAsync {
      val result = reportService.create(reporterId, reporterName, targetId, targetName, reason)
      scheduler.runSync(reporter) {
        when (result) {
          is CreateReportResult.Created -> {
            MessageUtil.sendMessage(
              reporter,
              Message.REPORTS_SUBMITTED,
              "id",
              result.report.id.toString(),
              "player",
              result.report.targetName,
            )
            notifyStaff(result.report.id, reporterName, targetName, reason)
          }
          is CreateReportResult.Cooldown ->
            MessageUtil.sendMessage(
              reporter,
              Message.REPORTS_COOLDOWN,
              "seconds",
              result.remainingSeconds.toString(),
            )
          CreateReportResult.LimitReached ->
            MessageUtil.sendMessage(reporter, Message.REPORTS_LIMIT)
        }
      }
    }
  }

  private fun list(context: CommandContext<Sender>) {
    val sender = context.sender()
    if (!configManager.reportsEnabled) {
      MessageUtil.sendMessage(sender.nativeSender, Message.REPORTS_DISABLED)
      return
    }
    val requestedPage: Int = context.getOrDefault("page", 1)
    val player = sender.nativeSender as? Player
    if (player != null) {
      reportMenu.open(player, requestedPage)
      return
    }
    scheduler.runAsync {
      val page = reportService.page(requestedPage, PAGE_SIZE)
      val entries =
        page.entries.map {
          MessageUtil.getMessage(
            Message.REPORTS_ENTRY,
            "id",
            it.id.toString(),
            "status",
            it.status.name,
            "reporter",
            it.reporterName,
            "player",
            it.targetName,
            "reason",
            it.reason,
            "timeago",
            TimeUtil.formatTimeAgo(it.createdAt, localeManager),
          )
        }
      scheduler.runSync {
        sender.sendMessage(
          MessageUtil.getMessage(
            Message.REPORTS_HEADER,
            "page",
            page.page.toString(),
            "max_pages",
            page.maxPages.toString(),
          )
        )
        if (entries.isEmpty()) {
          MessageUtil.sendMessage(sender.nativeSender, Message.REPORTS_EMPTY)
        } else {
          entries.forEach(sender::sendMessage)
        }
      }
    }
  }

  private fun claim(context: CommandContext<Sender>) {
    mutate(context, true)
  }

  private fun close(context: CommandContext<Sender>) {
    mutate(context, false)
  }

  private fun mutate(context: CommandContext<Sender>, claim: Boolean) {
    val sender = context.sender().nativeSender
    if (!configManager.reportsEnabled) {
      MessageUtil.sendMessage(sender, Message.REPORTS_DISABLED)
      return
    }
    val id = context.get<Int>("id").toLong()
    val staffName = sender.name
    scheduler.runAsync {
      val result =
        if (claim) reportService.claim(id, staffName) else reportService.close(id, staffName)
      scheduler.runSync {
        when (result) {
          ReportMutationResult.UPDATED ->
            MessageUtil.sendMessage(
              sender,
              if (claim) Message.REPORTS_CLAIMED else Message.REPORTS_CLOSED,
              "id",
              id.toString(),
            )
          ReportMutationResult.NOT_FOUND ->
            MessageUtil.sendMessage(sender, Message.REPORTS_NOT_FOUND, "id", id.toString())
          ReportMutationResult.ALREADY_CLOSED ->
            MessageUtil.sendMessage(sender, Message.REPORTS_ALREADY_CLOSED, "id", id.toString())
        }
      }
    }
  }

  private fun notifyStaff(id: Long, reporter: String, target: String, reason: String) {
    if (!configManager.reportNotifyStaff) return
    val recipients =
      plugin.server.onlinePlayers.filter { it.hasPermission("spectra.reports") }.toMutableList()
    val message =
      MessageUtil.getMessage(
        Message.REPORTS_NOTIFICATION,
        "id",
        id.toString(),
        "reporter",
        reporter,
        "player",
        target,
        "reason",
        reason,
      )
    recipients.forEach { MessageUtil.sendMessage(it, message) }
    MessageUtil.sendMessage(plugin.server.consoleSender, message)
  }

  private companion object {
    const val PAGE_SIZE = 8
  }
}
