package com.akardas.kaptor.store

import com.akardas.kaptor.model.HttpHeader
import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to captured HTTP transactions. The plugin writes through the `create`/
 * `update` methods; the UI observes via [transactions] and [transaction].
 */
interface TransactionRepository {

    /** All transactions, newest first, emitting a new list whenever the store changes. */
    val transactions: Flow<List<HttpTransaction>>

    /** A single transaction observed by id, or `null` if it no longer exists. */
    fun transaction(id: Long): Flow<HttpTransaction?>

    /** Inserts a freshly-started request and returns its generated id. */
    suspend fun create(request: RequestSnapshot): Long

    /** Records the response for a previously created transaction. */
    suspend fun update(id: Long, response: ResponseSnapshot)

    /** Records a failure for a previously created transaction. */
    suspend fun fail(id: Long, error: String, tookMs: Long?)

    /** Removes a single transaction by id. */
    suspend fun delete(id: Long)

    /** Removes every stored transaction. */
    suspend fun clear()

    /** Removes transactions whose request date is older than [threshold] (epoch millis). */
    suspend fun deleteOlderThan(threshold: Long)

    /** Keeps only the [count] most recent transactions, deleting any older ones beyond that. */
    suspend fun retainLatest(count: Int)
}

/** The data captured when a request is sent. */
data class RequestSnapshot(
    val requestDate: Long,
    val method: String?,
    val url: String?,
    val host: String?,
    val path: String?,
    val scheme: String?,
    val protocol: String?,
    val contentType: String?,
    val contentLength: Long?,
    val headers: List<HttpHeader>,
    val body: String?,
    val bodyIsPlainText: Boolean,
    val status: TransactionStatus = TransactionStatus.Requested,
)

/** The data captured when a response arrives. */
data class ResponseSnapshot(
    val responseDate: Long,
    val tookMs: Long?,
    val protocol: String?,
    val code: Int?,
    val message: String?,
    val contentType: String?,
    val contentLength: Long?,
    val headers: List<HttpHeader>,
    val body: String?,
    val bodyIsPlainText: Boolean,
)
