package com.akardas.kaptor

import com.akardas.kaptor.model.TransactionStatus
import com.akardas.kaptor.plugin.Kaptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
}
