// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

import SwiftRs
import Tauri
import UIKit
import WebKit

// DeclaredAgeRange is only available on iOS 26+. We conditionally import
// to avoid compile errors on older SDKs. At runtime, #available guards
// ensure we return notApplicable on unsupported platforms.
#if canImport(DeclaredAgeRange)
import DeclaredAgeRange
#endif

class AgeRangeArgs: Decodable {
    var minimumAge: Int = 13
}

class AgeSignalsPlugin: Plugin {

    @objc public func checkAgeRange(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(AgeRangeArgs.self)
        let minimumAge = args.minimumAge

        Task { @MainActor in
            await self.performAgeCheck(invoke: invoke, minimumAge: minimumAge)
        }
    }

    private func performAgeCheck(invoke: Invoke, minimumAge: Int) async {
        #if canImport(DeclaredAgeRange)
        guard #available(iOS 26.2, macOS 26, *) else {
            // iOS < 26: DeclaredAgeRange not available
            invoke.resolve(["state": "notApplicable"])
            return
        }

        do {
            let eligible = try await AgeRangeService.shared.isEligibleForAgeFeatures
            guard eligible else {
                // Not in a regulated region — age signals not required
                invoke.resolve(["state": "notApplicable"])
                return
            }

            let vc = rootViewController()
            let response = try await AgeRangeService.shared.requestAgeRange(
                ageGates: minimumAge,
                in: vc
            )

            switch response {
            case .declinedSharing:
                // User is in a non-regulated region and declined to share
                invoke.resolve(["state": "notApplicable"])

            case .sharing(let ageRange):
                if ageRange.lowerBound == nil {
                    // lowerBound == nil means person is BELOW the lowest age gate
                    // i.e., they are under minimumAge
                    invoke.resolve([
                        "state": "belowMinimumAge",
                        "minimumAge": minimumAge
                    ])
                } else {
                    // lowerBound is set → person is AT or ABOVE the minimum age gate
                    invoke.resolve(["state": "inRange"])
                }

            @unknown default:
                invoke.resolve([
                    "state": "error",
                    "errorCode": "unknownResponse",
                    "errorMessage": "Unexpected AgeRangeService response"
                ])
            }
        } catch {
            handleAgeRangeError(error: error, invoke: invoke)
        }
        #else
        // DeclaredAgeRange framework not available in this SDK
        invoke.resolve(["state": "notApplicable"])
        #endif
    }

    #if canImport(DeclaredAgeRange)
    @available(iOS 26.2, macOS 26, *)
    private func handleAgeRangeError(error: Error, invoke: Invoke) {
        if let ageError = error as? AgeRangeService.Error {
            switch ageError {
            case .notAvailable:
                // Service not available (missing Apple Account, device setup issue)
                invoke.resolve(["state": "notApplicable"])
            case .invalidRequest:
                invoke.resolve([
                    "state": "error",
                    "errorCode": "invalidRequest",
                    "errorMessage": "Invalid age gate configuration — ensure minimumAge ≥ 2"
                ])
            @unknown default:
                invoke.resolve([
                    "state": "error",
                    "errorCode": "internalError",
                    "errorMessage": error.localizedDescription
                ])
            }
        } else {
            invoke.resolve([
                "state": "error",
                "errorCode": "internalError",
                "errorMessage": error.localizedDescription
            ])
        }
    }
    #else
    private func handleAgeRangeError(error: Error, invoke: Invoke) {
        invoke.resolve(["state": "notApplicable"])
    }
    #endif

    private func rootViewController() -> UIViewController {
        UIApplication.shared
            .connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?
            .windows
            .first(where: { $0.isKeyWindow })?
            .rootViewController
            ?? UIViewController()
    }
}

@_cdecl("init_plugin_age_signals")
func initPlugin() -> Plugin {
    return AgeSignalsPlugin()
}
