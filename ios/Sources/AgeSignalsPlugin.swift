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
    var minimumAge: Int = 0
}

// MARK: - Bridge state (mirrors Android's AgeSignalsState)

internal enum AgeSignalsState {
    case inRange
    case notApplicable
    case belowMinimumAge(minimumAge: Int)
    case error(code: String, message: String)

    func toJSObject() -> [String: Any] {
        switch self {
        case .inRange:
            return ["state": "inRange"]
        case .notApplicable:
            return ["state": "notApplicable"]
        case .belowMinimumAge(let minimumAge):
            return ["state": "belowMinimumAge", "minimumAge": minimumAge]
        case .error(let code, let message):
            return ["state": "error", "errorCode": code, "errorMessage": message]
        }
    }
}

// MARK: - Mapper (mirrors Android's AgeSignalsMapper)

internal enum AgeSignalsMapper {

    #if canImport(DeclaredAgeRange)
    @available(iOS 26.2, macOS 26, *)
    static func mapResponse(_ response: AgeRangeService.Response, minimumAge: Int) -> AgeSignalsState {
        switch response {
        case .declinedSharing:
            return .notApplicable

        case .sharing(let range):
            return mapAgeRange(range, minimumAge: minimumAge)

        @unknown default:
            return .error(code: "unknownResponse", message: "Unexpected AgeRangeService response")
        }
    }

    @available(iOS 26.2, macOS 26, *)
    static func mapAgeRange(_ range: AgeRangeService.AgeRange, minimumAge: Int) -> AgeSignalsState {
        let ageLower = range.lowerBound
        let ageUpper = range.upperBound

        // Confirmed at or above the minimum age.
        if let lower = ageLower, lower >= minimumAge {
            return .inRange
        }

        // Confirmed below the minimum age (upper bound known and below threshold).
        if let upper = ageUpper, upper < minimumAge {
            return .belowMinimumAge(minimumAge: minimumAge)
        }

        // lowerBound == nil means the person is below the lowest age gate.
        if ageLower == nil {
            return .belowMinimumAge(minimumAge: minimumAge)
        }

        // Range spans the threshold — conservative: grant access.
        return .inRange
    }

    @available(iOS 26.2, macOS 26, *)
    static func mapError(_ error: Error) -> AgeSignalsState {
        if let ageError = error as? AgeRangeService.Error {
            switch ageError {
            case .notAvailable:
                // Service not available (missing Apple Account, device setup issue)
                return .notApplicable
            case .invalidRequest:
                return .error(code: "invalidRequest",
                              message: "Invalid age gate configuration — ensure minimumAge ≥ 2")
            @unknown default:
                return .error(code: "internalError", message: error.localizedDescription)
            }
        }
        return .error(code: "internalError", message: error.localizedDescription)
    }
    #endif
}

// MARK: - Plugin

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
            invoke.resolve(AgeSignalsState.notApplicable.toJSObject())
            return
        }

        do {
            let eligible = try await AgeRangeService.shared.isEligibleForAgeFeatures
            guard eligible else {
                invoke.resolve(AgeSignalsState.notApplicable.toJSObject())
                return
            }

            let vc = rootViewController()
            let response = try await AgeRangeService.shared.requestAgeRange(
                ageGates: minimumAge,
                in: vc
            )

            let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
            invoke.resolve(state.toJSObject())
        } catch {
            let state = AgeSignalsMapper.mapError(error)
            invoke.resolve(state.toJSObject())
        }
        #else
        invoke.resolve(AgeSignalsState.notApplicable.toJSObject())
        #endif
    }

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
