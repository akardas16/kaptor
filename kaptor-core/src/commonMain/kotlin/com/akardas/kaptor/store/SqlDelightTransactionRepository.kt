package com.akardas.kaptor.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.akardas.kaptor.db.HttpTransactionEntity
import com.akardas.kaptor.db.KaptorDatabase
import com.akardas.kaptor.model.HttpHeader
import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.model.TransactionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [TransactionRepository] backed by SQLDelight, so captured traffic survives process restarts.
 *
 * @param ioDispatcher dispatcher used for database work; overridable in tests.
 */
class SqlDelightTransactionRepository(
    private val database: KaptorDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TransactionRepository {

    private val queries = database.transactionQueries

    override val transactions: Flow<List<HttpTransaction>> =
        queries.selectAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun transaction(id: Long): Flow<HttpTransaction?> =
        queries.selectById(id)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { it?.toDomain() }

    override suspend fun create(request: RequestSnapshot): Long = withContext(ioDispatcher) {
        queries.transactionWithResult {
            queries.insert(
                requestDate = request.requestDate,
                method = request.method,
                url = request.url,
                host = request.host,
                path = request.path,
                scheme = request.scheme,
                protocol = request.protocol,
                requestContentType = request.contentType,
                requestContentLength = request.contentLength,
                requestHeaders = HttpHeader.encode(request.headers),
                requestBody = request.body,
                requestBodyIsPlainText = request.bodyIsPlainText,
                status = request.status.name,
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun update(id: Long, response: ResponseSnapshot): Unit = withContext(ioDispatcher) {
        queries.updateResponse(
            id = id,
            responseDate = response.responseDate,
            tookMs = response.tookMs,
            protocol = response.protocol,
            responseCode = response.code?.toLong(),
            responseMessage = response.message,
            responseContentType = response.contentType,
            responseContentLength = response.contentLength,
            responseHeaders = HttpHeader.encode(response.headers),
            responseBody = response.body,
            responseBodyIsPlainText = response.bodyIsPlainText,
            status = TransactionStatus.Complete.name,
        )
    }

    override suspend fun fail(id: Long, error: String, tookMs: Long?): Unit = withContext(ioDispatcher) {
        queries.updateError(
            id = id,
            error = error,
            tookMs = tookMs,
            responseDate = null,
            status = TransactionStatus.Failed.name,
        )
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        queries.deleteById(id)
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        queries.clear()
    }

    override suspend fun deleteOlderThan(threshold: Long): Unit = withContext(ioDispatcher) {
        queries.deleteOlderThan(threshold)
    }

    override suspend fun retainLatest(count: Int): Unit = withContext(ioDispatcher) {
        queries.retainLatest(count.toLong())
    }
}

private fun HttpTransactionEntity.toDomain(): HttpTransaction = HttpTransaction(
    id = id,
    status = runCatching { TransactionStatus.valueOf(status) }.getOrDefault(TransactionStatus.Requested),
    requestDate = requestDate,
    responseDate = responseDate,
    tookMs = tookMs,
    protocol = protocol,
    method = method,
    url = url,
    host = host,
    path = path,
    scheme = scheme,
    requestContentType = requestContentType,
    requestContentLength = requestContentLength,
    requestHeaders = HttpHeader.decode(requestHeaders),
    requestBody = requestBody,
    requestBodyIsPlainText = requestBodyIsPlainText,
    responseCode = responseCode?.toInt(),
    responseMessage = responseMessage,
    responseContentType = responseContentType,
    responseContentLength = responseContentLength,
    responseHeaders = HttpHeader.decode(responseHeaders),
    responseBody = responseBody,
    responseBodyIsPlainText = responseBodyIsPlainText,
    error = error,
)

/** Convenience factory: builds a repository from a [DatabaseDriverFactory]. */
fun TransactionRepository(factory: DatabaseDriverFactory): TransactionRepository =
    SqlDelightTransactionRepository(KaptorDatabase(factory.createDriver()))
