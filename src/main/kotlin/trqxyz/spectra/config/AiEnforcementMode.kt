/*
 * This file is part of Spectra - https://github.com/trqxyz/ai_server
 * Copyright (C) 2026 SpectraAI
 *
 * Spectra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package trqxyz.spectra.config

enum class AiEnforcementMode {
  SHADOW,
  ENFORCE;

  companion object {
    fun parse(value: String?): AiEnforcementMode? =
      entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
  }
}
