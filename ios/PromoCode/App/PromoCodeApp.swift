import PromoCodeCore
import SwiftUI
import UIKit

/// Builds the model at launch, before any scene: when a region event wakes
/// the app in the background, the location manager must exist to receive it.
final class AppDelegate: NSObject, UIApplicationDelegate {
    @MainActor lazy var model = AppModel()

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        MainActor.assumeIsolated {
            model.registerBackgroundRefresh()
            model.refreshAll()
        }
        return true
    }
}

@main
struct PromoCodeApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(delegate.model)
                .onOpenURL { url in
                    if let link = DeepLinkParser.parse(url) { delegate.model.router.open(link) }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: delegate.model.refreshAll()
            case .background: delegate.model.scheduleBackgroundRefresh()
            default: break
            }
        }
    }
}

enum DeepLinkParser {
    static func parse(_ url: URL) -> DeepLink? { DeepLink(url: url) }
}
