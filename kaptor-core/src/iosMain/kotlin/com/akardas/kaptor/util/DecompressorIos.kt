package com.akardas.kaptor.util

actual object Decompressor {
    actual fun decode(encoding: String, data: ByteArray): ByteArray? = try {
        when (encoding) {
            "gzip", "x-gzip" -> PureInflate.gunzip(data)
            "deflate" -> PureInflate.inflateDeflate(data)
            // Brotli has no system decoder on Apple platforms; report as unsupported.
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
