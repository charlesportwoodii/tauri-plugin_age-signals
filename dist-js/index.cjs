'use strict';

var core = require('@tauri-apps/api/core');

/**
 * Check whether the current user meets the specified minimum age requirement.
 *
 * Platform behavior:
 * - **Android (API 23+)**: Queries the Play Age Signals SDK transparently (no UI).
 *   Only available in regulated regions (Brazil from March 2026, Utah from May 2026, etc.)
 * - **iOS 26+**: Presents a native system sheet asking the user to share their age range.
 *   Requires `com.apple.developer.declared-age-range` entitlement in the consuming app.
 * - **Desktop / unsupported**: Always returns `null` (not applicable).
 *
 * @param minimumAge - The minimum age threshold to check (e.g., `13` for 13+).
 *   Must be ≥ 2. On iOS, this becomes the single age gate passed to `requestAgeRange`.
 *
 * @returns A promise resolving to:
 *   - `true`  — User is confirmed at or above `minimumAge`
 *   - `null`  — Age cannot be determined (feature not available in region, platform
 *               doesn't support age signals, or user declined to share)
 *
 * @throws {BelowMinimumAgeError} when the user is confirmed below `minimumAge`
 * @throws {ApiNotAvailableError} when the platform API is unavailable
 * @throws {NetworkError} on network failures (Android)
 * @throws {PlayStoreNotFoundError} when Play Store is missing (Android)
 * @throws {AppNotOwnedError} when app was not installed via Play Store (Android)
 * @throws {InvalidRequestError} for bad age gate configuration (iOS)
 * @throws {InternalError} for unexpected platform errors
 *
 * @example
 * ```typescript
 * import { checkAgeRange } from 'tauri-plugin-age-signals'
 *
 * try {
 *   const eligible = await checkAgeRange(13)
 *   if (eligible === true) {
 *     // User is 13 or older — enable age-appropriate content
 *   } else {
 *     // null: not applicable in this region/platform — apply default restrictions
 *   }
 * } catch (error: unknown) {
 *   const e = error as AgeSignalsError
 *   if (e.type === 'BelowMinimumAge') {
 *     // User is confirmed under 13 — restrict access
 *   }
 * }
 * ```
 */
async function checkAgeRange(minimumAge) {
    return await core.invoke("plugin:age-signals|check_age_range", {
        minimumAge,
    });
}

exports.checkAgeRange = checkAgeRange;
