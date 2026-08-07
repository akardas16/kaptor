package com.akardas.kaptor.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Platform bridge for sharing a transaction out of the inspector (Android share sheet, iOS
 * `UIActivityViewController`, …). Provided via [LocalKaptorShareHandler]; when absent, the
 * share menu is hidden.
 */
interface KaptorShareHandler {
    /** Shares plain text (used for "share as text" and "share as cURL"). */
    fun shareText(text: String, subject: String)

    /** Writes [text] to a temporary file named [fileName] and shares it. */
    fun shareFile(fileName: String, text: String, mimeType: String)
}

/** The active share handler, or `null` if the host app didn't provide one. */
val LocalKaptorShareHandler = staticCompositionLocalOf<KaptorShareHandler?> { null }
