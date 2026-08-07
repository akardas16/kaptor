package com.akardas.kaptor.model

/**
 * An immutable snapshot of a single HTTP request/response captured by the inspector.
 *
 * Mirrors the shape of Chucker's `HttpTransaction`, adapted for Kotlin Multiplatform.
 */
data class HttpTransaction(
    val id: Long,
    val status: TransactionStatus,
    val requestDate: Long?,
    val responseDate: Long?,
    val tookMs: Long?,
    val protocol: String?,
    val method: String?,
    val url: String?,
    val host: String?,
    val path: String?,
    val scheme: String?,
    val requestContentType: String?,
    val requestContentLength: Long?,
    val requestHeaders: List<HttpHeader>,
    val requestBody: String?,
    val requestBodyIsPlainText: Boolean,
    val responseCode: Int?,
    val responseMessage: String?,
    val responseContentType: String?,
    val responseContentLength: Long?,
    val responseHeaders: List<HttpHeader>,
    val responseBody: String?,
    val responseBodyIsPlainText: Boolean,
    val error: String?,
) {
    /** True while the response has not yet arrived. */
    val isInProgress: Boolean get() = status == TransactionStatus.Requested

    /** `GET /path` style short label for the list row. */
    val shortLabel: String get() = "${method.orEmpty()} ${path.orEmpty()}".trim()

    /** Human-facing status code label, e.g. `200`, `!!!` for errors, `...` while pending. */
    val statusLabel: String
        get() = when (status) {
            TransactionStatus.Failed -> "!!!"
            TransactionStatus.Requested -> "..."
            TransactionStatus.Complete -> responseCode?.toString() ?: "?"
        }
}
