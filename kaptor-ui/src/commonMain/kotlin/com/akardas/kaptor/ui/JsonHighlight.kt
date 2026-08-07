package com.akardas.kaptor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Colors used to syntax-highlight a JSON body. */
data class JsonColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val punctuation: Color,
)

/** Theme-aware default palette — brighter pastels on dark, saturated darks on light. */
@Composable
fun rememberJsonColors(): JsonColors =
    if (isSystemInDarkTheme()) {
        JsonColors(
            key = Color(0xFF7FD4FF),
            string = Color(0xFF9BE29B),
            number = Color(0xFFFFC46B),
            keyword = Color(0xFFD08BE8),
            punctuation = Color(0xFF9AA0A6),
        )
    } else {
        JsonColors(
            key = Color(0xFF0B6BCB),
            string = Color(0xFF1B7A3D),
            number = Color(0xFFB25000),
            keyword = Color(0xFF7A2FA6),
            punctuation = Color(0xFF5F6368),
        )
    }

/**
 * Tokenizes an (already pretty-printed) JSON string into a colored [AnnotatedString]. Property
 * names, string values, numbers, and `true`/`false`/`null` keywords each get their own color;
 * whitespace and layout are preserved verbatim. Never throws — unrecognized characters are
 * appended plain, so it is safe on arbitrary input.
 */
fun jsonToAnnotatedString(json: String, colors: JsonColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = json.length
    while (i < n) {
        val c = json[i]
        when {
            c == '"' -> {
                val start = i
                i++
                while (i < n) {
                    when (json[i]) {
                        '\\' -> i += 2
                        '"' -> { i++; break }
                        else -> i++
                    }
                }
                val token = json.substring(start, i.coerceAtMost(n))
                // A string is a key when the next non-whitespace character is a colon.
                var j = i
                while (j < n && json[j].isWhitespace()) j++
                val isKey = j < n && json[j] == ':'
                withStyle(SpanStyle(color = if (isKey) colors.key else colors.string)) { append(token) }
            }

            c.isDigit() || (c == '-' && i + 1 < n && json[i + 1].isDigit()) -> {
                val start = i
                i++
                while (i < n && (json[i].isDigit() || json[i] in ".eE+-")) i++
                withStyle(SpanStyle(color = colors.number)) { append(json.substring(start, i)) }
            }

            json.startsWith("true", i) || json.startsWith("false", i) || json.startsWith("null", i) -> {
                val keyword = when {
                    json.startsWith("true", i) -> "true"
                    json.startsWith("false", i) -> "false"
                    else -> "null"
                }
                withStyle(SpanStyle(color = colors.keyword)) { append(keyword) }
                i += keyword.length
            }

            c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',' -> {
                withStyle(SpanStyle(color = colors.punctuation)) { append(c.toString()) }
                i++
            }

            else -> {
                append(c.toString())
                i++
            }
        }
    }
}

/**
 * Overlays a search highlight on top of an already-styled [AnnotatedString], keeping the existing
 * syntax colors. [text] must be the plain text the receiver was built from (offsets must match).
 */
fun AnnotatedString.withSearchHighlight(text: String, query: String, activeStart: Int = -1): AnnotatedString {
    if (query.isBlank()) return this
    val haystack = text.lowercase()
    val needle = query.lowercase()
    return buildAnnotatedString {
        append(this@withSearchHighlight)
        var start = 0
        while (true) {
            val match = haystack.indexOf(needle, start)
            if (match < 0) break
            val bg = if (match == activeStart) ACTIVE_MATCH_BG else MATCH_BG
            addStyle(
                SpanStyle(background = bg, color = Color.Black),
                match,
                match + query.length,
            )
            start = match + query.length
        }
    }
}
