// swift-tools-version:5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "tauri-plugin-age-signals",
    platforms: [
        .macOS(.v13),
        .iOS(.v17),
    ],
    products: [
        .library(
            name: "tauri-plugin-age-signals",
            type: .static,
            targets: ["tauri-plugin-age-signals"]),
    ],
    dependencies: [
        .package(name: "Tauri", path: "../.tauri/tauri-api")
    ],
    targets: [
        .target(
            name: "tauri-plugin-age-signals",
            dependencies: [
                .byName(name: "Tauri")
            ],
            path: "Sources",
            // DeclaredAgeRange is a system framework available on iOS 26+.
            // Weak-linked so the plugin builds on older SDKs; all usage is already
            // guarded by #available(iOS 26, *) at runtime.
            linkerSettings: [
                .unsafeFlags(["-weak_framework", "DeclaredAgeRange"], .when(platforms: [.iOS, .macOS]))
            ]
        ),
        // The test file is fully self-contained (all mock types defined inline,
        // only XCTest imported). No dependency on the plugin target — that would
        // transitively pull in the Tauri SDK which imports UIKit, preventing
        // swift test from compiling on macOS runners.
        .testTarget(
            name: "AgeSignalsPluginTests",
            dependencies: [],
            path: "Tests/AgeSignalsPluginTests"
        )
    ]
)
