package com.akardas.kaptor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

/** A parsed JSON value: object, array, or primitive. */
sealed interface JsonNode
data class JObj(val entries: List<Pair<String, JsonNode>>) : JsonNode
data class JArr(val items: List<JsonNode>) : JsonNode
data class JPrim(val text: String, val kind: JsonKind) : JsonNode

enum class JsonKind { STRING, NUMBER, KEYWORD }

/** A tolerant recursive-descent JSON parser. Returns `null` on malformed input. */
object JsonParser {
    fun parseOrNull(raw: String): JsonNode? = try {
        val p = Cursor(raw)
        p.skipWs()
        val node = p.value()
        p.skipWs()
        if (p.pos == raw.length) node else null
    } catch (_: Exception) {
        null
    }

    private class Cursor(val s: String) {
        var pos = 0

        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        fun value(): JsonNode {
            skipWs()
            return when (s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JPrim(string(), JsonKind.STRING)
                else -> when {
                    s.startsWith("true", pos) -> { pos += 4; JPrim("true", JsonKind.KEYWORD) }
                    s.startsWith("false", pos) -> { pos += 5; JPrim("false", JsonKind.KEYWORD) }
                    s.startsWith("null", pos) -> { pos += 4; JPrim("null", JsonKind.KEYWORD) }
                    else -> number()
                }
            }
        }

        private fun obj(): JObj {
            pos++ // {
            skipWs()
            val entries = ArrayList<Pair<String, JsonNode>>()
            if (s[pos] == '}') { pos++; return JObj(entries) }
            while (true) {
                skipWs()
                val key = string()
                skipWs()
                require(s[pos] == ':'); pos++
                entries.add(key to value())
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; break }
                    else -> error("expected , or }")
                }
            }
            return JObj(entries)
        }

        private fun arr(): JArr {
            pos++ // [
            skipWs()
            val items = ArrayList<JsonNode>()
            if (s[pos] == ']') { pos++; return JArr(items) }
            while (true) {
                items.add(value())
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; break }
                    else -> error("expected , or ]")
                }
            }
            return JArr(items)
        }

        private fun string(): String {
            val start = pos
            require(s[pos] == '"'); pos++
            while (pos < s.length) {
                when (s[pos]) {
                    '\\' -> pos += 2
                    '"' -> { pos++; return s.substring(start, pos) }
                    else -> pos++
                }
            }
            error("unterminated string")
        }

        private fun number(): JPrim {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return JPrim(s.substring(start, pos), JsonKind.NUMBER)
        }
    }
}

private data class JsonRow(val text: AnnotatedString, val togglePath: String?)

/**
 * Renders [root] as an interactive tree: tapping any object/array row collapses or expands it.
 * A gutter marker (▾ expanded, ▸ collapsed) keeps every line aligned. Syntax-colored via [colors].
 */
@Composable
fun CollapsibleJson(
    root: JsonNode,
    colors: JsonColors,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val collapsed = remember(root) { mutableStateListOf<String>() }
    val rows = buildJsonRows(root, collapsed.toHashSet(), colors)

    Column(Modifier.fillMaxWidth()) {
        for (row in rows) {
            val modifier = if (row.togglePath != null) {
                Modifier.fillMaxWidth().clickable {
                    if (!collapsed.remove(row.togglePath)) collapsed.add(row.togglePath)
                }
            } else {
                Modifier.fillMaxWidth()
            }
            Text(
                text = row.text,
                modifier = modifier,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                lineHeight = lineHeight,
            )
        }
    }
}

private fun buildJsonRows(root: JsonNode, collapsed: Set<String>, colors: JsonColors): List<JsonRow> {
    val out = ArrayList<JsonRow>()
    val punct = SpanStyle(color = colors.punctuation)
    val keyStyle = SpanStyle(color = colors.key)

    fun AnnotatedString.Builder.primitive(node: JPrim) {
        val color = when (node.kind) {
            JsonKind.STRING -> colors.string
            JsonKind.NUMBER -> colors.number
            JsonKind.KEYWORD -> colors.keyword
        }
        withStyle(SpanStyle(color = color)) { append(node.text) }
    }

    fun line(gutter: String, indent: Int, build: AnnotatedString.Builder.() -> Unit): AnnotatedString =
        buildAnnotatedString {
            withStyle(punct) { append(gutter) } // "▾ " / "▸ " / "  "
            append("  ".repeat(indent))
            build()
        }

    fun emit(
        node: JsonNode,
        indent: Int,
        keyPrefix: (AnnotatedString.Builder.() -> Unit)?,
        trailingComma: Boolean,
        path: String,
    ) {
        val comma: AnnotatedString.Builder.() -> Unit = { if (trailingComma) withStyle(punct) { append(",") } }
        when (node) {
            is JPrim -> out.add(
                JsonRow(line("  ", indent) { keyPrefix?.invoke(this); primitive(node); comma() }, null),
            )

            is JObj -> when {
                node.entries.isEmpty() ->
                    out.add(JsonRow(line("  ", indent) { keyPrefix?.invoke(this); withStyle(punct) { append("{}") }; comma() }, null))

                path in collapsed ->
                    out.add(JsonRow(line("▸ ", indent) {
                        keyPrefix?.invoke(this)
                        withStyle(punct) { append("{ … ${node.entries.size} }") }
                        comma()
                    }, path))

                else -> {
                    out.add(JsonRow(line("▾ ", indent) { keyPrefix?.invoke(this); withStyle(punct) { append("{") } }, path))
                    node.entries.forEachIndexed { i, (key, value) ->
                        emit(value, indent + 1, { withStyle(keyStyle) { append(key) }; withStyle(punct) { append(": ") } }, i < node.entries.lastIndex, "$path.$key")
                    }
                    out.add(JsonRow(line("  ", indent) { withStyle(punct) { append("}") }; comma() }, null))
                }
            }

            is JArr -> when {
                node.items.isEmpty() ->
                    out.add(JsonRow(line("  ", indent) { keyPrefix?.invoke(this); withStyle(punct) { append("[]") }; comma() }, null))

                path in collapsed ->
                    out.add(JsonRow(line("▸ ", indent) {
                        keyPrefix?.invoke(this)
                        withStyle(punct) { append("[ … ${node.items.size} ]") }
                        comma()
                    }, path))

                else -> {
                    out.add(JsonRow(line("▾ ", indent) { keyPrefix?.invoke(this); withStyle(punct) { append("[") } }, path))
                    node.items.forEachIndexed { i, value ->
                        emit(value, indent + 1, null, i < node.items.lastIndex, "$path[$i]")
                    }
                    out.add(JsonRow(line("  ", indent) { withStyle(punct) { append("]") }; comma() }, null))
                }
            }
        }
    }

    emit(root, 0, null, false, "$")
    return out
}
