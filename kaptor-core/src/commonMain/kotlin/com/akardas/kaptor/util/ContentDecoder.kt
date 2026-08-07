package com.akardas.kaptor.util

/** Result of decoding a (possibly compressed) response body into text. */
data class DecodedBody(
    val text: String?,
    val isPlainText: Boolean,
    /** Human note for the UI, e.g. "decoded from gzip" or an unsupported-encoding message. */
    val note: String?,
)

/**
 * Decodes a raw response body according to its `Content-Encoding`, transparently handling
 * `gzip`, `deflate` and `br` (where a decoder is available), including stacked encodings such as
 * `deflate, gzip`. Falls back gracefully to a note when an encoding can't be decoded.
 */
object ContentDecoder {

    fun decode(contentEncoding: String?, raw: ByteArray): DecodedBody {
        val encodings = contentEncoding
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() && it != "identity" }
            ?: emptyList()

        if (encodings.isEmpty()) {
            return DecodedBody(raw.decodeToString(), isPlainText = true, note = null)
        }

        // Content-Encoding lists apply outermost-last, so decode in reverse.
        var bytes = raw
        for (encoding in encodings.asReversed()) {
            val decoded = Decompressor.decode(encoding, bytes)
                ?: return DecodedBody(
                    text = null,
                    isPlainText = false,
                    note = "Body is $contentEncoding-encoded; no decoder available on this platform.",
                )
            bytes = decoded
        }
        return DecodedBody(bytes.decodeToString(), isPlainText = true, note = "decoded from $contentEncoding")
    }
}
