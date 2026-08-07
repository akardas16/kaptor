package com.akardas.kaptor.sample

import android.app.Application
import com.akardas.kaptor.android.KaptorAndroid
import com.akardas.kaptor.plugin.Kaptor
import com.akardas.kaptor.store.DatabaseDriverFactory
import com.akardas.kaptor.store.TransactionRepository
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

/**
 * Owns the singletons for the sample: the SQLDelight-backed repository and a Ktor client (OkHttp
 * engine) with the inspector installed. Also starts the inspector notification and provides a
 * "rerun" implementation for the swipe action.
 */
/** A clearly-fake bearer token attached to every sample request for demo purposes. */
private const val DEMO_BEARER_TOKEN = "kp_live_demo_7f3a9c2b8e1d4a60"

class SampleApplication : Application() {

    lateinit var repository: TransactionRepository
        private set

    lateinit var client: HttpClient
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Headers Ktor manages itself; replaying them verbatim would corrupt the request.
    private val skippedHeaders = setOf("content-length", "host", "content-type", "connection")

    override fun onCreate() {
        super.onCreate()

        repository = TransactionRepository(DatabaseDriverFactory(this))

        // A MockEngine-backed client: requests hit the scripted fake API in MockBackend.kt
        // (offline, deterministic) but still flow through the real Kaptor capture plugin.
        client = HttpClient(kaptorMockEngine()) {
            install(Kaptor) {
                repository = this@SampleApplication.repository
            }
            // Attach an auth bearer token and a Ktor user-agent to every outgoing request, so
            // captured transactions show realistic Authorization / User-Agent headers.
            defaultRequest {
                header(HttpHeaders.Authorization, "Bearer $DEMO_BEARER_TOKEN")
                header(HttpHeaders.UserAgent, "Ktor client")
            }
        }

        // Rerun rebuilds the captured request and re-sends it — the plugin captures a NEW entry.
        val rerunner = KaptorRequestRerunner { tx ->
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

        // Mock requests shown in the inspector's "+" sheet — each fires through our client, so the
        // Kaptor plugin captures it. Lets you generate test traffic without leaving the inspector.
        val mockRequests = listOf(
            KaptorMockRequest("List users", "GET", "/users") { client.get("$BASE_URL/users") },
            KaptorMockRequest("Get user", "GET", "/users/7") { client.get("$BASE_URL/users/7") },
            KaptorMockRequest("Log in", "POST", "/login") {
                client.post("$BASE_URL/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"user":"trinity","password":"the-one"}""")
                }
            },
            KaptorMockRequest("Search", "GET", "/search?q=ktor") { client.get("$BASE_URL/search?q=ktor") },
            KaptorMockRequest("Gzip report", "GET", "/report.gz") { client.get("$BASE_URL/report.gz") },
            KaptorMockRequest("Slow request", "GET", "/slow · 3s") { client.get("$BASE_URL/slow") },
            KaptorMockRequest("Not found", "GET", "/orders/999 · 404") { client.get("$BASE_URL/orders/999") },
            KaptorMockRequest("Server error", "POST", "/checkout · 500") { client.post("$BASE_URL/checkout") },
            KaptorMockRequest("Unavailable", "GET", "/inventory · 503") { client.get("$BASE_URL/inventory") },
        )

        // Post/keep the inspector notification up to date; tapping it opens the inspector.
        KaptorAndroid.install(this, repository, rerunner, mockRequests)
    }
}
