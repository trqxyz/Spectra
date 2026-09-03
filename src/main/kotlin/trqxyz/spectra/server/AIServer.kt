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
package trqxyz.spectra.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import java.util.concurrent.CompletableFuture
import trqxyz.spectra.SpectraPlugin
import trqxyz.spectra.ai.AiBatchTransport
import trqxyz.spectra.ai.AiRequestContext
import trqxyz.spectra.ai.AiTransport

private const val TRANSPORT_TIMEOUT_STATUS = -1
private const val TRANSPORT_NETWORK_ERROR_STATUS = -2
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_FORBIDDEN = 403

class AIServer(
  private val plugin: SpectraPlugin,
  url: String,
  private val apiKey: String,
  private val apiCooldown: ApiCooldown,
) : AiTransport, AiBatchTransport {
  private val serverUri: URI = validateServerUri(url)

  override fun send(payload: ByteArray, context: AiRequestContext?): CompletableFuture<String> {
    if (apiCooldown.isWaiting()) {
      return CompletableFuture.failedFuture(
        RequestException(ResponseCode.WAITING, "Server is in backoff.")
      )
    }

    return sendRequest(payload, batch = false, context = context)
  }

  override fun sendBatch(items: List<ByteArray>): CompletableFuture<String> {
    val rejection =
      when {
        apiCooldown.isWaiting() -> RequestException(ResponseCode.WAITING, "Server is in backoff.")
        items.isEmpty() -> RequestException(ResponseCode.BAD_REQUEST, "Empty batch")
        items.size > BATCH_MAX_ITEMS ->
          RequestException(
            ResponseCode.INVALID_SEQUENCE,
            "Batch count ${items.size} exceeds wire-format max $BATCH_MAX_ITEMS",
          )
        items.any { it.isEmpty() } ->
          RequestException(ResponseCode.INVALID_SEQUENCE, "Batch items must not be empty")
        items.any { it.size > BATCH_MAX_ITEM_BYTES } ->
          RequestException(
            ResponseCode.PAYLOAD_TOO_LARGE,
            "Batch item exceeds $BATCH_MAX_ITEM_BYTES bytes",
          )
        batchWireSize(items) > MAX_INFERENCE_BODY_BYTES ->
          RequestException(
            ResponseCode.PAYLOAD_TOO_LARGE,
            "Batch payload exceeds $MAX_INFERENCE_BODY_BYTES bytes",
          )
        else -> null
      }
    if (rejection != null) return CompletableFuture.failedFuture(rejection)
    return sendRequest(encodeBatchFraming(items), batch = true)
  }

  private fun sendRequest(
    body: ByteArray,
    batch: Boolean,
    context: AiRequestContext? = null,
  ): CompletableFuture<String> {
    return HTTP_CLIENT.sendAsync(
        buildRequest(body, batch, context),
        HttpResponse.BodyHandlers.ofString(),
      )
      .thenApply { response -> catchResponse(response) }
      .exceptionallyCompose { throwable -> catchException(throwable) }
  }

  internal fun buildRequest(
    body: ByteArray,
    batch: Boolean,
    context: AiRequestContext? = null,
  ): HttpRequest {
    val builder =
      HttpRequest.newBuilder(serverUri)
        .header("Content-Type", "application/octet-stream")
        .header("User-Agent", "Spectra/" + plugin.pluginMeta.version)
        .header("Authorization", "Bearer $apiKey")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        .timeout(if (batch) BATCH_REQUEST_TIMEOUT else REQUEST_TIMEOUT)
    if (batch) {
      builder.header("X-Batch", "1")
    }
    context?.playerName?.let { builder.header("X-Player-Name", it) }
    context?.models?.takeIf { it.isNotBlank() }?.let { builder.header("X-Models", it) }
    context?.playerUuid?.takeIf { it.isNotBlank() }?.let { builder.header("X-Player-UUID", it) }
    context?.streamId?.takeIf { it.isNotBlank() }?.let { builder.header("X-Stream-ID", it) }
    context?.chunkSequence?.let { builder.header("X-Chunk-Sequence", it.toString()) }

    return builder.build()
  }

  private fun encodeBatchFraming(items: List<ByteArray>): ByteArray {
    val totalSize = batchWireSize(items).toInt()
    val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(items.size.toShort())
    for (item in items) {
      buf.putInt(item.size)
      buf.put(item)
    }
    return buf.array()
  }

  private fun batchWireSize(items: List<ByteArray>): Long =
    BATCH_COUNT_SIZE.toLong() + items.sumOf { BATCH_ITEM_HEADER_SIZE.toLong() + it.size }

  private fun catchResponse(response: HttpResponse<String>): String {
    val statusCode = response.statusCode()
    if (statusCode >= 300 || statusCode < 200) {
      if (statusCode >= 500 || statusCode == 403) {
        apiCooldown.recordFailure()
      }

      throw RequestException(
        ResponseCode.fromStatusCode(statusCode),
        "HTTP Status $statusCode: ${response.body()}",
        responseBody = response.body(),
      )
    }

    apiCooldown.recordSuccess()
    return response.body()
  }

  private fun <U> catchException(throwable: Throwable): CompletableFuture<U> {
    val cause =
      if (throwable is java.util.concurrent.CompletionException && throwable.cause != null) {
        throwable.cause!!
      } else {
        throwable
      }
    if (cause is RequestException) {
      return CompletableFuture.failedFuture(cause)
    }

    if (cause !is HttpTimeoutException) {
      apiCooldown.recordFailure()
    }

    val code =
      if (cause is HttpTimeoutException) ResponseCode.TIMEOUT else ResponseCode.NETWORK_ERROR

    return CompletableFuture.failedFuture(
      RequestException(code, "Request failed: " + cause.message, cause)
    )
  }

  enum class ResponseCode(val httpCode: Int) {
    SUCCESS(200),
    BAD_REQUEST(400),
    UNAUTHORIZED(HTTP_STATUS_UNAUTHORIZED),
    FORBIDDEN(HTTP_STATUS_FORBIDDEN),
    NOT_FOUND(404),
    PAYLOAD_TOO_LARGE(413),
    CHUNK_SEQUENCE_MISMATCH(409),
    INVALID_SEQUENCE(422),
    RATE_LIMITED(429),
    SERVER_ERROR(500),
    SERVICE_UNAVAILABLE(503),
    TIMEOUT(TRANSPORT_TIMEOUT_STATUS),
    NETWORK_ERROR(TRANSPORT_NETWORK_ERROR_STATUS),
    PARSE_ERROR(-3),
    WAITING(-5),
    UNKNOWN_ERROR(-4);

    companion object {
      @JvmStatic
      fun fromStatusCode(code: Int): ResponseCode {
        for (value in entries) if (value.httpCode == code) return value
        return if (code >= 500) SERVER_ERROR else if (code >= 400) BAD_REQUEST else UNKNOWN_ERROR
      }
    }
  }

  class RequestException : RuntimeException {
    val code: ResponseCode
    val responseBody: String?

    constructor(
      code: ResponseCode,
      message: String,
      responseBody: String? = null,
    ) : super(message) {
      this.code = code
      this.responseBody = responseBody
    }

    constructor(code: ResponseCode, message: String, cause: Throwable) : super(message, cause) {
      this.code = code
      this.responseBody = null
    }
  }

  companion object {
    private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
    private val REQUEST_TIMEOUT = Duration.ofSeconds(5)
    private val BATCH_REQUEST_TIMEOUT = Duration.ofSeconds(10)

    private const val BATCH_COUNT_SIZE = 2
    private const val BATCH_ITEM_HEADER_SIZE = 4
    private const val BATCH_MAX_ITEM_BYTES = 2 * 1024 * 1024
    private const val MAX_INFERENCE_BODY_BYTES = 2 * 1024 * 1024
    const val BATCH_MAX_ITEMS = 256

    internal fun validateServerUri(url: String): URI {
      val uri = URI.create(url)
      val scheme = uri.scheme?.lowercase()
      require(uri.host != null && uri.userInfo == null) {
        "AI server URL must contain a valid host and must not contain user credentials."
      }
      require(scheme == "https" || (scheme == "http" && isLoopbackHost(uri.host))) {
        "AI server URL must use HTTPS unless it targets localhost."
      }
      return uri
    }

    private fun isLoopbackHost(host: String): Boolean {
      val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
      return normalized == "localhost" || normalized == "::1" || normalized.startsWith("127.")
    }

    private val HTTP_CLIENT: HttpClient =
      HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    fun shutdownHttpClient() {
      runCatching { (HTTP_CLIENT as? AutoCloseable)?.close() }
    }
  }
}
