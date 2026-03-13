// swift-tools-version:5.9
// Standalone test package — no Tauri/SwiftRs/UIKit dependency.
// All mock types are defined inline in the test file; only XCTest is imported.
// This compiles cleanly on macOS CI runners where UIKit is unavailable.

import PackageDescription

let package = Package(
    name: "AgeSignalsPluginTests",
    platforms: [
        .macOS(.v12),
    ],
    targets: [
        .testTarget(
            name: "AgeSignalsPluginTests",
            dependencies: [],
            path: "AgeSignalsPluginTests"
        )
    ]
)
