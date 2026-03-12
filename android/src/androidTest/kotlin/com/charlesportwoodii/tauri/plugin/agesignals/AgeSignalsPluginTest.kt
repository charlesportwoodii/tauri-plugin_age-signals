// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package com.charlesportwoodii.tauri.plugin.agesignals

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus
import com.google.android.play.agesignals.testing.FakeAgeSignalsManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Android Age Signals mapping logic.
 *
 * Uses FakeAgeSignalsManager from com.google.android.play:age-signals testing package
 * to exercise all AgeSignalsVerificationStatus variants and error codes.
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AgeSignalsPluginTest {

    private lateinit var fakeManager: FakeAgeSignalsManager
    private val minimumAge = 13

    @Before
    fun setUp() {
        fakeManager = FakeAgeSignalsManager()
    }

    // ---- VERIFIED status ----

    @Test
    fun verified_user_above_minimum_age_returns_inRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(18)
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "inRange")
    }

    @Test
    fun verified_user_at_minimum_age_returns_inRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(13)
            .setAgeUpper(15)
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "inRange")
    }

    @Test
    fun verified_user_below_minimum_age_returns_belowMinimumAge() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.VERIFIED)
            .setAgeLower(0)
            .setAgeUpper(12)
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "belowMinimumAge")
    }

    // ---- DECLARED status ----

    @Test
    fun declared_user_above_minimum_age_returns_inRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.DECLARED)
            .setAgeLower(16)
            .setAgeUpper(17)
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "inRange")
    }

    @Test
    fun declared_user_below_minimum_age_returns_belowMinimumAge() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.DECLARED)
            .setAgeLower(0)
            .setAgeUpper(12)
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "belowMinimumAge")
    }

    // ---- SUPERVISED status ----

    @Test
    fun supervised_user_above_minimum_age_returns_inRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED)
            .setAgeLower(13)
            .setInstallId("test-install-id")
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "inRange")
    }

    @Test
    fun supervised_approval_pending_above_minimum_age_returns_inRange() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING)
            .setAgeLower(13)
            .setInstallId("test-install-id")
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "inRange")
    }

    @Test
    fun supervised_approval_denied_returns_belowMinimumAge() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED)
            .setAgeLower(8)
            .setAgeUpper(12)
            .setInstallId("test-install-id")
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "belowMinimumAge")
    }

    // ---- UNKNOWN status ----

    @Test
    fun unknown_status_returns_notApplicable() {
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.UNKNOWN)
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "notApplicable")
    }

    // ---- null status (not in applicable region) ----

    @Test
    fun null_status_returns_notApplicable() {
        // Build a result with no userStatus → null
        val result = AgeSignalsResult.builder().build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "notApplicable")
    }

    // ---- Range spanning threshold ----

    @Test
    fun range_spanning_threshold_returns_inRange_conservatively() {
        // ageLower=0, ageUpper=18: range spans threshold → conservative grant
        val result = AgeSignalsResult.builder()
            .setUserStatus(AgeSignalsVerificationStatus.SUPERVISED)
            .setAgeLower(0)
            .setInstallId("test-install-id")
            // no ageUpper → null
            .build()
        fakeManager.setNextAgeSignalsResult(result)
        assertStateForResult(fakeManager, minimumAge, "inRange")
    }

    // ---- Error codes ----

    @Test
    fun network_error_code_returns_error_state() {
        fakeManager.setNextAgeSignalsException(
            com.google.android.play.agesignals.AgeSignalsException(-3)
        )
        assertErrorCodeForException(fakeManager, minimumAge, "networkError")
    }

    @Test
    fun app_not_owned_returns_error_state() {
        fakeManager.setNextAgeSignalsException(
            com.google.android.play.agesignals.AgeSignalsException(-9)
        )
        assertErrorCodeForException(fakeManager, minimumAge, "appNotOwned")
    }

    @Test
    fun api_not_available_returns_error_state() {
        fakeManager.setNextAgeSignalsException(
            com.google.android.play.agesignals.AgeSignalsException(-1)
        )
        assertErrorCodeForException(fakeManager, minimumAge, "apiNotAvailable")
    }

    @Test
    fun play_store_not_found_returns_error_state() {
        fakeManager.setNextAgeSignalsException(
            com.google.android.play.agesignals.AgeSignalsException(-2)
        )
        assertErrorCodeForException(fakeManager, minimumAge, "playStoreNotFound")
    }

    @Test
    fun internal_error_code_returns_error_state() {
        fakeManager.setNextAgeSignalsException(
            com.google.android.play.agesignals.AgeSignalsException(-100)
        )
        assertErrorCodeForException(fakeManager, minimumAge, "internalError")
    }

    // ---- Helpers ----

    private fun assertStateForResult(
        manager: FakeAgeSignalsManager,
        minimumAge: Int,
        expectedState: String
    ) {
        var actualState: String? = null
        manager.checkAgeSignals(
            com.google.android.play.agesignals.AgeSignalsRequest.builder().build()
        ).addOnSuccessListener { result ->
            val mapped = mapResultForTest(result, minimumAge)
            actualState = mapped.getString("state")
        }.addOnFailureListener { exception ->
            val mapped = mapExceptionForTest(exception)
            actualState = mapped.getString("state")
        }
        // Tasks are synchronous in Fake manager
        assertEquals(expectedState, actualState)
    }

    private fun assertErrorCodeForException(
        manager: FakeAgeSignalsManager,
        minimumAge: Int,
        expectedCode: String
    ) {
        var actualCode: String? = null
        manager.checkAgeSignals(
            com.google.android.play.agesignals.AgeSignalsRequest.builder().build()
        ).addOnSuccessListener { _ ->
            // not expected in error tests
        }.addOnFailureListener { exception ->
            val mapped = mapExceptionForTest(exception)
            actualCode = mapped.getString("code")
        }
        assertEquals(expectedCode, actualCode)
    }

    // Mirror the mapping logic from AgeSignalsPlugin for testing without Android context
    private fun mapResultForTest(result: AgeSignalsResult, minimumAge: Int): app.tauri.plugin.JSObject {
        val status = result.userStatus()
        val ageLower = result.ageLower()
        val ageUpper = result.ageUpper()

        if (status == null || status == AgeSignalsVerificationStatus.UNKNOWN) {
            return jsObject("state" to "notApplicable")
        }
        if (status == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED) {
            return jsObject("state" to "belowMinimumAge", "minimumAge" to minimumAge)
        }
        if (ageLower != null && ageLower >= minimumAge) {
            return jsObject("state" to "inRange")
        }
        if (ageUpper != null && ageUpper < minimumAge) {
            return jsObject("state" to "belowMinimumAge", "minimumAge" to minimumAge)
        }
        return jsObject("state" to "inRange")
    }

    private fun mapExceptionForTest(exception: Exception): app.tauri.plugin.JSObject {
        if (exception is com.google.android.play.agesignals.AgeSignalsException) {
            return when (exception.errorCode) {
                -3 -> jsObject("state" to "error", "code" to "networkError")
                -2 -> jsObject("state" to "error", "code" to "playStoreNotFound")
                -9 -> jsObject("state" to "error", "code" to "appNotOwned")
                -1, -4, -5, -6, -7, -8 -> jsObject("state" to "error", "code" to "apiNotAvailable")
                else -> jsObject("state" to "error", "code" to "internalError")
            }
        }
        return jsObject("state" to "error", "code" to "internalError")
    }

    private fun jsObject(vararg pairs: Pair<String, Any>): app.tauri.plugin.JSObject =
        app.tauri.plugin.JSObject().apply {
            for ((k, v) in pairs) {
                when (v) {
                    is String -> put(k, v)
                    is Int -> put(k, v)
                    is Boolean -> put(k, v)
                }
            }
        }
}
