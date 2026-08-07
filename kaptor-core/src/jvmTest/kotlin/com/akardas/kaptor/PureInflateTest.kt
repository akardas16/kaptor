package com.akardas.kaptor

import com.akardas.kaptor.util.ContentDecoder
import com.akardas.kaptor.util.PureInflate
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the pure-Kotlin inflater (the code path iOS uses at runtime) against real gzip/deflate
 * output produced by `java.util.zip`. DEFLATE is platform-independent, so passing here means the
 * native path is correct too.
 */
class PureInflateTest {

    private val sample =
        """{"message":"Hello, Kaptor!","items":[1,2,3,4,5],"nested":{"a":true,"b":null}} """
            .repeat(50)

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(bytes) } }.toByteArray()

    private fun deflate(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { DeflaterOutputStream(it).use { d -> d.write(bytes) } }.toByteArray()

    @Test
    fun gunzipRoundTrip() {
        val original = sample.encodeToByteArray()
        assertContentEquals(original, PureInflate.gunzip(gzip(original)))
    }

    @Test
    fun deflateRoundTrip() {
        val original = sample.encodeToByteArray()
        assertContentEquals(original, PureInflate.inflateDeflate(deflate(original)))
    }

    @Test
    fun handlesStoredBlocksAndSmallInputs() {
        // Very small payloads are typically emitted as stored (uncompressed) DEFLATE blocks.
        val original = "{}".encodeToByteArray()
        assertContentEquals(original, PureInflate.gunzip(gzip(original)))
    }

    @Test
    fun contentDecoderDecodesGzipHeader() {
        // Exercises the JVM Decompressor actual + ContentDecoder wiring end to end.
        val decoded = ContentDecoder.decode("gzip", gzip(sample.encodeToByteArray()))
        assertEquals(sample, decoded.text)
        assertTrue(decoded.isPlainText)
        assertTrue(decoded.note?.contains("gzip") == true)
    }

    @Test
    fun unsupportedEncodingReportsNote() {
        val decoded = ContentDecoder.decode("br", byteArrayOf(1, 2, 3))
        // Brotli decoder is optional; without it on the classpath this is reported, not crashed.
        assertEquals(null, decoded.text)
        assertTrue(decoded.note?.contains("br") == true)
    }
}
