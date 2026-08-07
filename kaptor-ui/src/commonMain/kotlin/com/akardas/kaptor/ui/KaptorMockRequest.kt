package com.akardas.kaptor.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A predefined ("mock") request a developer can fire from the inspector's **+** sheet to generate
 * traffic while testing. The inspector has no HTTP client of its own, so [send] is supplied by the
 * host app and should issue the request through its own Ktor client — the Kaptor plugin then
 * captures it like any other call.
 *
 * ```
 * KaptorMockRequest("List users", method = "GET", subtitle = "/users") {
 *     client.get("https://api.example.com/users")
 * }
 * ```
 */
class KaptorMockRequest(
    val title: String,
    val method: String? = null,
    val subtitle: String? = null,
    val send: suspend () -> Unit,
)

/**
 * The mock requests shown in the **+** sheet on the list screen. Provide a non-empty list (via
 * [androidx.compose.runtime.CompositionLocalProvider], or `KaptorAndroid.install(...)`) to reveal
 * the button; the default empty list hides it.
 */
val LocalKaptorMockRequests = staticCompositionLocalOf<List<KaptorMockRequest>> { emptyList() }
