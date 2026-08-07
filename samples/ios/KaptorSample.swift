//
//  KaptorSample.swift
//
//  Drop-in sample showing how to present the Kaptor on iOS.
//
//  `Shared` below is your Kotlin Multiplatform framework module — the umbrella framework that
//  exports `:kaptor-ui` (and transitively `:kaptor-core`). Rename the import to match your
//  own framework (e.g. `import ComposeApp` or `import shared`).
//

import SwiftUI
import UIKit
import Shared

// MARK: - Shared Kotlin provider (recommended)
//
// The "Rerun" action and the "+" mock-requests sheet both send requests through YOUR Ktor client,
// which the inspector doesn't own. They're `KaptorRequestRerunner` / `KaptorMockRequest`, whose
// bodies are Kotlin `suspend` lambdas — build them in your **shared Kotlin** module where the
// client lives, then just hand them to Swift. For example, in commonMain/iosMain:
//
//   object KaptorProvider {
//       val repository = KaptorIos.shared.createRepository()
//       val client = HttpClient(Darwin) { install(Kaptor) { repository = KaptorProvider.repository } }
//       val rerunner = KaptorRequestRerunner { tx -> /* rebuild + client.request(tx.url) */ }
//       val mockRequests = listOf(
//           KaptorMockRequest(title: "List users", method: "GET", subtitle: "/users") {
//               client.get("https://api.example.com/users")
//           }
//       )
//   }

// MARK: - SwiftUI wrapper

/// Bridges the Compose `UIViewController` produced by `KaptorIos` into SwiftUI.
struct KaptorView: UIViewControllerRepresentable {
    let repository: TransactionRepository
    /// Optional — from your shared Kotlin provider. Pass `nil` / `[]` to hide the actions.
    var rerunner: KaptorRequestRerunner? = nil
    var mockRequests: [KaptorMockRequest] = []

    func makeUIViewController(context: Context) -> UIViewController {
        KaptorIos.shared.viewController(
            repository: repository,
            rerunner: rerunner,
            mockRequests: mockRequests
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - Presenting it

/// Example: a debug button that presents the inspector as a full-screen sheet.
struct DebugMenu: View {
    /// Inject the SAME repository you installed into your Ktor client's `Kaptor` plugin.
    let repository: TransactionRepository
    @State private var showingKaptor = false

    var body: some View {
        Button("Open Kaptor") { showingKaptor = true }
            .sheet(isPresented: $showingKaptor) {
                // Pull rerunner + mockRequests from your shared Kotlin provider.
                KaptorView(
                    repository: repository,
                    rerunner: KaptorProvider.shared.rerunner,
                    mockRequests: KaptorProvider.shared.mockRequests
                )
                .ignoresSafeArea()
            }
    }
}

// MARK: - UIKit alternative
//
// let vc = KaptorIos.shared.viewController(
//     repository: repository,
//     rerunner: KaptorProvider.shared.rerunner,
//     mockRequests: KaptorProvider.shared.mockRequests)
// vc.modalPresentationStyle = .fullScreen
// present(vc, animated: true)
//
// Or the minimal form (no rerun / no + sheet):
// let vc = KaptorIos.shared.viewController(repository: repository)
