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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package trqxyz.spectra.api

import java.util.Optional
import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider

/** Service loader for [SpectraApi] via Bukkit ServicesManager. */
object SpectraApiProvider {
  /**
   * Returns the Spectra API instance if SpectraPlugin is present and registered.
   *
   * @return optional SpectraApi
   */
  @JvmStatic
  fun get(): Optional<SpectraApi> {
    val provider: RegisteredServiceProvider<SpectraApi>? =
      Bukkit.getServicesManager().getRegistration(SpectraApi::class.java)
    return if (provider == null) {
      Optional.empty()
    } else {
      Optional.ofNullable(provider.provider)
    }
  }
}
