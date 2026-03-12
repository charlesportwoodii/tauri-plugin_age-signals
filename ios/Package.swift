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
        .testTarget(
            name: "AgeSignalsPluginTests",
            dependencies: ["tauri-plugin-age-signals"],
            path: "Tests/AgeSignalsPluginTests"
        )
    ]
)
