package com.akardas.kaptor.util

/**
 * A dependency-free DEFLATE decompressor (RFC 1951) with gzip (RFC 1952) and zlib (RFC 1950)
 * framing. A faithful Kotlin port of Mark Adler's public-domain `puff.c` reference inflater.
 *
 * Used for content decoding on Kotlin/Native (iOS), where no `java.util.zip` exists. The JVM and
 * Android targets use the platform inflater instead — see the `Decompressor` actuals.
 */
internal object PureInflate {

    /** Decompresses a gzip stream (RFC 1952). */
    fun gunzip(data: ByteArray): ByteArray {
        require(data.size >= 18) { "gzip stream too short" }
        require(data[0].toInt() and 0xff == 0x1f && data[1].toInt() and 0xff == 0x8b) { "bad gzip magic" }
        require(data[2].toInt() and 0xff == 8) { "unsupported gzip compression method" }
        val flg = data[3].toInt() and 0xff
        var pos = 10
        if (flg and 0x04 != 0) { // FEXTRA
            val xlen = (data[pos].toInt() and 0xff) or ((data[pos + 1].toInt() and 0xff) shl 8)
            pos += 2 + xlen
        }
        if (flg and 0x08 != 0) { while (data[pos].toInt() != 0) pos++; pos++ } // FNAME
        if (flg and 0x10 != 0) { while (data[pos].toInt() != 0) pos++; pos++ } // FCOMMENT
        if (flg and 0x02 != 0) pos += 2 // FHCRC
        return Inflater(data, pos).inflate()
    }

    /**
     * Decompresses a `deflate` Content-Encoding payload, which may be zlib-wrapped (RFC 1950) or
     * a bare DEFLATE stream — both occur in the wild, so we detect the zlib header.
     */
    fun inflateDeflate(data: ByteArray): ByteArray {
        if (data.size >= 2) {
            val cmf = data[0].toInt() and 0xff
            val flg = data[1].toInt() and 0xff
            val zlibWrapped = (cmf and 0x0f) == 8 && ((cmf shl 8) or flg) % 31 == 0
            if (zlibWrapped) return Inflater(data, 2).inflate()
        }
        return Inflater(data, 0).inflate()
    }

    // Length code base values and extra bits (RFC 1951 §3.2.5).
    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
    private val CODE_LENGTH_ORDER = intArrayOf(
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
    )

    private const val MAX_BITS = 15
    private const val MAX_L_CODES = 286
    private const val MAX_D_CODES = 30
    private const val FIX_L_CODES = 288

    /** A canonical Huffman code table: [count] symbols of each bit length, ordered [symbol] list. */
    private class Huffman(maxSymbols: Int) {
        val count = IntArray(MAX_BITS + 1)
        val symbol = IntArray(maxSymbols)
    }

    private class Inflater(private val input: ByteArray, startIndex: Int) {
        private var inPos = startIndex
        private var bitBuf = 0
        private var bitCnt = 0

        private var out = ByteArray(1024)
        private var outLen = 0

        fun inflate(): ByteArray {
            var last: Int
            do {
                last = bits(1)
                when (bits(2)) {
                    0 -> stored()
                    1 -> fixedBlock()
                    2 -> dynamicBlock()
                    else -> error("invalid block type")
                }
            } while (last == 0)
            return out.copyOf(outLen)
        }

        private fun bits(need: Int): Int {
            var value = bitBuf
            while (bitCnt < need) {
                if (inPos >= input.size) error("ran out of input")
                value = value or ((input[inPos++].toInt() and 0xff) shl bitCnt)
                bitCnt += 8
            }
            bitBuf = value shr need
            bitCnt -= need
            return value and ((1 shl need) - 1)
        }

        private fun appendByte(b: Int) {
            if (outLen == out.size) out = out.copyOf(out.size * 2)
            out[outLen++] = b.toByte()
        }

        private fun stored() {
            // Stored blocks are byte-aligned; drop the partial bit buffer.
            bitBuf = 0
            bitCnt = 0
            if (inPos + 4 > input.size) error("incomplete stored block header")
            val len = (input[inPos].toInt() and 0xff) or ((input[inPos + 1].toInt() and 0xff) shl 8)
            inPos += 4 // skip LEN and NLEN
            if (inPos + len > input.size) error("incomplete stored block")
            for (i in 0 until len) appendByte(input[inPos++].toInt() and 0xff)
        }

        private fun construct(h: Huffman, lengths: IntArray, n: Int) {
            for (len in 0..MAX_BITS) h.count[len] = 0
            for (symbol in 0 until n) h.count[lengths[symbol]]++
            if (h.count[0] == n) return
            val offs = IntArray(MAX_BITS + 2)
            offs[1] = 0
            for (len in 1 until MAX_BITS) offs[len + 1] = offs[len] + h.count[len]
            for (symbol in 0 until n) {
                if (lengths[symbol] != 0) h.symbol[offs[lengths[symbol]]++] = symbol
            }
        }

        private fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..MAX_BITS) {
                code = code or bits(1)
                val count = h.count[len]
                if (code - count < first) return h.symbol[index + (code - first)]
                index += count
                first = (first + count) shl 1
                code = code shl 1
            }
            error("invalid Huffman code")
        }

        private fun codes(lenCode: Huffman, distCode: Huffman) {
            while (true) {
                var symbol = decode(lenCode)
                when {
                    symbol == 256 -> return
                    symbol < 256 -> appendByte(symbol)
                    else -> {
                        symbol -= 257
                        if (symbol >= 29) error("invalid length code")
                        val length = LENGTH_BASE[symbol] + bits(LENGTH_EXTRA[symbol])
                        val distSymbol = decode(distCode)
                        val dist = DIST_BASE[distSymbol] + bits(DIST_EXTRA[distSymbol])
                        if (dist > outLen) error("distance too far back")
                        var remaining = length
                        while (remaining-- > 0) {
                            appendByte(out[outLen - dist].toInt() and 0xff)
                        }
                    }
                }
            }
        }

        private fun fixedBlock() {
            val lengths = IntArray(FIX_L_CODES)
            var i = 0
            while (i < 144) lengths[i++] = 8
            while (i < 256) lengths[i++] = 9
            while (i < 280) lengths[i++] = 7
            while (i < 288) lengths[i++] = 8
            val lenCode = Huffman(FIX_L_CODES)
            construct(lenCode, lengths, FIX_L_CODES)
            val distLengths = IntArray(MAX_D_CODES) { 5 }
            val distCode = Huffman(MAX_D_CODES)
            construct(distCode, distLengths, MAX_D_CODES)
            codes(lenCode, distCode)
        }

        private fun dynamicBlock() {
            val nLen = bits(5) + 257
            val nDist = bits(5) + 1
            val nCode = bits(4) + 4
            if (nLen > MAX_L_CODES || nDist > MAX_D_CODES) error("too many codes")

            val codeLengths = IntArray(19)
            for (i in 0 until nCode) codeLengths[CODE_LENGTH_ORDER[i]] = bits(3)
            val codeLengthCode = Huffman(19)
            construct(codeLengthCode, codeLengths, 19)

            val lengths = IntArray(MAX_L_CODES + MAX_D_CODES)
            var index = 0
            while (index < nLen + nDist) {
                val symbol = decode(codeLengthCode)
                when {
                    symbol < 16 -> lengths[index++] = symbol
                    symbol == 16 -> {
                        if (index == 0) error("repeat with no previous length")
                        val prev = lengths[index - 1]
                        var repeat = 3 + bits(2)
                        while (repeat-- > 0) lengths[index++] = prev
                    }
                    symbol == 17 -> {
                        var repeat = 3 + bits(3)
                        while (repeat-- > 0) lengths[index++] = 0
                    }
                    else -> { // 18
                        var repeat = 11 + bits(7)
                        while (repeat-- > 0) lengths[index++] = 0
                    }
                }
            }
            if (index > nLen + nDist) error("too many lengths")

            val lenCode = Huffman(MAX_L_CODES)
            construct(lenCode, lengths, nLen)
            val distLengths = IntArray(MAX_D_CODES)
            for (i in 0 until nDist) distLengths[i] = lengths[nLen + i]
            val distCode = Huffman(MAX_D_CODES)
            construct(distCode, distLengths, nDist)
            codes(lenCode, distCode)
        }
    }
}
