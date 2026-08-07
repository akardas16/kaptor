package com.akardas.kaptor.util

/**
 * Small dependency-free formatting helpers used by the UI layer. Kept in core so both the
 * bundled Compose UI and any custom consumer UI share identical rendering.
 */
object FormatUtils {

    /** Formats a byte count as `1.2 kB`, `3.4 MB`, etc. (SI units, matching Chucker). */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1000) return "$bytes B"
        val units = listOf("kB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1000 && unitIndex < units.lastIndex) {
            value /= 1000.0
            unitIndex++
        }
        return "${roundTo(value, 1)} ${units[unitIndex]}"
    }

    /** Formats a millisecond duration as `123 ms` or `1.23 s`. */
    fun formatDuration(ms: Long): String =
        if (ms < 1000) "$ms ms" else "${roundTo(ms / 1000.0, 2)} s"

    /** True when [contentType] looks like a JSON payload. */
    fun isJson(contentType: String?): Boolean {
        val ct = contentType?.lowercase() ?: return false
        return ct.contains("json")
    }

    /**
     * Pretty-prints a JSON string with two-space indentation. Returns the input unchanged if it
     * is not valid JSON, so it is always safe to call on arbitrary response bodies.
     */
    fun formatJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        val first = trimmed[0]
        if (first != '{' && first != '[') return raw
        return try {
            JsonPrettyPrinter(trimmed).print()
        } catch (_: Exception) {
            raw
        }
    }

    private fun roundTo(value: Double, decimals: Int): String {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        val rounded = kotlin.math.round(value * factor) / factor
        // Trim trailing ".0" for whole numbers.
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString()
    }
}

/**
 * A minimal recursive-descent JSON pretty printer. Not a validator: it re-serializes a
 * well-formed document with indentation and throws on malformed input (caught by [FormatUtils]).
 */
private class JsonPrettyPrinter(private val src: String) {
    private var pos = 0
    private val out = StringBuilder()
    private var indent = 0

    fun print(): String {
        skipWs()
        value()
        skipWs()
        if (pos != src.length) error("Trailing content at $pos")
        return out.toString()
    }

    private fun value() {
        when (val c = peek()) {
            '{' -> obj()
            '[' -> arr()
            '"' -> out.append(string())
            else -> if (c == '-' || c.isDigit() || c.isLetter()) out.append(literal())
            else error("Unexpected '$c' at $pos")
        }
    }

    private fun obj() {
        expect('{')
        skipWs()
        if (peek() == '}') { pos++; out.append("{}"); return }
        out.append("{\n")
        indent++
        var first = true
        while (true) {
            if (!first) out.append(",\n")
            first = false
            skipWs()
            appendIndent()
            out.append(string())
            skipWs()
            expect(':')
            out.append(": ")
            skipWs()
            value()
            skipWs()
            when (peek()) {
                ',' -> pos++
                '}' -> { pos++; break }
                else -> error("Expected ',' or '}' at $pos")
            }
        }
        indent--
        out.append("\n")
        appendIndent()
        out.append("}")
    }

    private fun arr() {
        expect('[')
        skipWs()
        if (peek() == ']') { pos++; out.append("[]"); return }
        out.append("[\n")
        indent++
        var first = true
        while (true) {
            if (!first) out.append(",\n")
            first = false
            skipWs()
            appendIndent()
            value()
            skipWs()
            when (peek()) {
                ',' -> pos++
                ']' -> { pos++; break }
                else -> error("Expected ',' or ']' at $pos")
            }
        }
        indent--
        out.append("\n")
        appendIndent()
        out.append("]")
    }

    private fun string(): String {
        val start = pos
        expect('"')
        while (pos < src.length) {
            when (src[pos]) {
                '\\' -> pos += 2
                '"' -> { pos++; return src.substring(start, pos) }
                else -> pos++
            }
        }
        error("Unterminated string from $start")
    }

    private fun literal(): String {
        val start = pos
        while (pos < src.length && src[pos] !in ",]}\n\r\t ") pos++
        return src.substring(start, pos)
    }

    private fun appendIndent() {
        repeat(indent) { out.append("  ") }
    }

    private fun peek(): Char = if (pos < src.length) src[pos] else error("Unexpected end")
    private fun expect(c: Char) {
        if (peek() != c) error("Expected '$c' at $pos"); pos++
    }
    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }
}
