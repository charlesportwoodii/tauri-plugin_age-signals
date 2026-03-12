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
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest

@InvokeArg
class AgeRangeArgs {
    var minimumAge: Int = 0
}

@TauriPlugin
class AgeSignalsPlugin(private val activity: Activity) : Plugin(activity) {

    private val ageSignalsManager = AgeSignalsManagerFactory.create(activity)

    @Command
    fun checkAgeRange(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(AgeRangeArgs::class.java)
            val minimumAge = args.minimumAge

            ageSignalsManager
                .checkAgeSignals(AgeSignalsRequest.builder().build())
                .addOnSuccessListener { result ->
                    invoke.resolve(AgeSignalsMapper.mapResult(result, minimumAge).toJSObject())
                }
                .addOnFailureListener { exception ->
                    invoke.resolve(AgeSignalsMapper.mapException(exception).toJSObject())
                }
        } catch (e: Exception) {
            invoke.resolve(AgeSignalsState.Error("internalError", e.message ?: "Unknown error").toJSObject())
        }
    }
}

private fun AgeSignalsState.toJSObject(): JSObject = when (this) {
    is AgeSignalsState.InRange -> JSObject().apply { put("state", "inRange") }
    is AgeSignalsState.NotApplicable -> JSObject().apply { put("state", "notApplicable") }
    is AgeSignalsState.BelowMinimumAge -> JSObject().apply {
        put("state", "belowMinimumAge")
        put("minimumAge", minimumAge)
    }
    is AgeSignalsState.Error -> JSObject().apply {
        put("state", "error")
        put("code", code)
        put("message", message)
    }
}
