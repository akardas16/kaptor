package com.akardas.kaptor.share

import com.akardas.kaptor.model.HttpHeader
import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.util.FormatUtils
import com.akardas.kaptor.util.formatEpochMillis

/** Renders a captured [HttpTransaction] into shareable representations. */
object TransactionFormats {

    /** A Chucker-style plain-text dump of the whole transaction. */
    fun shareText(tx: HttpTransaction): String = buildString {
        appendLine("URL: ${tx.url.orEmpty()}")
        appendLine("Method: ${tx.method.orEmpty()}")
        tx.protocol?.let { appendLine("Protocol: $it") }
        appendLine("Status: ${tx.status}")
        tx.responseCode?.let { code ->
            appendLine("Response: $code${tx.responseMessage?.let { " $it" }.orEmpty()}")
        }
        appendLine("SSL: ${if (tx.scheme.equals("https", ignoreCase = true)) "Yes" else "No"}")
        appendLine()

        tx.requestDate?.let { appendLine("Request time: ${formatEpochMillis(it)}") }
        tx.responseDate?.let { appendLine("Response time: ${formatEpochMillis(it)}") }
        tx.tookMs?.let { appendLine("Duration: $it ms") }
        appendLine()

        val requestSize = tx.requestContentLength ?: 0L
        val responseSize = tx.responseContentLength ?: 0L
        appendLine("Request size: ${FormatUtils.formatBytes(requestSize)}")
        appendLine("Response size: ${FormatUtils.formatBytes(responseSize)}")
        appendLine("Total size: ${FormatUtils.formatBytes(requestSize + responseSize)}")
        appendLine()

        appendLine("---------- Request ----------")
        appendLine()
        appendHeaders(tx.requestHeaders)
        appendLine()
        appendLine(bodyOrPlaceholder(tx.requestBody, tx.requestContentType, tx.requestBodyIsPlainText))
        appendLine()

        appendLine("---------- Response ----------")
        appendLine()
        appendHeaders(tx.responseHeaders)
        appendLine()
        append(bodyOrPlaceholder(tx.responseBody, tx.responseContentType, tx.responseBodyIsPlainText))
    }

    /** An executable `curl` command reproducing the request. */
    fun curl(tx: HttpTransaction): String = buildString {
        append("curl -X ${tx.method ?: "GET"}")
        tx.requestHeaders.forEach { header ->
            append(" \\\n  -H '${header.name}: ${header.value.escapeSingleQuotes()}'")
        }
        if (!tx.requestBody.isNullOrEmpty()) {
            append(" \\\n  --data '${tx.requestBody.escapeSingleQuotes()}'")
        }
        append(" \\\n  '${tx.url.orEmpty()}'")
    }

    private fun StringBuilder.appendHeaders(headers: List<HttpHeader>) {
        headers.forEach { appendLine("${it.name}: ${it.value}") }
    }

    private fun bodyOrPlaceholder(body: String?, contentType: String?, isPlainText: Boolean): String = when {
        body.isNullOrEmpty() -> "(body is empty)"
        !isPlainText -> "(binary body)"
        FormatUtils.isJson(contentType) -> FormatUtils.formatJson(body)
        else -> body
    }

    private fun String.escapeSingleQuotes(): String = replace("'", "'\\''")
}
