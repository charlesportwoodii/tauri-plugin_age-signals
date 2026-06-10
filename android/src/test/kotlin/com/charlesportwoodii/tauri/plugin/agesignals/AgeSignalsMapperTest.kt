// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package com.charlesportwoodii.tauri.plugin.agesignals

import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

/**
 * Local JVM unit tests for AgeSignalsMapper.
 *
 * The fixtures mirror the scenarios Google documents for FakeAgeSignalsManager:
 * https://developer.android.com/google/play/age-signals/test-age-signals-api
 * — a VERIFIED adult, a SUPERVISED minor, a DECLARED self-reported range, supervised
 * approval PENDING (with and without a prior approval date) and DENIED, UNKNOWN, a null
 * status, and the NETWORK_ERROR exception — plus the full set of documented error codes.
 *
 * No device or emulator required. Run with: ./gradlew test  (from android/)
 */
class AgeSignalsMapperTest {

    private val minimumAge = 13

    private fun approvalDate(): Date =
        Date.from(LocalDate.of(2025, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant())

    // ---- VERIFIED: adult, decided on the reported age range ----

    @Test
    fun verified_adult_18_returns_InRange() {
        // Google fixture: VERIFIED, ageLower = 18.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(18)
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun verified_at_minimum_age_returns_InRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(13)
            .setAgeUpper(15)
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun verified_below_minimum_age_returns_BelowMinimumAge() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(0)
            .setAgeUpper(12)
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(minimumAge), AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- DECLARED: self-reported, decided on the reported age range ----

    @Test
    fun declared_custom_range_13_to_15_returns_InRange() {
        // Google fixture: DECLARED, ageLower = 13, ageUpper = 15, installId set.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.DECLARED)
            .setAgeLower(13)
            .setAgeUpper(15)
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun declared_below_minimum_age_returns_BelowMinimumAge() {
        // Same DECLARED 13-15 range checked against a higher gate of 18.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.DECLARED)
            .setAgeLower(13)
            .setAgeUpper(15)
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(18), AgeSignalsMapper.mapResult(result, 18))
    }

    // ---- SUPERVISED: guardian-managed → allowed regardless of the child's age ----

    @Test
    fun supervised_minor_13_to_17_returns_InRange() {
        // Google fixture: SUPERVISED, ageLower = 13, ageUpper = 17, installId set.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED)
            .setAgeLower(13)
            .setAgeUpper(17)
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun supervised_below_minimum_age_still_returns_InRange() {
        // Parental consent overrides the age gate: a supervised account below the gate
        // is still permitted. Gate of 18, supervised minor aged 13-17.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED)
            .setAgeLower(13)
            .setAgeUpper(17)
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, 18))
    }

    // ---- SUPERVISED_APPROVAL_PENDING: approval not yet granted → block ----

    @Test
    fun supervised_approval_pending_no_prior_approval_returns_BelowMinimumAge() {
        // Google fixture: PENDING, ageLower = 13, ageUpper = 17, installId, no approval date.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING)
            .setAgeLower(13)
            .setAgeUpper(17)
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(minimumAge), AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun supervised_approval_pending_with_prior_approval_returns_BelowMinimumAge() {
        // Google fixture: PENDING with a prior mostRecentApprovalDate. A new significant
        // change is awaiting approval → still blocked until resolved.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING)
            .setAgeLower(13)
            .setAgeUpper(17)
            .setMostRecentApprovalDate(approvalDate())
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(minimumAge), AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- SUPERVISED_APPROVAL_DENIED: explicitly denied → block regardless of age ----

    @Test
    fun supervised_approval_denied_returns_BelowMinimumAge() {
        // Google fixture: DENIED, ageLower = 13, ageUpper = 17, installId, approval date.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED)
            .setAgeLower(13)
            .setAgeUpper(17)
            .setMostRecentApprovalDate(approvalDate())
            .setInstallId("fake_install_id")
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(minimumAge), AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- UNKNOWN and null: no usable signal → NotApplicable ----

    @Test
    fun unknown_status_returns_NotApplicable() {
        // Google fixture: UNKNOWN, no age range.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.UNKNOWN)
            .build()
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun null_status_returns_NotApplicable() {
        // Google fixture: setUserStatus(null).
        val result = AgeSignalsResult.builder().build()
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- Age range spanning the threshold → conservative grant ----

    @Test
    fun verified_range_spanning_threshold_returns_InRange_conservatively() {
        // ageLower below the gate, ageUpper above it: cannot confirm "too young" → grant.
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(10)
            .setAgeUpper(20)
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- mapException: genuine errors (actionable by the caller) ----

    @Test
    fun network_error_returns_Error_networkError() {
        // -3 = NETWORK_ERROR: transient, retryable
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-3)) as AgeSignalsState.Error
        assertEquals("networkError", state.code)
    }

    @Test
    fun internal_error_returns_Error_internalError() {
        // -100 = INTERNAL_ERROR: unexpected Play Store error
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-100)) as AgeSignalsState.Error
        assertEquals("internalError", state.code)
    }

    @Test
    fun non_AgeSignalsException_returns_internalError() {
        val state = AgeSignalsMapper.mapException(RuntimeException("boom")) as AgeSignalsState.Error
        assertEquals("internalError", state.code)
    }

    // ---- mapException: environmental conditions → NotApplicable (not actionable) ----

    @Test
    fun error_minus_1_api_not_available_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-1)))
    }

    @Test
    fun error_minus_2_play_store_not_found_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-2)))
    }

    @Test
    fun error_minus_4_play_services_not_found_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-4)))
    }

    @Test
    fun error_minus_5_cannot_bind_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-5)))
    }

    @Test
    fun error_minus_6_play_store_outdated_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-6)))
    }

    @Test
    fun error_minus_7_play_services_outdated_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-7)))
    }

    @Test
    fun error_minus_8_client_transient_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-8)))
    }

    @Test
    fun error_minus_9_app_not_owned_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-9)))
    }

    @Test
    fun error_minus_10_sdk_outdated_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-10)))
    }

    @Test
    fun unrecognised_AgeSignalsException_code_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-42)))
    }
}
