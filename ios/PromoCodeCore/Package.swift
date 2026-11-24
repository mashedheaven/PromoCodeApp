// swift-tools-version:5.9
import PackageDescription

// Platform-independent logic for the iOS app: models, rules, reminder and
// geofence planning, and Supabase sync. Builds and tests on Linux too, so it
// can be verified without Xcode (`swift test`).
let package = Package(
    name: "PromoCodeCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "PromoCodeCore", targets: ["PromoCodeCore"]),
    ],
    targets: [
        .target(name: "PromoCodeCore"),
        .testTarget(name: "PromoCodeCoreTests", dependencies: ["PromoCodeCore"]),
    ]
)
