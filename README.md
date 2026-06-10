# tauri-plugin-age-signals

[![CI](https://github.com/charlesportwoodii/tauri-plugin-age-signals/actions/workflows/ci.yml/badge.svg)](https://github.com/charlesportwoodii/tauri-plugin-age-signals/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD--3--Clause-blue.svg)](LICENSE)

A [Tauri v2](https://v2.tauri.app) plugin for **age-range verification** using platform-native APIs. Enables compliant age-gating for regulated regions without storing or transmitting personal data.

| Platform | Min Version | Mechanism | UI Required |
|---|---|---|---|
| Android | API 23 (Android 6) | Google Play Age Signals SDK | No — transparent, no UI |
| iOS | 26+ | Apple `DeclaredAgeRange` framework | Yes — native system consent sheet |
| Desktop / other | Any | Not applicable | — |

> **Regulated regions (as of 2026):** Brazil (March 2026), Utah, USA (May 2026), Louisiana, USA (July 2026). In all other regions the API returns `"notApplicable"`, so your app should treat `"notApplicable"` as "apply default restrictions."

---

## Table of Contents

- [How It Works](#how-it-works)
- [Installation](#installation)
- [Setup](#setup)
  - [1. Register the Plugin (Rust)](#1-register-the-plugin-rust)
  - [2. Configure Permissions (Tauri Capabilities)](#2-configure-permissions-tauri-capabilities)
  - [3. iOS — Entitlement](#3-ios--entitlement)
  - [4. iOS — Info.plist Usage Description](#4-ios--infoplist-usage-description)
- [Usage](#usage)
  - [TypeScript / JavaScript](#typescript--javascript)
  - [Return Values](#return-values)
  - [Error Reference](#error-reference)
- [Platform Behavior Details](#platform-behavior-details)
  - [Android](#android)
  - [iOS](#ios)
  - [Desktop](#desktop)
- [Testing](#testing)
  - [Rust Unit Tests](#rust-unit-tests)
  - [Android Instrumented Tests](#android-instrumented-tests)
  - [iOS Tests](#ios-tests)
  - [Running the Example App](#running-the-example-app)
- [Contributing](#contributing)
- [License](#license)

---

## How It Works

Age verification regulations in several jurisdictions require apps to determine whether a user is above a minimum age threshold before showing age-gated content. This plugin bridges to the two platform-native mechanisms:

- **Android**: The [Google Play Age Signals SDK](https://developer.android.com/google/play/integrity/age-signals) queries the device's Play Store account data silently — no UI is shown to the user. The SDK returns an age range (e.g., "21–35") or a supervised account status. The plugin interprets this against the `minimumAge` you specify.
- **iOS 26+**: Apple's [`DeclaredAgeRange`](https://developer.apple.com/documentation/declaredagerange) framework presents a system sheet asking the user to consent to sharing their age range with the app. The result is either a confirmed age lower-bound or a declined/undetermined state.
- **Desktop / unsupported mobile**: Returns `"notApplicable"` immediately — no network call, no UI.

The plugin surfaces a single unified API regardless of platform. Your app code handles three resolved outcomes — `"meetsAgeGate"`, `"belowAgeGate"`, `"notApplicable"` — plus thrown errors for genuine failures.

---

## Installation

### Rust (`Cargo.toml`)

```toml
[dependencies]
tauri-plugin-age-signals = { git = "https://github.com/charlesportwoodii/tauri-plugin-age-signals" }
```

Once published to crates.io:

```toml
[dependencies]
tauri-plugin-age-signals = "0.1"
```

### JavaScript / TypeScript

```bash
# npm
npm install tauri-plugin-age-signals

# yarn
yarn add tauri-plugin-age-signals

# pnpm
pnpm add tauri-plugin-age-signals
```

Once published to npm, or point to the local path during development:

```json
{
  "dependencies": {
    "tauri-plugin-age-signals": "file:../../"
  }
}
```

---

## Setup

### 1. Register the Plugin (Rust)

In your app's `src-tauri/src/lib.rs`:

```rust
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_age_signals::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 2. Configure Permissions (Tauri Capabilities)

Add the plugin permission to your app's capability file (e.g., `src-tauri/capabilities/default.json`):

```json
{
  "$schema": "../gen/schemas/desktop-schema.json",
  "identifier": "default",
  "description": "Default capabilities",
  "windows": ["main"],
  "permissions": [
    "core:default",
    "age-signals:default"
  ]
}
```

`age-signals:default` grants `allow-check-age-range`. You can also use the fine-grained permission `age-signals:allow-check-age-range` directly, or `age-signals:deny-check-age-range` to explicitly block the command.

### 3. iOS — Entitlement

The consuming app must have the **Declared Age Range** entitlement. In Xcode:

1. Open your project in Xcode (`yarn tauri ios open` or open `gen/apple/*.xcodeproj`)
2. Select your app target → **Signing & Capabilities**
3. Click **+ Capability** and add **Declared Age Range**

This adds `com.apple.developer.declared-age-range` to your `.entitlements` file. Without this entitlement the iOS implementation returns `"notApplicable"` rather than presenting the consent sheet.

You can also add it manually to your entitlements file:

```xml
<key>com.apple.developer.declared-age-range</key>
<true/>
```

### 4. iOS — Info.plist Usage Description

iOS requires a usage description string explaining why the app requests age-range data. Add the following to your `Info.plist`:

```xml
<key>NSAgeRangeUsageDescription</key>
<string>This app uses your age range to show age-appropriate content.</string>
```

Without this key the system sheet will not be displayed and the call will fail with an `InvalidRequest` error.

---

## Usage

### TypeScript / JavaScript

```typescript
import { ageSignal } from 'tauri-plugin-age-signals'
import type { AgeSignalsError } from 'tauri-plugin-age-signals'

try {
  switch (await ageSignal(13)) {
    case 'meetsAgeGate':
      // Old enough, or a guardian-approved supervised account
      showAgeGatedContent()
      break

    case 'belowAgeGate':
      // Confirmed under 13, or supervised approval pending/denied
      blockAccess()
      break

    case 'notApplicable':
      // Age cannot be determined: desktop, non-regulated region, user declined (iOS),
      // sideloaded (Android), or platform has no signal. Apply your own default.
      applyDefaultRestrictions()
      break
  }
} catch (error: unknown) {
  const e = error as AgeSignalsError

  switch (e.type) {
    case 'NetworkError':
      // Android: transient network failure — safe to retry
      console.warn('Network error:', e.data)
      break

    case 'PlayStoreNotFound':
      // Android: Play Store is not installed
      // Treat the same as null (cannot verify)
      applyDefaultRestrictions()
      break

    case 'AppNotOwned':
      // Android: app was not installed through the Play Store
      // Side-loaded apps cannot use Age Signals
      applyDefaultRestrictions()
      break

    case 'ApiNotAvailable':
      // Android: Play Services or Age Signals API is outdated
      console.warn('API unavailable:', e.data)
      applyDefaultRestrictions()
      break

    case 'InvalidRequest':
      // iOS: misconfigured entitlement or Info.plist — check setup steps above
      console.error('Invalid age range request:', e.data)
      break

    case 'InternalError':
      console.error('Internal error:', e.data)
      break
  }
}
```

### Return Values

`ageSignal()` resolves to an `AgeSignal`:

| Value | Meaning |
|---|---|
| `"meetsAgeGate"` | User is **permitted**: confirmed at or above `minimumAge`, or a supervised account approved by a guardian |
| `"belowAgeGate"` | User is **not permitted**: confirmed below `minimumAge`, or a supervised account whose guardian approval is pending or denied |
| `"notApplicable"` | Age **cannot be determined**: feature unavailable in region, platform doesn't support age signals, user declined to share (iOS), or app is sideloaded (Android) |

Genuine failures (network, Play Store missing, etc.) are thrown — see [Error Reference](#error-reference) below.

> **Design guidance:** When the result is `"notApplicable"`, apply your **most restrictive** default. Errors represent the *absence* of an answer, not a "too young" verdict — only `"belowAgeGate"` confirms a user is under age.

### Error Reference

| Error type | When thrown | Platform |
|---|---|---|
| `ApiNotAvailable` | Play Services or the Age Signals API is too old to support the request. | Android |
| `NetworkError` | Transient network failure while querying age signals. | Android |
| `PlayStoreNotFound` | Google Play Store is not installed on the device. | Android |
| `AppNotOwned` | The app was not installed through the Play Store (e.g., sideloaded via ADB). | Android |
| `InvalidRequest` | Misconfigured entitlement or `minimumAge < 2`. Check [iOS setup](#3-ios--entitlement). | iOS |
| `InternalError` | Unexpected native error. `data` contains the raw error message. | Both |

---

## Platform Behavior Details

### Android

The plugin uses the [Google Play Age Signals SDK](https://developer.android.com/google/play/integrity/age-signals) (`com.google.android.play:age-signals`).

**Requirements:**
- Device must have Google Play Services installed
- App must be installed through the Play Store (not sideloaded)
- Device must be in a regulated region (or the SDK returns `"notApplicable"`)
- Minimum Android API level: 23 (Android 6.0)

**Age range interpretation:**

The Play API returns an age range (e.g., lower bound 21, upper bound 35) rather than an exact age. The plugin resolves this against `minimumAge` conservatively:

For `VERIFIED` and `DECLARED` users the Play API returns an age range (e.g., lower bound 21, upper bound 35) rather than an exact age. The plugin resolves this against `minimumAge` conservatively:

| Scenario | Result |
|---|---|
| Range is entirely at or above `minimumAge` | `"meetsAgeGate"` |
| Range spans the `minimumAge` boundary | `"meetsAgeGate"` (conservative — no false negatives) |
| Range is entirely below `minimumAge` | `"belowAgeGate"` |
| `SUPERVISED` status (guardian-managed account) | `"meetsAgeGate"` (parental consent, regardless of age) |
| `SUPERVISED_APPROVAL_PENDING` or `SUPERVISED_APPROVAL_DENIED` | `"belowAgeGate"` (approval not granted) |
| `UNKNOWN` status, or device not in a regulated region | `"notApplicable"` |

The conservative interpretation (range spanning the boundary → `"meetsAgeGate"`) avoids incorrectly locking out legitimate users. If your use-case requires stricter enforcement, treat `"notApplicable"` as restricted.

**Supervised accounts (Family Link):**

A `SUPERVISED` account is guardian-managed and treated as **permitted** — parental consent overrides the age gate, so a supervised minor below `minimumAge` still resolves to `"meetsAgeGate"`. While guardian approval for a significant change is outstanding the status is `SUPERVISED_APPROVAL_PENDING`, and an explicit rejection is `SUPERVISED_APPROVAL_DENIED`; both resolve to `"belowAgeGate"` until the approval is granted.

### iOS

The plugin uses Apple's [`DeclaredAgeRange`](https://developer.apple.com/documentation/declaredagerange) framework introduced in iOS 26.

**Requirements:**
- iOS 26 or later (earlier versions always return `"notApplicable"`)
- App must have `com.apple.developer.declared-age-range` entitlement
- `NSAgeRangeUsageDescription` must be set in `Info.plist`
- Device must be in an eligible region (per Apple's policy)

**User interaction:**

When all requirements are met, calling `ageSignal()` presents a **system-managed consent sheet** asking the user to share their age range. The user can decline; in that case the call returns `"notApplicable"` (not an error).

**Age interpretation:**

| Scenario | Result |
|---|---|
| User consents and lower bound ≥ `minimumAge` | `"meetsAgeGate"` |
| User consents and lower bound is nil (below range) | `"belowAgeGate"` |
| User declines to share | `"notApplicable"` |
| Device not eligible (non-regulated region, parental controls, etc.) | `"notApplicable"` |
| Framework not available (iOS < 26) | `"notApplicable"` |

### Desktop

Desktop platforms (macOS, Windows, Linux) are not subject to mobile age signal regulations. `ageSignal()` always returns `"notApplicable"` immediately with no network call or user interaction.

---

## Testing

### Rust Unit Tests

The plugin includes unit tests for the core mapping logic (14 tests total covering all result paths). Run them with:

```bash
# From the plugin root
cargo test

# With output for debugging
cargo test -- --nocapture

# Run a specific test module
cargo test mapping::tests
cargo test desktop::tests
```

Tests are in:
- `src/mapping.rs` — 11 tests covering all Android/iOS result state → Rust type mappings
- `src/desktop.rs` — 3 tests verifying desktop always returns `None`

### Android Instrumented Tests

The Android tests run on a connected device or emulator and use `FakeAgeSignalsManager` from the Play Age Signals SDK to simulate API responses without needing a real regulated-region device.

**Prerequisites:**
- Java 17+
- Android SDK with build tools for API 34+
- Android NDK r27 (`27.0.11902837`)
- A connected Android device **or** an x86_64 emulator (API 34+, Google APIs image recommended)
- Rust Android targets installed

**Setup:**

```bash
# Install Rust Android targets
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# Install frontend dependencies
cd examples/tauri-app
yarn install

# Initialize the Tauri Android project (generates gen/android/ and .tauri/)
yarn tauri android init
```

**Run the tests:**

```bash
# From examples/tauri-app — start a connected device or emulator first, then:
cd gen/android
./gradlew connectedAndroidTest

# To run only the plugin module's tests:
./gradlew connectedAndroidTest --tests "com.charlesportwoodii.tauri.plugin.agesignals.*"
```

**View results:** HTML reports are written to `gen/android/build/reports/androidTests/connected/`.

### iOS Tests

The iOS Swift tests are part of the Tauri-generated Xcode project and require:

- macOS with Xcode that includes the iOS 26 SDK (Xcode 16+ beta or later)
- The `DeclaredAgeRange` system framework (bundled with iOS 26 SDK)
- Rust iOS targets installed

**Setup:**

```bash
# Install Rust iOS targets
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios

# Install frontend dependencies
cd examples/tauri-app
yarn install

# Initialize the Tauri iOS project (generates gen/apple/ and .tauri/)
yarn tauri ios init
```

**Run the tests from Xcode:**

```bash
# Open the generated Xcode project
yarn tauri ios open

# Then in Xcode: Product → Test (⌘U)
# Or run a specific scheme/destination from the command line:
xcodebuild test \
  -project gen/apple/tauri-app.xcodeproj \
  -scheme tauri-app \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=26.0"
```

**Note:** The `DeclaredAgeRange` framework link in `ios/Package.swift` requires the iOS 26 SDK at build time. On Xcode versions without iOS 26 SDK, the Swift package will fail to link. The Rust compilation itself (without Swift) targets iOS without this restriction and works on any version.

**Validate Rust compilation only (no iOS 26 SDK needed):**

```bash
# From plugin root — validates Rust code compiles for iOS
cargo build --target aarch64-apple-ios
cargo build --target aarch64-apple-ios-sim
```

### Running the Example App

The `examples/tauri-app/` directory contains a Svelte demo application.

**Prerequisites for all platforms:**

```bash
cd examples/tauri-app
yarn install
```

**Desktop:**

```bash
yarn tauri dev          # Development mode with hot reload
yarn tauri build        # Production build
```

On desktop, `ageSignal()` always returns `"notApplicable"`. Use the desktop build to validate your error handling UI and `null` path.

**Android:**

Requirements: Java 17, Android SDK + NDK r27, Android device or emulator, Rust Android targets (see [Android Instrumented Tests](#android-instrumented-tests) setup).

```bash
# Debug build + deploy to connected device
yarn tauri android dev

# Release APK (requires signing configuration)
yarn tauri android build
```

Testing on a real device in a regulated region (Brazil, Utah, Louisiana) is required to exercise the live API path. In non-regulated regions or on emulators, `ageSignal()` returns `"notApplicable"`.

**iOS:**

Requirements: macOS, Xcode with iOS 26 SDK, Apple Developer account with the `com.apple.developer.declared-age-range` entitlement enabled, Rust iOS targets (see [iOS Tests](#ios-tests) setup).

```bash
# Open in Xcode for first-time signing setup
yarn tauri ios open

# Run on a connected device (signing must be configured in Xcode first)
yarn tauri ios dev

# Build IPA
yarn tauri ios build
```

The consent sheet only appears on physical devices running iOS 26+ in an eligible region. On the simulator, or on devices in non-eligible regions, `ageSignal()` returns `"notApplicable"`.

---

## Contributing

Contributions are welcome. Please open an issue to discuss significant changes before submitting a PR.

**Development setup:**

```bash
# Install Rust (minimum 1.85.0)
rustup toolchain install 1.85.0

# Build the plugin
cargo build

# Build the JS layer
yarn install
yarn build

# Run all Rust tests
cargo test
```

**Code style:**
- Rust: `cargo fmt` + `cargo clippy --all-features -- -D warnings`
- TypeScript: follow existing conventions in `guest-js/`

---

## License

BSD-3-Clause — see [LICENSE](LICENSE).

Copyright (c) Charles R. Portwood II
