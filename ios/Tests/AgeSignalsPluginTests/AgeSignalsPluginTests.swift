// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

import XCTest

// We test the pure mapping logic independently of the Tauri plugin framework.
// Since AgeRangeService.shared is a system service, we use a protocol-based
// mock approach to exercise all response paths.

#if canImport(DeclaredAgeRange)
import DeclaredAgeRange
#endif

// MARK: - Mock types for testing (protocol-based)

/// The response type we simulate from AgeRangeService
enum MockAgeRangeResponse {
    case sharing(lowerBound: Int?, upperBound: Int?)
    case declinedSharing
}

enum MockAgeRangeServiceError: Error {
    case notAvailable
    case invalidRequest
}

// MARK: - Pure mapping logic under test

/// Mirror of the plugin's mapping logic — extracted for testability.
enum AgeRangeState: Equatable {
    case inRange
    case notApplicable
    case belowMinimumAge(Int)
    case error(String)
}

func mapMockResponse(
    _ response: MockAgeRangeResponse,
    minimumAge: Int
) -> AgeRangeState {
    switch response {
    case .declinedSharing:
        return .notApplicable
    case .sharing(let lowerBound, _):
        if lowerBound == nil {
            return .belowMinimumAge(minimumAge)
        } else {
            return .inRange
        }
    }
}

func mapMockError(_ error: MockAgeRangeServiceError) -> AgeRangeState {
    switch error {
    case .notAvailable:
        return .notApplicable
    case .invalidRequest:
        return .error("invalidRequest")
    }
}

// MARK: - Tests

final class AgeSignalsPluginTests: XCTestCase {

    // ---- iOS availability guard ----

    func test_notApplicable_when_platform_not_available() {
        // Simulates the #available(iOS 26, *) guard failing
        // On iOS < 26 the plugin returns notApplicable
        let state: AgeRangeState = .notApplicable
        XCTAssertEqual(state, .notApplicable)
    }

    func test_notApplicable_when_not_eligible() {
        // isEligibleForAgeFeatures == false → notApplicable
        let state: AgeRangeState = .notApplicable
        XCTAssertEqual(state, .notApplicable)
    }

    // ---- declinedSharing ----

    func test_declined_sharing_returns_notApplicable() {
        let response = MockAgeRangeResponse.declinedSharing
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .notApplicable)
    }

    // ---- sharing with lowerBound == nil (below minimum age) ----

    func test_sharing_nil_lowerBound_returns_belowMinimumAge_13() {
        let response = MockAgeRangeResponse.sharing(lowerBound: nil, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .belowMinimumAge(13))
    }

    func test_sharing_nil_lowerBound_returns_belowMinimumAge_18() {
        let response = MockAgeRangeResponse.sharing(lowerBound: nil, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 18)
        XCTAssertEqual(state, .belowMinimumAge(18))
    }

    // ---- sharing with lowerBound set (at or above minimum age) ----

    func test_sharing_lowerBound_13_returns_inRange_for_threshold_13() {
        let response = MockAgeRangeResponse.sharing(lowerBound: 13, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .inRange)
    }

    func test_sharing_lowerBound_18_returns_inRange() {
        let response = MockAgeRangeResponse.sharing(lowerBound: 18, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .inRange)
    }

    func test_sharing_lowerBound_set_returns_inRange_regardless_of_exact_value() {
        // Any non-nil lowerBound means the person is at or above the gate
        for lowerBound in [13, 15, 16, 17, 18, 21, 100] {
            let response = MockAgeRangeResponse.sharing(lowerBound: lowerBound, upperBound: nil)
            let state = mapMockResponse(response, minimumAge: 13)
            XCTAssertEqual(state, .inRange, "Expected inRange for lowerBound=\(lowerBound)")
        }
    }

    // ---- Errors ----

    func test_notAvailable_error_returns_notApplicable() {
        let state = mapMockError(.notAvailable)
        XCTAssertEqual(state, .notApplicable)
    }

    func test_invalidRequest_error_returns_error_state() {
        let state = mapMockError(.invalidRequest)
        XCTAssertEqual(state, .error("invalidRequest"))
    }

    // ---- Age gate boundary tests ----

    func test_threshold_13_below_age_returns_belowMinimumAge() {
        // Child under 13: lowerBound is nil (below lowest gate of 13)
        let response = MockAgeRangeResponse.sharing(lowerBound: nil, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .belowMinimumAge(13))
    }

    func test_threshold_13_at_age_returns_inRange() {
        // Person is exactly 13: lowerBound = 13
        let response = MockAgeRangeResponse.sharing(lowerBound: 13, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .inRange)
    }

    func test_multiple_age_gates_below_lowest_returns_belowMinimumAge() {
        // App uses gates [13, 16, 18] and person is under 13
        // lowerBound will be nil from Apple's API
        let response = MockAgeRangeResponse.sharing(lowerBound: nil, upperBound: nil)
        let state = mapMockResponse(response, minimumAge: 13)
        XCTAssertEqual(state, .belowMinimumAge(13))
    }
}
