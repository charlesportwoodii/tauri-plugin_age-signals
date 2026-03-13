// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package app.tauri.annotation

@Target(AnnotationTarget.CLASS)
annotation class TauriPlugin

@Target(AnnotationTarget.FUNCTION)
annotation class Command

@Target(AnnotationTarget.CLASS)
annotation class InvokeArg
