package com.akardas.kaptor.plugin

import com.akardas.kaptor.model.HttpHeader
import com.akardas.kaptor.store.RequestSnapshot
import com.akardas.kaptor.store.ResponseSnapshot
import com.akardas.kaptor.store.TransactionRepository
import com.akardas.kaptor.util.ContentDecoder
import com.akardas.kaptor.util.currentEpochMillis
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Headers
import io.ktor.http.content.OutgoingContent

/** Configuration for the [Kaptor] plugin. */
class KaptorConfig {
    /** Where captured transactions are stored. Must be set before installing the plugin. */
    var repository: TransactionRepository? = null

    /**
     * Bodies larger than this (bytes) are not captured, to avoid holding large downloads in
     * memory. Non-textual bodies are never captured regardless of size.
     */
    var maxContentLength: Long = 250_000L

    /** Optional filter; return `false` to skip capturing a given request entirely. */
    var filter: ((HttpRequestBuilder) -> Boolean)? = null
}

/**
 * A Ktor client plugin that records every request/response into a [TransactionRepository],
 * the Ktor-native analogue of Chucker's OkHttp interceptor.
 *
 * Install it on your client:
 * ```
 * val repository = TransactionRepository(databaseDriverFactory)
 * val client = HttpClient(engine) {
 *     install(Kaptor) { repository = repository }
 * }
 * ```
 */
val Kaptor = createClientPlugin("Kaptor", ::KaptorConfig) {
    val repository = requireNotNull(pluginConfig.repository) {
        "Kaptor requires a repository. Set `repository = ...` when installing the plugin."
    }
    val maxContentLength = pluginConfig.maxContentLength
    val filter = pluginConfig.filter

    on(Send) { request ->
        if (filter != null && !filter(request)) {
            return@on proceed(request)
        }

        val requestBody = (request.body as? OutgoingContent)
        val (reqBodyText, reqIsText) = requestBody.captureBody(maxContentLength)

        val url = request.url.build()
        val transactionId = repository.create(
            RequestSnapshot(
                requestDate = currentEpochMillis(),
                method = request.method.value,
                url = url.toString(),
                host = url.host,
                path = url.encodedPath,
                scheme = url.protocol.name,
                protocol = null,
                contentType = requestBody?.contentType?.toString(),
                contentLength = requestBody?.contentLength,
                headers = mergeHeaders(request.headers.build(), requestBody),
                body = reqBodyText,
                bodyIsPlainText = reqIsText,
            ),
        )

        val startedAt = currentEpochMillis()
        val call: HttpClientCall = try {
            proceed(request)
        } catch (cause: Throwable) {
            repository.fail(
                id = transactionId,
                error = cause.messageOrClassName(),
                tookMs = currentEpochMillis() - startedAt,
            )
            throw cause
        }

        recordResponse(repository, transactionId, startedAt, call, maxContentLength)
    }
}

/**
 * Reads the response body (when textual and within the size cap) and writes the response
 * snapshot. Returns a call whose body is safe for the application to read again.
 */
private suspend fun recordResponse(
    repository: TransactionRepository,
    transactionId: Long,
    startedAt: Long,
    call: HttpClientCall,
    maxContentLength: Long,
): HttpClientCall {
    val response = call.response
    val contentType = response.headers[io.ktor.http.HttpHeaders.ContentType]
    val contentEncoding = response.headers[io.ktor.http.HttpHeaders.ContentEncoding]
    val contentLength = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull()
    val textual = isTextual(contentType)
    val withinCap = contentLength == null || contentLength <= maxContentLength

    // `save()` buffers the body in memory so both the inspector and the caller can read it. We
    // read the RAW bytes (before Ktor's own ContentEncoding decoding, if any) and decode them
    // ourselves, so compressed bodies are captured correctly regardless of client config.
    val (returnedCall, body, bodyIsText) = if (textual && withinCap) {
        val saved = call.save()
        val raw = runCatching { saved.response.readRawBytes() }.getOrNull()
        val decoded = raw?.let { ContentDecoder.decode(contentEncoding, it) }
        Triple(saved, decoded?.text, decoded?.isPlainText ?: false)
    } else {
        Triple(call, null, false)
    }

    repository.update(
        id = transactionId,
        response = ResponseSnapshot(
            responseDate = currentEpochMillis(),
            tookMs = currentEpochMillis() - startedAt,
            protocol = response.version.toString(),
            code = response.status.value,
            message = response.status.description,
            contentType = contentType,
            contentLength = contentLength,
            headers = response.headers.toHttpHeaders(),
            body = body,
            bodyIsPlainText = bodyIsText,
        ),
    )
    return returnedCall
}

private suspend fun OutgoingContent?.captureBody(maxContentLength: Long): Pair<String?, Boolean> {
    if (this == null) return null to true
    val textual = isTextual(contentType?.toString())
    val length = contentLength
    if (!textual || (length != null && length > maxContentLength)) return null to false
    return when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString() to true
        is OutgoingContent.NoContent -> null to true
        // Streaming bodies can't be read without consuming the source, so we skip them.
        else -> null to false
    }
}

private fun mergeHeaders(headers: Headers, content: OutgoingContent?): List<HttpHeader> {
    val merged = headers.toHttpHeaders().toMutableList()
    content?.contentType?.let { merged += HttpHeader(io.ktor.http.HttpHeaders.ContentType, it.toString()) }
    content?.contentLength?.let { merged += HttpHeader(io.ktor.http.HttpHeaders.ContentLength, it.toString()) }
    return merged
}

private fun Headers.toHttpHeaders(): List<HttpHeader> =
    entries().flatMap { (name, values) -> values.map { HttpHeader(name, it) } }

private fun isTextual(contentType: String?): Boolean {
    val ct = contentType?.lowercase() ?: return false
    return ct.startsWith("text/") ||
        ct.contains("json") ||
        ct.contains("xml") ||
        ct.contains("x-www-form-urlencoded") ||
        ct.contains("javascript") ||
        ct.contains("html")
}

private fun Throwable.messageOrClassName(): String =
    message ?: this::class.simpleName ?: "Unknown error"
