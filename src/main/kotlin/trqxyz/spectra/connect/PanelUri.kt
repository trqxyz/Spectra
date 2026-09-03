package trqxyz.spectra.connect

import java.net.URI

internal fun panelUri(configuredUrl: String): URI {
  val uri = URI.create(configuredUrl.trim())
  val scheme = uri.scheme?.lowercase()
  require(uri.host != null && uri.userInfo == null) {
    "Panel URL must contain a valid host and must not contain user credentials."
  }
  require(scheme == "https" || (scheme == "http" && isLoopbackHost(uri.host))) {
    "Panel URL must use HTTPS unless it targets localhost."
  }
  return uri
}

private fun isLoopbackHost(host: String): Boolean {
  val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
  return normalized == "localhost" || normalized == "::1" || normalized.startsWith("127.")
}
