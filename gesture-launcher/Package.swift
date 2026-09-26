// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "GestureLauncher",
    platforms: [.macOS(.v15)],
    targets: [
        .target(name: "GestureCore"),
        .testTarget(name: "GestureCoreTests", dependencies: ["GestureCore"]),
    ]
)
