package com.akardas.kaptor.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.akardas.kaptor.model.HttpTransaction

/**
 * Re-issues a captured request. The inspector can't do this itself (it has no HTTP client), so the
 * host app provides an implementation that rebuilds the request from [HttpTransaction] and sends it
 * through its own Ktor client. Provided via [LocalKaptorRequestRerunner]; when absent, the
 * swipe "Rerun" action is hidden.
 */
fun interface KaptorRequestRerunner {
    fun rerun(transaction: HttpTransaction)
}

/** The active rerunner, or `null` if the host app didn't provide one. */
val LocalKaptorRequestRerunner = staticCompositionLocalOf<KaptorRequestRerunner?> { null }
