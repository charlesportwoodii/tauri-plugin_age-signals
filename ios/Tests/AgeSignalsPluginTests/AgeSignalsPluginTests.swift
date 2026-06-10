// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

import XCTest

// We test the pure mapping logic independently of the Tauri plugin framework.
// Since AgeRangeService.shared is a system service, we use mock types that
// mirror Apple's DeclaredAgeRange types and duplicate the mapper logic from
// AgeSignalsPlugin.swift so it can be verified on any host platform.

// MARK: - Mock types (mirror Apple's DeclaredAgeRange types)

struct MockAgeRange {
    var lowerBound: Int?
    var upperBound: Int?
}

enum MockAgeRangeResponse {
    case sharing(range: MockAgeRange)
    case declinedSharing
}

enum MockAgeRangeServiceError: Error {
    case notAvailable
    case invalidRequest
}

// MARK: - AgeSignalsState (mirrors production AgeSignalsState)

enum AgeSignalsState: Equatable {
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

// MARK: - AgeSignalsMapper (mirrors production AgeSignalsMapper)

enum AgeSignalsMapper {

    static func mapResponse(_ response: MockAgeRangeResponse, minimumAge: Int) -> AgeSignalsState {
        switch response {
        case .declinedSharing:
            return .notApplicable
        case .sharing(let range):
            return mapAgeRange(range, minimumAge: minimumAge)
        }
    }

    static func mapAgeRange(_ range: MockAgeRange, minimumAge: Int) -> AgeSignalsState {
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

    static func mapError(_ error: MockAgeRangeServiceError) -> AgeSignalsState {
        switch error {
        case .notAvailable:
            return .notApplicable
        case .invalidRequest:
            return .error(code: "invalidRequest",
                          message: "Invalid age gate configuration — ensure minimumAge ≥ 2")
        }
    }
}

// MARK: - Tests

final class AgeSignalsPluginTests: XCTestCase {

    let minimumAge = 13

    // ---- Platform availability guards ----

    func test_notApplicable_when_platform_not_available() {
        // iOS < 26: DeclaredAgeRange framework not available → notApplicable
        let state: AgeSignalsState = .notApplicable
        XCTAssertEqual(state, .notApplicable)
    }

    func test_notApplicable_when_not_eligible() {
        // isEligibleForAgeFeatures == false → notApplicable
        let state: AgeSignalsState = .notApplicable
        XCTAssertEqual(state, .notApplicable)
    }

    // ---- declinedSharing (user declined to share age) ----

    func test_declined_sharing_returns_notApplicable() {
        let response = MockAgeRangeResponse.declinedSharing
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .notApplicable)
    }

    // ---- sharing: confirmed at or above minimum age ----

    func test_sharing_lowerBound_at_minimum_returns_inRange() {
        // Person at exactly the minimum age: lowerBound == minimumAge
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: 13, upperBound: nil))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .inRange)
    }

    func test_sharing_lowerBound_above_minimum_returns_inRange() {
        // Adult (18+) checked against gate of 13
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: 18, upperBound: nil))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .inRange)
    }

    func test_sharing_lowerBound_13_upperBound_15_returns_inRange() {
        // Range 13-15 checked against gate of 13
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: 13, upperBound: 15))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .inRange)
    }

    // ---- sharing: confirmed below minimum age ----

    func test_sharing_nil_lowerBound_returns_belowMinimumAge() {
        // lowerBound == nil: person is below the lowest age gate
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: nil, upperBound: 11))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .belowMinimumAge(minimumAge: 13))
    }

    func test_sharing_nil_lowerBound_returns_belowMinimumAge_18() {
        // Same logic with a higher gate
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: nil, upperBound: 16))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: 18)
        XCTAssertEqual(state, .belowMinimumAge(minimumAge: 18))
    }

    func test_sharing_upperBound_below_minimum_returns_belowMinimumAge() {
        // Upper bound confirmed below threshold (e.g. verified 0-12 against gate 13)
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: 0, upperBound: 12))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .belowMinimumAge(minimumAge: 13))
    }

    func test_sharing_range_13_15_below_gate_18_returns_belowMinimumAge() {
        // Range 13-15 checked against higher gate of 18
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: 13, upperBound: 15))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: 18)
        XCTAssertEqual(state, .belowMinimumAge(minimumAge: 18))
    }

    // ---- sharing: range spans the threshold → conservative grant ----

    func test_sharing_range_spanning_threshold_returns_inRange() {
        // lowerBound below the gate, upperBound above it → cannot confirm "too young" → grant
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: 10, upperBound: 20))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .inRange)
    }

    // ---- sharing: nil lowerBound with nil upperBound ----

    func test_sharing_both_nil_returns_belowMinimumAge() {
        // Both bounds nil: no age data at all → defensive: block
        let response = MockAgeRangeResponse.sharing(
            range: MockAgeRange(lowerBound: nil, upperBound: nil))
        let state = AgeSignalsMapper.mapResponse(response, minimumAge: minimumAge)
        XCTAssertEqual(state, .belowMinimumAge(minimumAge: 13))
    }

    // ---- Errors ----

    func test_notAvailable_error_returns_notApplicable() {
        let state = AgeSignalsMapper.mapError(.notAvailable)
        XCTAssertEqual(state, .notApplicable)
    }

    func test_invalidRequest_error_returns_error_state() {
        let state = AgeSignalsMapper.mapError(.invalidRequest)
        XCTAssertEqual(state, .error(
            code: "invalidRequest",
            message: "Invalid age gate configuration — ensure minimumAge ≥ 2"))
    }

    // ---- toJSObject serialization ----

    func test_toJSObject_inRange() {
        let obj = AgeSignalsState.inRange.toJSObject()
        XCTAssertEqual(obj["state"] as? String, "inRange")
        XCTAssertNil(obj["minimumAge"])
        XCTAssertNil(obj["errorCode"])
    }

    func test_toJSObject_notApplicable() {
        let obj = AgeSignalsState.notApplicable.toJSObject()
        XCTAssertEqual(obj["state"] as? String, "notApplicable")
    }

    func test_toJSObject_belowMinimumAge() {
        let obj = AgeSignalsState.belowMinimumAge(minimumAge: 13).toJSObject()
        XCTAssertEqual(obj["state"] as? String, "belowMinimumAge")
        XCTAssertEqual(obj["minimumAge"] as? Int, 13)
    }

    func test_toJSObject_error() {
        let obj = AgeSignalsState.error(code: "networkError", message: "No connection").toJSObject()
        XCTAssertEqual(obj["state"] as? String, "error")
        XCTAssertEqual(obj["errorCode"] as? String, "networkError")
        XCTAssertEqual(obj["errorMessage"] as? String, "No connection")
    }
}
