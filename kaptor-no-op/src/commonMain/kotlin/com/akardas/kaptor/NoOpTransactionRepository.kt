package com.akardas.kaptor

import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.store.RequestSnapshot
import com.akardas.kaptor.store.ResponseSnapshot
import com.akardas.kaptor.store.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [TransactionRepository] that discards everything. Wire this into release builds so the
 * inspector plugin can stay installed with zero storage/UI overhead.
 *
 * ```
 * val repository = if (BuildConfig.DEBUG) TransactionRepository(driverFactory)
 *                  else NoOpTransactionRepository()
 * ```
 */
class NoOpTransactionRepository : TransactionRepository {
    override val transactions: Flow<List<HttpTransaction>> = flowOf(emptyList())
    override fun transaction(id: Long): Flow<HttpTransaction?> = flowOf(null)
    override suspend fun create(request: RequestSnapshot): Long = NO_OP_ID
    override suspend fun update(id: Long, response: ResponseSnapshot) = Unit
    override suspend fun fail(id: Long, error: String, tookMs: Long?) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun clear() = Unit
    override suspend fun deleteOlderThan(threshold: Long) = Unit

    private companion object {
        const val NO_OP_ID = -1L
    }
}
