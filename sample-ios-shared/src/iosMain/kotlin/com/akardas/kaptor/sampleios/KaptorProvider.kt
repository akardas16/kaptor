package com.akardas.kaptor.sampleios

import com.akardas.kaptor.plugin.Kaptor
import com.akardas.kaptor.store.TransactionRepository
import com.akardas.kaptor.ui.KaptorIos
import com.akardas.kaptor.ui.KaptorMockRequest
import com.akardas.kaptor.ui.KaptorRequestRerunner
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

private const val DEMO_BEARER_TOKEN = "kp_live_demo_7f3a9c2b8e1d4a60"

/**
 * Wires the iOS sample: a SQLDelight repository, a MockEngine-backed Ktor client with the Kaptor
 * plugin installed, a rerunner, and the mock requests for the **+** sheet. Swift presents the
 * inspector via [viewController].
 */
object KaptorProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val skippedHeaders = setOf("content-length", "host", "content-type", "connection")

    val repository: TransactionRepository = KaptorIos.createRepository()

    val client: HttpClient = HttpClient(kaptorMockEngine()) {
        install(Kaptor) { repository = this@KaptorProvider.repository }
        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer $DEMO_BEARER_TOKEN")
            header(HttpHeaders.UserAgent, "Ktor client")
        }
    }

    private val rerunner = KaptorRequestRerunner { tx ->
        val url = tx.url ?: return@KaptorRequestRerunner
        scope.launch {
            runCatching {
                client.request(url) {
                    method = HttpMethod.parse(tx.method ?: "GET")
                    tx.requestHeaders
                        .filterNot { it.name.lowercase() in skippedHeaders }
                        .forEach { header(it.name, it.value) }
                    tx.requestContentType?.let { runCatching { contentType(ContentType.parse(it)) } }
                    if (!tx.requestBody.isNullOrEmpty()) setBody(tx.requestBody!!)
                }
            }
        }
    }

    private val mockRequests: List<KaptorMockRequest> = listOf(
        KaptorMockRequest("List users", "GET", "/users") { client.get("$BASE_URL/users") },
        KaptorMockRequest("Get user", "GET", "/users/7") { client.get("$BASE_URL/users/7") },
        KaptorMockRequest("Log in", "POST", "/login") {
            client.post("$BASE_URL/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"user":"trinity","password":"the-one"}""")
            }
        },
        KaptorMockRequest("Search", "GET", "/search?q=ktor") { client.get("$BASE_URL/search?q=ktor") },
        KaptorMockRequest("Report", "GET", "/report") { client.get("$BASE_URL/report") },
        KaptorMockRequest("Slow request", "GET", "/slow · 3s") { client.get("$BASE_URL/slow") },
        KaptorMockRequest("Not found", "GET", "/orders/999 · 404") { client.get("$BASE_URL/orders/999") },
        KaptorMockRequest("Server error", "POST", "/checkout · 500") { client.post("$BASE_URL/checkout") },
        KaptorMockRequest("Unavailable", "GET", "/inventory · 503") { client.get("$BASE_URL/inventory") },
    )

    /** The inspector UI, ready for SwiftUI/UIKit to present. */
    fun viewController(): UIViewController =
        KaptorIos.viewController(repository, rerunner, mockRequests)

    /** Fires a few sample requests so the inspector has traffic to show on first launch. */
    fun sendSample() {
        scope.launch {
            runCatching { client.get("$BASE_URL/users") }
            runCatching { client.get("$BASE_URL/users/7") }
            runCatching {
                client.post("$BASE_URL/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"user":"trinity","password":"the-one"}""")
                }
            }
            runCatching { client.get("$BASE_URL/orders/999") }
            runCatching { client.post("$BASE_URL/checkout") }
        }
    }
}
