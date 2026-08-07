package com.akardas.kaptor.util

import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

actual object Decompressor {
    actual fun decode(encoding: String, data: ByteArray): ByteArray? = try {
        when (encoding) {
            "gzip", "x-gzip" -> GZIPInputStream(data.inputStream()).use { it.readBytes() }
            "deflate" -> inflate(data)
            "br", "brotli" -> brotli(data)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    /** `deflate` may be zlib-wrapped or a bare stream; try wrapped first, then raw (nowrap). */
    private fun inflate(data: ByteArray): ByteArray = try {
        InflaterInputStream(data.inputStream()).use { it.readBytes() }
    } catch (_: Exception) {
        InflaterInputStream(data.inputStream(), Inflater(true)).use { it.readBytes() }
    }

    /**
     * Brotli via the optional `org.brotli:dec` dependency, loaded reflectively so it stays an
     * opt-in extra. Add `implementation("org.brotli:dec:0.1.2")` to enable it; otherwise returns
     * null and the UI shows the encoded-body note.
     */
    private fun brotli(data: ByteArray): ByteArray? = try {
        val streamClass = Class.forName("org.brotli.dec.BrotliInputStream")
        val ctor = streamClass.getConstructor(InputStream::class.java)
        (ctor.newInstance(data.inputStream()) as InputStream).use { it.readBytes() }
    } catch (_: Throwable) {
        null
    }
}
