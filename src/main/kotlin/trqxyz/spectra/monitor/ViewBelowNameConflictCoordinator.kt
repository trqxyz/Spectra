/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
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
package trqxyz.spectra.monitor

import java.util.UUID
import org.bukkit.entity.Player
import trqxyz.spectra.SpectraPlugin

internal class ViewBelowNameConflictCoordinator(
  private val plugin: SpectraPlugin,
  private val tracker: ViewTargetTracker,
  private val belowNameBridge: ViewBelowNamePacketBridge,
  private val sessionProvider: (UUID) -> ViewSession?,
) {
  fun reassertDisplay(
    viewerId: UUID,
    conflictingObjective: String,
    viewerResolver: (UUID) -> Player?,
  ) {
    val session = sessionProvider(viewerId)?.takeIf(ViewSession::usesBelowName)
    val viewer = viewerResolver(viewerId)
    val objectiveName = session?.belowObjectiveName
    if (session != null && viewer != null && objectiveName != null) {
      logFirstConflict(session, viewer, conflictingObjective)
      belowNameBridge.displayObjective(viewer, objectiveName)
      tracker.refreshTrackedTargets(viewer, session)
    }
  }

  fun recreateObjective(viewerId: UUID, objectiveName: String, viewerResolver: (UUID) -> Player?) {
    val session = sessionProvider(viewerId)
    val viewer = viewerResolver(viewerId)
    val shouldRecreate =
      session != null && session.usesBelowName() && session.belowObjectiveName == objectiveName

    if (shouldRecreate && viewer != null) {
      session.targetTeams.values.forEach(TargetTeamState::invalidateBelowName)
      belowNameBridge.createObjective(
        viewer,
        objectiveName,
        session.config.belowTitle,
        session.config.defaultBelowText,
      )
      tracker.refreshTrackedTargets(viewer, session)
    }
  }

  private fun logFirstConflict(session: ViewSession, viewer: Player, conflictingObjective: String) {
    if (session.belowNameConflictLogged) {
      return
    }

    plugin.logger.warning(
      "[View] Viewer ${viewer.name} reasserted Spectra below-name display after " +
        "'$conflictingObjective' attempted to claim the slot."
    )
    session.belowNameConflictLogged = true
  }
}
