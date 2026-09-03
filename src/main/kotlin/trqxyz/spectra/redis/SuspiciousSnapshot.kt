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
package trqxyz.spectra.redis

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class SuspiciousSnapshot
@JsonCreator
constructor(
  @param:JsonProperty("server") val server: String,
  @param:JsonProperty("uuid") val uuid: String,
  @param:JsonProperty("name") val name: String,
  @param:JsonProperty("buffer") val buffer: Double,
  @param:JsonProperty("ping") val ping: Int,
  @param:JsonProperty("updatedAt") val updatedAt: Long,
)
