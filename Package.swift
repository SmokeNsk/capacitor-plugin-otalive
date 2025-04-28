// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorPluginOtalive",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapacitorPluginOtalive",
            targets: ["OtaLiveUpdaterPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "OtaLiveUpdaterPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/OtaLiveUpdaterPlugin"),
        .testTarget(
            name: "OtaLiveUpdaterPluginTests",
            dependencies: ["OtaLiveUpdaterPlugin"],
            path: "ios/Tests/OtaLiveUpdaterPluginTests")
    ]
)