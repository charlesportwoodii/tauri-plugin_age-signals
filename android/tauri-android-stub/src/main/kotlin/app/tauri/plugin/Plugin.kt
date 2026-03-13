// Copyright (c) 2024 charlesportwoodii@erianna.com
// SPDX-License-Identifier: BSD-3-Clause

package app.tauri.plugin

// Activity typed as Any so this stub compiles as a plain JVM library
// without requiring the Android SDK.
abstract class Plugin(protected val activity: Any)
