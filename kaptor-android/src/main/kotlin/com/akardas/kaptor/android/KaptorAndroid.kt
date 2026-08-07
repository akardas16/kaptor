package com.akardas.kaptor.android

import android.content.Context
import android.content.Intent
import com.akardas.kaptor.store.TransactionRepository
import com.akardas.kaptor.ui.KaptorMockRequest
import com.akardas.kaptor.ui.KaptorRequestRerunner

/**
 * Public entry point for the Android launcher.
 *
 * Typical debug-build wiring:
 * ```
 * val repository = TransactionRepository(DatabaseDriverFactory(context))
 * val client = HttpClient(OkHttp) { install(Kaptor) { this.repository = repository } }
 * KaptorAndroid.install(context, repository)   // shows the notification
 * ```
 */
object KaptorAndroid {

    private var notifier: KaptorNotifier? = null

    /**
     * Registers [repository] as the active one, starts the notification, and returns the notifier
     * so callers can [KaptorNotifier.stop] it if needed. Safe to call multiple times.
     *
     * Pass a [rerunner] to enable the swipe "Rerun" action; it should rebuild the request from the
     * given transaction and re-send it through the app's Ktor client.
     */
    fun install(
        context: Context,
        repository: TransactionRepository,
        rerunner: KaptorRequestRerunner? = null,
        mockRequests: List<KaptorMockRequest> = emptyList(),
    ): KaptorNotifier {
        KaptorRegistry.repository = repository
        KaptorRegistry.rerunner = rerunner
        KaptorRegistry.mockRequests = mockRequests
        return (notifier ?: KaptorNotifier(context, repository).also { notifier = it })
            .apply { start() }
    }

    /** Opens the inspector UI directly (e.g. from a debug-menu button), without the notification. */
    fun launch(context: Context) {
        val intent = Intent(context, KaptorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Stops the notification and clears the registered repository. */
    fun uninstall() {
        notifier?.stop()
        notifier = null
        KaptorRegistry.repository = null
    }
}
