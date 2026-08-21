package com.akardas.kaptor

import com.akardas.kaptor.model.TransactionStatus
import com.akardas.kaptor.plugin.Kaptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaptorTest {

    private fun clientWith(repository: FakeTransactionRepository): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = """{"token":"abc","expires":3600}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(engine) {
            install(Kaptor) { this.repository = repository }
        }
    }

    @Test
    fun capturesRequestAndResponse() = runTest {
        val repository = FakeTransactionRepository()
        val client = clientWith(repository)

        val response = client.post("https://api.example.com/v1/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"user":"neo"}""")
        }

        // The application can still read the body after the inspector saved it.
        assertEquals("""{"token":"abc","expires":3600}""", response.bodyAsText())

        val transaction = repository.transactions.first().single()
        assertEquals("POST", transaction.method)
        assertEquals("api.example.com", transaction.host)
        assertEquals("/v1/login", transaction.path)
        assertEquals(TransactionStatus.Complete, transaction.status)
        assertEquals(200, transaction.responseCode)
        assertEquals("""{"user":"neo"}""", transaction.requestBody)
        assertTrue(transaction.responseBody?.contains("token") == true)
        assertTrue((transaction.tookMs ?: -1) >= 0)
    }

    @Test
    fun recordsFailureWhenEngineThrows() = runTest {
        val repository = FakeTransactionRepository()
        val engine = MockEngine { _ -> throw RuntimeException("boom") }
        val client = HttpClient(engine) {
            install(Kaptor) { this.repository = repository }
        }

        val failed = runCatching {
            client.post("https://api.example.com/explode") { setBody("x") }
        }
        assertTrue(failed.isFailure)

        val transaction = repository.transactions.first().single()
        assertEquals(TransactionStatus.Failed, transaction.status)
        assertTrue(transaction.error?.contains("boom") == true)
    }

    @Test
    fun redactsConfiguredHeadersBeforeStoring() = runTest {
        val repository = FakeTransactionRepository()
        val engine = MockEngine { _ ->
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.SetCookie to listOf("session=super-secret"),
                ),
            )
        }
        val client = HttpClient(engine) {
            install(Kaptor) {
                this.repository = repository
                // Case-insensitive match on the name.
                redactHeaders = setOf("authorization", "Set-Cookie")
            }
        }

        client.get("https://api.example.com/me") {
            header(HttpHeaders.Authorization, "Bearer super-secret-token")
        }

        val tx = repository.transactions.first().single()
        val auth = tx.requestHeaders.first { it.name.equals("Authorization", ignoreCase = true) }
        val cookie = tx.responseHeaders.first { it.name.equals("Set-Cookie", ignoreCase = true) }
        assertFalse(auth.value.contains("super-secret-token"), "request secret must not be stored")
        assertFalse(cookie.value.contains("super-secret"), "response secret must not be stored")
    }

    @Test
    fun capsResponseBodyWithoutContentLength() = runTest {
        val repository = FakeTransactionRepository()
        val big = "x".repeat(2_000)
        // MockEngine sends no Content-Length here, so the cap must apply to the read bytes.
        val engine = MockEngine { _ ->
            respond(
                content = big,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(Kaptor) {
                this.repository = repository
                maxContentLength = 1_000
            }
        }

        val response = client.get("https://api.example.com/stream")
        // The application can still read the full body — only the inspector skips capturing it.
        assertEquals(big, response.bodyAsText())

        val tx = repository.transactions.first().single()
        assertNull(tx.responseBody, "oversized body must not be stored")
    }

    @Test
    fun retainsOnlyMostRecentTransactions() = runTest {
        val repository = FakeTransactionRepository()
        val engine = MockEngine { _ ->
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine) {
            install(Kaptor) {
                this.repository = repository
                maxStoredTransactions = 3
            }
        }

        repeat(5) { i -> client.get("https://api.example.com/item/$i") }

        val stored = repository.transactions.first()
        assertEquals(3, stored.size, "only the newest 3 should remain")
        // Newest-first ordering; the last two requests are the most recent.
        assertTrue(stored.any { it.url?.endsWith("/item/4") == true })
        assertTrue(stored.none { it.url?.endsWith("/item/0") == true })
    }
}
