// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package app.tauri.plugin

open class Invoke {
    fun resolve(value: JSObject) {}
    fun <T> parseArgs(clazz: Class<T>): T = clazz.getDeclaredConstructor().newInstance()
}
