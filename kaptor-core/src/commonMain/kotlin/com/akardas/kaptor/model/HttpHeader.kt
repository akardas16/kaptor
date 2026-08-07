package com.akardas.kaptor.model

/** A single HTTP header name/value pair. */
data class HttpHeader(
    val name: String,
    val value: String,
) {
    companion object {
        /** Encodes headers into a single storable string (one header per line). */
        fun encode(headers: List<HttpHeader>): String =
            headers.joinToString("\n") { "${it.name}:${it.value}" }

        /** Decodes the string produced by [encode] back into headers. */
        fun decode(raw: String?): List<HttpHeader> {
            if (raw.isNullOrEmpty()) return emptyList()
            return raw.lineSequence()
                .mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx <= 0) null
                    else HttpHeader(line.substring(0, idx), line.substring(idx + 1))
                }
                .toList()
        }
    }
}
