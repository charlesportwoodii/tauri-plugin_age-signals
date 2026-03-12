// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package com.charlesportwoodii.tauri.plugin.agesignals

import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Local JVM unit tests for AgeSignalsMapper.
 *
 * Tests all AgeSignalsVerificationStatus variants and all documented AgeSignalsException
 * error codes. No device or emulator required.
 *
 * Run with: ./gradlew test  (from android/)
 */
class AgeSignalsMapperTest {

    private val minimumAge = 13

    // ---- mapResult: VERIFIED ----

    @Test
    fun verified_above_minimum_age_returns_InRange() {
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

    // ---- mapResult: DECLARED ----

    @Test
    fun declared_above_minimum_age_returns_InRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.DECLARED)
            .setAgeLower(16)
            .setAgeUpper(17)
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun declared_below_minimum_age_returns_BelowMinimumAge() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.DECLARED)
            .setAgeLower(0)
            .setAgeUpper(12)
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(minimumAge), AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- mapResult: SUPERVISED ----

    @Test
    fun supervised_above_minimum_age_returns_InRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED)
            .setAgeLower(13)
            .setInstallId("test-install-id")
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun supervised_approval_pending_above_minimum_age_returns_InRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING)
            .setAgeLower(13)
            .setInstallId("test-install-id")
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun supervised_approval_denied_returns_BelowMinimumAge_regardless_of_age() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED)
            .setAgeLower(8)
            .setAgeUpper(12)
            .setInstallId("test-install-id")
            .build()
        assertEquals(AgeSignalsState.BelowMinimumAge(minimumAge), AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- mapResult: UNKNOWN and null ----

    @Test
    fun unknown_status_returns_NotApplicable() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.UNKNOWN)
            .build()
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    @Test
    fun null_status_returns_NotApplicable() {
        val result = AgeSignalsResult.builder().build()
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- mapResult: range spanning threshold ----

    @Test
    fun range_spanning_threshold_returns_InRange_conservatively() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED)
            .setAgeLower(0)
            .setInstallId("test-install-id")
            .build()
        assertEquals(AgeSignalsState.InRange, AgeSignalsMapper.mapResult(result, minimumAge))
    }

    // ---- mapException: known error codes ----

    @Test
    fun error_minus_1_api_not_available_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-1)))
    }

    @Test
    fun error_minus_2_play_store_not_found_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-2)) as AgeSignalsState.Error
        assertEquals("playStoreNotFound", state.code)
    }

    @Test
    fun error_minus_3_network_error_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-3)) as AgeSignalsState.Error
        assertEquals("networkError", state.code)
    }

    @Test
    fun error_minus_4_play_services_not_found_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-4)) as AgeSignalsState.Error
        assertEquals("apiNotAvailable", state.code)
    }

    @Test
    fun error_minus_5_cannot_bind_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-5)) as AgeSignalsState.Error
        assertEquals("apiNotAvailable", state.code)
    }

    @Test
    fun error_minus_6_play_store_outdated_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-6)) as AgeSignalsState.Error
        assertEquals("apiNotAvailable", state.code)
    }

    @Test
    fun error_minus_7_play_services_outdated_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-7)) as AgeSignalsState.Error
        assertEquals("apiNotAvailable", state.code)
    }

    @Test
    fun error_minus_8_client_transient_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-8)) as AgeSignalsState.Error
        assertEquals("apiNotAvailable", state.code)
    }

    @Test
    fun error_minus_9_app_not_owned_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-9)) as AgeSignalsState.Error
        assertEquals("appNotOwned", state.code)
    }

    @Test
    fun error_minus_10_sdk_outdated_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-10)) as AgeSignalsState.Error
        assertEquals("apiNotAvailable", state.code)
    }

    @Test
    fun error_minus_100_internal_returns_Error() {
        val state = AgeSignalsMapper.mapException(AgeSignalsException(-100)) as AgeSignalsState.Error
        assertEquals("internalError", state.code)
    }

    @Test
    fun unrecognised_AgeSignalsException_code_returns_NotApplicable() {
        assertEquals(AgeSignalsState.NotApplicable, AgeSignalsMapper.mapException(AgeSignalsException(-42)))
    }

    @Test
    fun non_AgeSignalsException_returns_internalError() {
        val state = AgeSignalsMapper.mapException(RuntimeException("boom")) as AgeSignalsState.Error
        assertEquals("internalError", state.code)
    }
}
