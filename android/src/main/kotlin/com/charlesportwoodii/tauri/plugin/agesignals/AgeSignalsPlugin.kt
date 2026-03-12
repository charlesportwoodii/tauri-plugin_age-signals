// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package com.charlesportwoodii.tauri.plugin.agesignals

import android.app.Activity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus

@InvokeArg
class AgeRangeArgs {
    var minimumAge: Int = 0
}

@TauriPlugin
class AgeSignalsPlugin(private val activity: Activity) : Plugin(activity) {

    private var ageSignalsManager: AgeSignalsManager =
        AgeSignalsManagerFactory.create(activity)

    @Command
    fun checkAgeRange(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(AgeRangeArgs::class.java)
            val minimumAge = args.minimumAge

            ageSignalsManager
                .checkAgeSignals(AgeSignalsRequest.builder().build())
                .addOnSuccessListener { result ->
                    val ret = mapResult(result, minimumAge)
                    invoke.resolve(ret)
                }
                .addOnFailureListener { exception ->
                    val ret = mapException(exception, minimumAge)
                    invoke.resolve(ret)
                }
        } catch (e: Exception) {
            invoke.resolve(errorResponse("internalError", e.message ?: "Unknown error"))
        }
    }

    private fun mapResult(result: AgeSignalsResult, minimumAge: Int): JSObject {
        val status = result.userStatus()
        val ageLower = result.ageLower()
        val ageUpper = result.ageUpper()

        // null status or UNKNOWN → not in an applicable region / user hasn't shared
        if (status == null || status == AgeSignalsVerificationStatus.UNKNOWN) {
            return notApplicableResponse()
        }

        // Supervised account that was explicitly denied by parent
        if (status == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED) {
            return belowMinimumAgeResponse(minimumAge)
        }

        // User is confirmed at or above minimum age
        if (ageLower != null && ageLower >= minimumAge) {
            return inRangeResponse()
        }

        // User is confirmed below minimum age (upper bound is known and below threshold)
        if (ageUpper != null && ageUpper < minimumAge) {
            return belowMinimumAgeResponse(minimumAge)
        }

        // Range spans the threshold (e.g., ageLower=0, ageUpper=18 when threshold=13)
        // Conservative: grant access when we cannot definitively confirm below-age
        return inRangeResponse()
    }

    private fun mapException(exception: Exception, minimumAge: Int): JSObject {
        if (exception is AgeSignalsException) {
            return when (exception.errorCode) {
                -3 -> errorResponse("networkError", "Network error: ${exception.message}")
                -2 -> errorResponse("playStoreNotFound", "Play Store not found")
                -9 -> errorResponse("appNotOwned", "App not installed via Play Store")
                -1, -4, -5, -6, -7, -8 ->
                    errorResponse("apiNotAvailable", "API not available: ${exception.message}")
                else -> errorResponse("internalError", exception.message ?: "Internal error")
            }
        }
        return errorResponse("internalError", exception.message ?: "Unknown error")
    }

    private fun inRangeResponse(): JSObject =
        JSObject().apply { put("state", "inRange") }

    private fun notApplicableResponse(): JSObject =
        JSObject().apply { put("state", "notApplicable") }

    private fun belowMinimumAgeResponse(minimumAge: Int): JSObject =
        JSObject().apply {
            put("state", "belowMinimumAge")
            put("minimumAge", minimumAge)
        }

    private fun errorResponse(code: String, message: String): JSObject =
        JSObject().apply {
            put("state", "error")
            put("code", code)
            put("message", message)
        }
}
