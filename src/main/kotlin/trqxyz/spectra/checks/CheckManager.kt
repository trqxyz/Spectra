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
package trqxyz.spectra.checks

import com.github.retrooper.packetevents.event.PacketReceiveEvent
import trqxyz.spectra.checks.type.PacketCheck
import trqxyz.spectra.checks.type.RotationCheck
import trqxyz.spectra.player.SpectraPlayer
import trqxyz.spectra.utils.update.RotationUpdate

class CheckManager(private val player: SpectraPlayer, checkFactories: Set<CheckFactory>) {
  private val rotationChecks = ArrayList<RotationCheck>()
  private val packetChecks = ArrayList<PacketCheck>()
  private val checks = HashMap<Class<out ICheck>, ICheck>()

  init {
    for (factory in checkFactories) {
      registerCheck(factory.create(player))
    }
  }

  private fun checksDisabled(): Boolean =
    player.exemptManager.isDisabled(player.player) || player.isBedrockExempt

  private fun registerCheck(check: ICheck) {
    checks[check.javaClass] = check

    if (check is RotationCheck) {
      rotationChecks.add(check)
    }

    if (check is PacketCheck) {
      packetChecks.add(check)
    }
  }

  fun reloadChecks() {
    for (check in checks.values) {
      if (check is Reloadable) {
        check.reload()
      }
    }
  }

  fun onRotationUpdate(update: RotationUpdate) {
    if (checksDisabled()) {
      return
    }
    for (check in rotationChecks) {
      check.process(update)
    }
  }

  fun onPacketReceive(event: PacketReceiveEvent) {
    if (checksDisabled()) {
      return
    }
    for (check in packetChecks) {
      check.onPacketReceive(event)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : ICheck> getCheck(clazz: Class<T>): T? {
    return checks[clazz] as T?
  }

  fun getAllChecks(): Collection<ICheck> = checks.values

  fun interface Factory {
    fun create(player: SpectraPlayer): CheckManager
  }
}
