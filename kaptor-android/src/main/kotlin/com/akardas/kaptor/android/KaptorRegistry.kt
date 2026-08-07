package com.akardas.kaptor.android

import com.akardas.kaptor.store.TransactionRepository
import com.akardas.kaptor.ui.KaptorMockRequest
import com.akardas.kaptor.ui.KaptorRequestRerunner

/**
 * Process-wide holder for the active [TransactionRepository] (and optional request rerunner).
 *
 * [KaptorActivity] is launched by the system from a notification's [android.app.PendingIntent],
 * so it can't be handed these directly — it reads them from here instead. Populated by
 * [KaptorAndroid.install].
 */
object KaptorRegistry {
    @Volatile
    var repository: TransactionRepository? = null
        internal set

    @Volatile
    var rerunner: KaptorRequestRerunner? = null
        internal set

    @Volatile
    var mockRequests: List<KaptorMockRequest> = emptyList()
        internal set
}
