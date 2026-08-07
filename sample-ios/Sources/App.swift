import SwiftUI
import Shared

/// Minimal iOS demo: a home screen with buttons to fire mock traffic and open the Kaptor inspector.
@main
struct KaptorSampleIosApp: App {
    var body: some Scene {
        WindowGroup {
            HomeView()
        }
    }
}

struct HomeView: View {
    @State private var showKaptor = false

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 4) {
                Text("Kaptor").font(.largeTitle).bold()
                Text("iOS Sample").foregroundStyle(.secondary)
            }
            .padding(.bottom, 24)

            Button("Send sample requests") {
                KaptorProvider.shared.sendSample()
            }
            .buttonStyle(.borderedProminent)

            Button("Open Kaptor") {
                showKaptor = true
            }
            .buttonStyle(.bordered)

            Text("Tap “Send sample requests” to generate traffic, then open Kaptor. "
                 + "Or open Kaptor and use its + sheet to fire requests.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .sheet(isPresented: $showKaptor) {
            KaptorRootView().ignoresSafeArea()
        }
    }
}

/// Bridges the Compose `UIViewController` from `KaptorProvider` into SwiftUI.
struct KaptorRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        KaptorProvider.shared.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
