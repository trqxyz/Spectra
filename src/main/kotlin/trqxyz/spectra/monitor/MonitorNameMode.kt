/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 KaelusAI
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
 * along with SpectraPlugin.  If not, see <https://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.monitor

import java.util.Locale

enum class MonitorNameMode {
  AUTO,
  ALWAYS,
  NEVER;

  companion object {
    @JvmStatic
    fun fromConfig(value: String?): MonitorNameMode {
      if (value == null) {
        return AUTO
      }
      return try {
        valueOf(value.trim().uppercase(Locale.ROOT))
      } catch (ex: IllegalArgumentException) {
        AUTO
      }
    }
  }
}
