package com.akardas.kaptor.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.akardas.kaptor.store.DatabaseDriverFactory
import com.akardas.kaptor.store.TransactionRepository
import platform.UIKit.UIViewController

/**
 * iOS entry point. Exposes the inspector as a [UIViewController] that SwiftUI/UIKit can present,
 * the iOS analogue of the Android `KaptorActivity`.
 *
 * From Swift:
 * ```swift
 * let vc = KaptorIos.shared.viewController(repository: repository)
 * present(vc, animated: true)
 * ```
 */
object KaptorIos {

    /** Wraps [KaptorScreen] in a Compose view controller for presentation on iOS. */
    fun viewController(repository: TransactionRepository): UIViewController =
        viewController(repository, rerunner = null, mockRequests = emptyList())

    /**
     * Full variant: pass a [rerunner] to enable the swipe "Rerun" action, and [mockRequests] to
     * show the **+** sheet for firing test traffic. Both run through your own Ktor client (the
     * inspector has none), so build them in your shared Kotlin module where the client lives.
     *
     * ```swift
     * let vc = KaptorIos.shared.viewController(
     *     repository: repo, rerunner: myRerunner, mockRequests: myMocks)
     * ```
     */
    fun viewController(
        repository: TransactionRepository,
        rerunner: KaptorRequestRerunner?,
        mockRequests: List<KaptorMockRequest>,
    ): UIViewController =
        ComposeUIViewController {
            CompositionLocalProvider(
                LocalKaptorShareHandler provides IosShareHandler(),
                LocalKaptorRequestRerunner provides rerunner,
                LocalKaptorMockRequests provides mockRequests,
            ) {
                KaptorTheme {
                    KaptorScreen(repository)
                }
            }
        }

    /**
     * Convenience factory for a persistent repository on iOS. Use the **same** instance you install
     * into your Ktor client's `Kaptor` plugin, otherwise the UI won't show captured traffic.
     */
    fun createRepository(): TransactionRepository =
        TransactionRepository(DatabaseDriverFactory())
}
