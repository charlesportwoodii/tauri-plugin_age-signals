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
        val status = result.userStatus()
        val ageLower = result.ageLower()
        val ageUpper = result.ageUpper()

        // null = not in applicable region; UNKNOWN = in applicable region but age unknown
        // Both cases cannot confirm an age → not applicable
        if (status == null || status == AgeSignalsVerificationStatus.UNKNOWN) {
            return AgeSignalsState.NotApplicable
        }

        // Supervised account that was explicitly denied by parent
        if (status == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED) {
            return AgeSignalsState.BelowMinimumAge(minimumAge)
        }

        // User is confirmed at or above minimum age
        if (ageLower != null && ageLower >= minimumAge) {
            return AgeSignalsState.InRange
        }

        // User is confirmed below minimum age (upper bound known and below threshold)
        if (ageUpper != null && ageUpper < minimumAge) {
            return AgeSignalsState.BelowMinimumAge(minimumAge)
        }

        // Range spans the threshold — conservative: grant access
        return AgeSignalsState.InRange
    }

    fun mapException(exception: Exception): AgeSignalsState {
        if (exception is AgeSignalsException) {
            return when (exception.errorCode) {
                -3 -> AgeSignalsState.Error("networkError", "Network error: ${exception.message}")
                -2 -> AgeSignalsState.Error("playStoreNotFound", "Play Store not found")
                -9 -> AgeSignalsState.Error("appNotOwned", "App not installed via Play Store")
                // -1 = API_NOT_AVAILABLE: service connected but no age data for this app.
                // In production (non-regulated regions) the success callback fires with null
                // userStatus instead. For dev/sideloaded builds without Play Console config
                // this fires because the app is unknown to the age signals service.
                -1 -> AgeSignalsState.NotApplicable
                -4, -5, -6, -7, -8 ->
                    AgeSignalsState.Error("apiNotAvailable", "API not available: ${exception.message}")
                // -10 = SDK_VERSION_OUTDATED: the age-signals library in this APK is too old
                -10 -> AgeSignalsState.Error("apiNotAvailable", "Age Signals SDK version is outdated")
                // -100 = INTERNAL_ERROR: unexpected Play Store internal error
                -100 -> AgeSignalsState.Error("internalError", exception.message ?: "Internal error")
                // Any other unrecognised code: feature not applicable for this user/region
                else -> AgeSignalsState.NotApplicable
            }
        }
        return AgeSignalsState.Error("internalError", exception.message ?: "Unknown error")
    }
}
