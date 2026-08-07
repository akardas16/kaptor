package com.akardas.kaptor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Background for all search matches; the active match gets [ACTIVE_MATCH_BG]. */
internal val MATCH_BG = Color(0xFFFFEB3B)
internal val ACTIVE_MATCH_BG = Color(0xFFFF7043)

/** Start offsets of every case-insensitive occurrence of [query] in [text]. */
internal fun searchMatches(text: String, query: String): List<Int> {
    if (query.isBlank()) return emptyList()
    val haystack = text.lowercase()
    val needle = query.lowercase()
    val out = ArrayList<Int>()
    var start = 0
    while (true) {
        val match = haystack.indexOf(needle, start)
        if (match < 0) break
        out.add(match)
        start = match + needle.length
    }
    return out
}

/**
 * Returns [text] as an [AnnotatedString] with every case-insensitive occurrence of [query]
 * highlighted. The occurrence starting at [activeStart] (if any) gets the active color so the
 * currently-focused match stands out. Returns the plain text unchanged when the query is blank.
 */
@Composable
internal fun highlight(text: String, query: String, activeStart: Int = -1): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()

    return buildAnnotatedString {
        var start = 0
        while (true) {
            val match = lowerText.indexOf(lowerQuery, start)
            if (match < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, match))
            val bg = if (match == activeStart) ACTIVE_MATCH_BG else MATCH_BG
            withStyle(SpanStyle(background = bg, color = Color.Black)) {
                append(text.substring(match, match + query.length))
            }
            start = match + query.length
        }
    }
}
