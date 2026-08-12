package trqxyz.spectra.command.commands.info

import java.util.Locale
import org.bukkit.OfflinePlayer
import org.incendo.cloud.CommandManager
import org.incendo.cloud.bukkit.parser.OfflinePlayerParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.description.Description
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.parser.standard.IntegerParser
import trqxyz.spectra.ai.AiDecisionHistory
import trqxyz.spectra.command.SpectraCommand
import trqxyz.spectra.config.LocaleManager
import trqxyz.spectra.sender.Sender
import trqxyz.spectra.utils.Message
import trqxyz.spectra.utils.MessageUtil
import trqxyz.spectra.utils.TimeUtil

class DecisionsCommand(
  private val history: AiDecisionHistory,
  private val localeManager: LocaleManager,
) : SpectraCommand {
  override fun register(manager: CommandManager<Sender>) {
    manager.buildAndRegister("spectra") {
      literal("decisions", Description.empty(), "decision")
        .permission("spectra.decisions")
        .required("target", OfflinePlayerParser.offlinePlayerParser())
        .optional("page", IntegerParser.integerParser(1))
        .handler(this@DecisionsCommand::show)
    }
  }

  private fun show(context: CommandContext<Sender>) {
    val sender = context.sender()
    val target: OfflinePlayer = context["target"]
    val requestedPage: Int = context.getOrDefault("page", 1)
    val page = history.page(target.uniqueId, requestedPage, PAGE_SIZE)
    sender.sendMessage(
      MessageUtil.getMessage(
        Message.DECISIONS_HEADER,
        "player",
        target.name ?: target.uniqueId.toString(),
        "page",
        page.page.toString(),
        "max_pages",
        page.maxPages.toString(),
      )
    )
    if (page.entries.isEmpty()) {
      MessageUtil.sendMessage(sender.nativeSender, Message.DECISIONS_EMPTY)
      return
    }
    for (entry in page.entries) {
      sender.sendMessage(
        MessageUtil.getMessage(
          Message.DECISIONS_ENTRY,
          "model",
          entry.model.uppercase(Locale.ROOT),
          "probability",
          String.format(Locale.ROOT, "%.2f", entry.probability * 100.0),
          "verdict",
          entry.verdict.uppercase(Locale.ROOT),
          "status",
          entry.status.uppercase(Locale.ROOT),
          "accepted",
          entry.accepted.toString(),
          "actionable",
          entry.actionable.toString(),
          "confidence",
          String.format(Locale.ROOT, "%.2f", entry.confidence),
          "novelty",
          String.format(Locale.ROOT, "%.2f", entry.novelty),
          "buffer",
          String.format(Locale.ROOT, "%.2f", entry.buffer),
          "window",
          entry.windowTicks.toString(),
          "version",
          entry.modelVersion,
          "timeago",
          TimeUtil.formatTimeAgo(entry.createdAt, localeManager),
        )
      )
    }
  }

  private companion object {
    const val PAGE_SIZE = 6
  }
}
