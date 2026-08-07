package com.akardas.kaptor.util

/**
 * Platform content decompressor. Returns the decompressed bytes, or `null` when the [encoding]
 * is unsupported on the current platform or decoding fails.
 *
 * `encoding` is a single, lower-cased token (e.g. `gzip`, `deflate`, `br`).
 */
expect object Decompressor {
    fun decode(encoding: String, data: ByteArray): ByteArray?
}
