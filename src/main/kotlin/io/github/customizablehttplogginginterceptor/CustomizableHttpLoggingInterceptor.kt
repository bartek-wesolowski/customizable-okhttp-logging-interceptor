package io.github.customizablehttplogginginterceptor

import io.github.customizablehttplogginginterceptor.internal.isProbablyUtf8
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okhttp3.internal.platform.Platform
import okio.Buffer
import okio.GzipSource
import kotlin.math.min

/**
 * An OkHttp interceptor which logs request and response information. Can be applied as an
 * [application interceptor][OkHttpClient.interceptors] or as a [OkHttpClient.networkInterceptors].
 *
 * The format of the logs created by this class should not be considered stable and may
 * change slightly between releases. If you need a stable logging format, use your own interceptor.
 *
 * @param logger the logger to use. Each platform has a default one.
 * @param logHeaders true to show headers
 * @param logBody true to show body
 * @param maxBodyLength the maximum body length to show. If body is longer than this value then it will be truncated to this
 * length. This parameter is only used if `logBody` is true.
 */
class CustomizableHttpLoggingInterceptor @JvmOverloads constructor(
  private val logger: Logger = Logger.DEFAULT,
  private val logHeaders: Boolean = false,
  private val logBody: Boolean = false,
  private val maxBodyLength: Long = Long.MAX_VALUE
) : Interceptor {

  @Volatile private var headersToRedact = emptySet<String>()

  fun interface Logger {
    fun log(message: String)

    companion object {
      /** A [Logger] defaults output appropriate for the current platform. */
      @JvmField
      val DEFAULT: Logger = DefaultLogger()
      private class DefaultLogger : Logger {
        override fun log(message: String) {
          Platform.get().log(message)
        }
      }
    }
  }

  fun redactHeader(name: String) {
    val newHeadersToRedact = TreeSet(String.CASE_INSENSITIVE_ORDER)
    newHeadersToRedact += headersToRedact
    newHeadersToRedact += name
    headersToRedact = newHeadersToRedact
  }

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()

    val requestBody = request.body

    val connection = chain.connection()
    var requestStartMessage =
        ("--> ${request.method} ${request.url}${if (connection != null) " " + connection.protocol() else ""}")
    if (!logHeaders && requestBody != null) {
      requestStartMessage += " (${requestBody.contentLength()}-byte body)"
    }
    logger.log(requestStartMessage)

    if (logHeaders) {
      val headers = request.headers

      if (requestBody != null) {
        // Request body headers are only present when installed as a network interceptor. When not
        // already present, force them to be included (if available) so their values are known.
        requestBody.contentType()?.let {
          if (headers["Content-Type"] == null) {
            logger.log("Content-Type: $it")
          }
        }
        if (requestBody.contentLength() != -1L) {
          if (headers["Content-Length"] == null) {
            logger.log("Content-Length: ${requestBody.contentLength()}")
          }
        }
      }

      for (i in 0 until headers.size) {
        logHeader(headers, i)
      }
    }

    if (!logBody || requestBody == null) {
      logger.log("--> END ${request.method}")
    } else if (bodyHasUnknownEncoding(request.headers)) {
      logger.log("--> END ${request.method} (encoded body omitted)")
    } else if (requestBody.isDuplex()) {
      logger.log("--> END ${request.method} (duplex request body omitted)")
    } else if (requestBody.isOneShot()) {
      logger.log("--> END ${request.method} (one-shot body omitted)")
    } else {
      val buffer = Buffer()
      requestBody.writeTo(buffer)

      val contentType = requestBody.contentType()
      val charset: Charset = contentType?.charset(UTF_8) ?: UTF_8

      logger.log("")
      if (buffer.isProbablyUtf8()) {
        logger.log(buffer.readString(min(buffer.size, maxBodyLength), charset))
        logger.log("--> END ${request.method} (${requestBody.contentLength()}-byte body)")
      } else {
        logger.log(
          "--> END ${request.method} (binary ${requestBody.contentLength()}-byte body omitted)"
        )
      }
    }

    val startNs = System.nanoTime()
    val response: Response
    try {
      response = chain.proceed(request)
    } catch (e: Exception) {
      logger.log("<-- HTTP FAILED: $e")
      throw e
    }

    val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

    val responseBody = response.body!!
    val contentLength = responseBody.contentLength()
    val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"
    logger.log(
        "<-- ${response.code}${if (response.message.isEmpty()) "" else ' ' + response.message} ${response.request.url} (${tookMs}ms${if (!logHeaders) ", $bodySize body" else ""})")

    val headers = response.headers
    if (logHeaders) {
      for (i in 0 until headers.size) {
        logHeader(headers, i)
      }
    }

    if (!logBody || !response.promisesBody()) {
      logger.log("<-- END HTTP")
    } else if (bodyHasUnknownEncoding(response.headers)) {
      logger.log("<-- END HTTP (encoded body omitted)")
    } else {
      val source = responseBody.source()
      source.request(Long.MAX_VALUE) // Buffer the entire body.
      var buffer = source.buffer

      var gzippedLength: Long? = null
      if ("gzip".equals(headers["Content-Encoding"], ignoreCase = true)) {
        gzippedLength = buffer.size
        GzipSource(buffer.clone()).use { gzippedResponseBody ->
          buffer = Buffer()
          buffer.writeAll(gzippedResponseBody)
        }
      }

      val contentType = responseBody.contentType()
      val charset: Charset = contentType?.charset(UTF_8) ?: UTF_8

      if (!buffer.isProbablyUtf8()) {
        logger.log("")
        logger.log("<-- END HTTP (binary ${buffer.size}-byte body omitted)")
        return response
      }

      if (contentLength != 0L) {
        logger.log("")
        logger.log(buffer.clone().readString(min(buffer.size, maxBodyLength), charset))
      }

      if (gzippedLength != null) {
        logger.log("<-- END HTTP (${buffer.size}-byte, $gzippedLength-gzipped-byte body)")
      } else {
        logger.log("<-- END HTTP (${buffer.size}-byte body)")
      }
    }

    return response
  }

  private fun logHeader(headers: Headers, i: Int) {
    val value = if (headers.name(i) in headersToRedact) "██" else headers.value(i)
    logger.log(headers.name(i) + ": " + value)
  }

  private fun bodyHasUnknownEncoding(headers: Headers): Boolean {
    val contentEncoding = headers["Content-Encoding"] ?: return false
    return !contentEncoding.equals("identity", ignoreCase = true) &&
        !contentEncoding.equals("gzip", ignoreCase = true)
  }
}
