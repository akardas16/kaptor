package com.akardas.kaptor

import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.model.TransactionStatus
import com.akardas.kaptor.store.RequestSnapshot
import com.akardas.kaptor.store.ResponseSnapshot
import com.akardas.kaptor.store.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TransactionRepository] used by tests; mirrors the SQLDelight one's behaviour. */
class FakeTransactionRepository : TransactionRepository {
    private val state = MutableStateFlow<List<HttpTransaction>>(emptyList())
    private var nextId = 1L

    override val transactions: Flow<List<HttpTransaction>> = state

    override fun transaction(id: Long): Flow<HttpTransaction?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun create(request: RequestSnapshot): Long {
        val id = nextId++
        state.value = state.value + HttpTransaction(
            id = id,
            status = request.status,
            requestDate = request.requestDate,
            responseDate = null,
            tookMs = null,
            protocol = request.protocol,
            method = request.method,
            url = request.url,
            host = request.host,
            path = request.path,
            scheme = request.scheme,
            requestContentType = request.contentType,
            requestContentLength = request.contentLength,
            requestHeaders = request.headers,
            requestBody = request.body,
            requestBodyIsPlainText = request.bodyIsPlainText,
            responseCode = null,
            responseMessage = null,
            responseContentType = null,
            responseContentLength = null,
            responseHeaders = emptyList(),
            responseBody = null,
            responseBodyIsPlainText = true,
            error = null,
        )
        return id
    }

    override suspend fun update(id: Long, response: ResponseSnapshot) = mutate(id) {
        it.copy(
            status = TransactionStatus.Complete,
            responseDate = response.responseDate,
            tookMs = response.tookMs,
            protocol = response.protocol ?: it.protocol,
            responseCode = response.code,
            responseMessage = response.message,
            responseContentType = response.contentType,
            responseContentLength = response.contentLength,
            responseHeaders = response.headers,
            responseBody = response.body,
            responseBodyIsPlainText = response.bodyIsPlainText,
        )
    }

    override suspend fun fail(id: Long, error: String, tookMs: Long?) = mutate(id) {
        it.copy(status = TransactionStatus.Failed, error = error, tookMs = tookMs)
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clear() { state.value = emptyList() }

    override suspend fun deleteOlderThan(threshold: Long) {
        state.value = state.value.filter { (it.requestDate ?: 0) >= threshold }
    }

    override suspend fun retainLatest(count: Int) {
        state.value = state.value.sortedByDescending { it.id }.take(count)
    }

    private inline fun mutate(id: Long, transform: (HttpTransaction) -> HttpTransaction) {
        state.value = state.value.map { if (it.id == id) transform(it) else it }
    }
}
