package com.akardas.kaptor.sampleios

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay

/**
 * A [MockEngine] scripting a fake REST API under [BASE_URL] — the iOS analogue of the Android
 * sample's mock backend. Deterministic and offline; requests flow through the real Kaptor plugin.
 *
 * Note: unlike the Android sample there is no gzip route — `java.util.zip` isn't available on
 * Kotlin/Native, so the "report" endpoint serves plain JSON here.
 */
const val BASE_URL = "https://api.kaptor.demo"

private val jsonHeaders: Headers = HeadersBuilder().apply {
    append(HttpHeaders.ContentType, "application/json")
    append(HttpHeaders.Server, "nginx/1.24.0")
    append("X-Server-IP", "54.240.174.87")
}.build()

fun kaptorMockEngine(): MockEngine = MockEngine { request ->
    val path = request.url.encodedPath
    val method = request.method
    when {
        path == "/users" && method == HttpMethod.Get ->
            respond(USERS_JSON, HttpStatusCode.OK, jsonHeaders)

        path.startsWith("/users/") && method == HttpMethod.Get ->
            respond(USER_JSON, HttpStatusCode.OK, jsonHeaders)

        path == "/login" && method == HttpMethod.Post ->
            respond(LOGIN_JSON, HttpStatusCode.OK, jsonHeaders)

        path == "/search" ->
            respond(SEARCH_JSON, HttpStatusCode.OK, jsonHeaders)

        path == "/report" ->
            respond(REPORT_JSON, HttpStatusCode.OK, jsonHeaders)

        path == "/orders/999" ->
            respond(NOT_FOUND_JSON, HttpStatusCode.NotFound, jsonHeaders)

        path == "/checkout" ->
            respond(SERVER_ERROR_JSON, HttpStatusCode.InternalServerError, jsonHeaders)

        path == "/inventory" ->
            respond(UNAVAILABLE_JSON, HttpStatusCode.ServiceUnavailable, jsonHeaders)

        path == "/slow" -> {
            delay(3000)
            respond(SLOW_JSON, HttpStatusCode.OK, jsonHeaders)
        }

        else -> respond("""{"error":"no_route","path":"$path"}""", HttpStatusCode.NotFound, jsonHeaders)
    }
}

private val USERS_JSON = """
{
  "page": 1,
  "total": 3,
  "users": [
    { "id": 1, "name": "Neo Anderson", "role": "admin", "active": true },
    { "id": 2, "name": "Trinity", "role": "member", "active": true },
    { "id": 3, "name": "Morpheus", "role": "member", "active": false }
  ]
}
""".trimIndent()

private val USER_JSON = """
{
  "id": 7,
  "name": "Trinity",
  "email": "trinity@zion.io",
  "role": "member",
  "tags": ["red-pill", "operator"],
  "meta": { "createdAt": "2026-01-14T09:20:00Z", "loginCount": 42 }
}
""".trimIndent()

private val LOGIN_JSON = """
{ "token": "demo.mock-token.abc123", "expiresIn": 3600, "user": { "id": 7, "name": "Trinity" } }
""".trimIndent()

private val SEARCH_JSON = """
{
  "query": "ktor",
  "count": 2,
  "results": [
    { "id": "a1", "title": "Ktor client plugins", "score": 0.98 },
    { "id": "b2", "title": "Ktor MockEngine guide", "score": 0.91 }
  ]
}
""".trimIndent()

private val REPORT_JSON = """
{ "report": "quarterly", "rows": 1200, "note": "Served by the iOS mock engine." }
""".trimIndent()

private val NOT_FOUND_JSON = """{ "error": "not_found", "message": "Order 999 does not exist" }"""
private val SERVER_ERROR_JSON = """{ "error": "internal_error", "message": "Payment provider timed out" }"""
private val UNAVAILABLE_JSON = """{ "error": "unavailable", "message": "Inventory service is warming up" }"""
private val SLOW_JSON = """{ "ok": true, "message": "This response was delayed 3s by the mock engine." }"""
