// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package com.charlesportwoodii.tauri.plugin.agesignals

import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus

internal sealed class AgeSignalsState {
    object InRange : AgeSignalsState()
    object NotApplicable : AgeSignalsState()
    data class BelowMinimumAge(val minimumAge: Int) : AgeSignalsState()
    data class Error(val code: String, val message: String) : AgeSignalsState()
}

internal object AgeSignalsMapper {

    fun mapResult(result: AgeSignalsResult, minimumAge: Int): AgeSignalsState {
        when (result.userStatus()) {
            // null  = not in a regulated region (the law does not apply here).
            // UNKNOWN = regulated region, but Play has no age data for this user.
            // Neither yields a usable signal → the caller applies its own default policy.
            null, AgeSignalsVerificationStatus.UNKNOWN ->
                return AgeSignalsState.NotApplicable

            // Active supervised account managed/approved by a guardian. Parental consent
            // permits use regardless of the child's own age.
            AgeSignalsVerificationStatus.SUPERVISED ->
                return AgeSignalsState.InRange

            // A guardian approval is required and is not currently granted (awaiting a
            // decision, or explicitly denied) → block until resolved.
            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING,
            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED ->
                return AgeSignalsState.BelowMinimumAge(minimumAge)

            // VERIFIED / DECLARED carry a real age range → decide on the bounds below.
            else -> {}
        }

        val ageLower = result.ageLower()
        val ageUpper = result.ageUpper()

        // Confirmed at or above the minimum age.
        if (ageLower != null && ageLower >= minimumAge) {
            return AgeSignalsState.InRange
        }

        // Confirmed below the minimum age (upper bound known and below threshold).
        if (ageUpper != null && ageUpper < minimumAge) {
            return AgeSignalsState.BelowMinimumAge(minimumAge)
        }

        // Range spans the threshold — conservative: grant access.
        return AgeSignalsState.InRange
    }

    fun mapException(exception: Exception): AgeSignalsState {
        if (exception is AgeSignalsException) {
            return when (exception.errorCode) {
                // -3 = NETWORK_ERROR: transient — caller may retry.
                -3 -> AgeSignalsState.Error("networkError", "Network error: ${exception.message}")
                // -100 = INTERNAL_ERROR: unexpected Play Store internal error.
                -100 -> AgeSignalsState.Error("internalError", exception.message ?: "Internal error")
                // All remaining codes represent environmental conditions the caller cannot
                // fix at runtime (Play Store missing, API outdated, app sideloaded, etc.).
                // These are equivalent to "age signals not available" → NotApplicable.
                //   -1  = API_NOT_AVAILABLE
                //   -2  = PLAY_STORE_NOT_FOUND
                //   -4  = PLAY_SERVICES_NOT_FOUND
                //   -5  = CANNOT_BIND_TO_SERVICE
                //   -6  = PLAY_STORE_VERSION_OUTDATED
                //   -7  = PLAY_SERVICES_VERSION_OUTDATED
                //   -8  = CLIENT_TRANSIENT_ERROR
                //   -9  = APP_NOT_OWNED
                //   -10 = SDK_VERSION_OUTDATED
                else -> AgeSignalsState.NotApplicable
            }
        }
        return AgeSignalsState.Error("internalError", exception.message ?: "Unknown error")
    }
}
