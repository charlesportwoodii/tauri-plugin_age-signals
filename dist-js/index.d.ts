/**
 * Outcome of an age signal check.
 *
 * - `meetsAgeGate`  — confirmed at or above `minimumAge`, or a supervised account
 *                     approved/managed by a guardian.
 * - `belowAgeGate`  — confirmed under `minimumAge`, or a supervised account whose
 *                     guardian approval is pending or denied.
 * - `notApplicable` — no usable signal (unregulated region, unsupported platform,
 *                     user declined, or age could not be determined). Apply your own default.
 */
export type AgeSignal = "meetsAgeGate" | "belowAgeGate" | "notApplicable";
/**
 * Error returned when the Age Signals API is not available (e.g., Play Store outdated).
 */
export interface ApiNotAvailableError {
    type: "ApiNotAvailable";
    data: string;
}
/**
 * Error returned on network failure (Android only).
 */
export interface NetworkError {
    type: "NetworkError";
    data: string;
}
/**
 * Error returned when Play Store is not found (Android only).
 */
export interface PlayStoreNotFoundError {
    type: "PlayStoreNotFound";
}
/**
 * Error returned when the app was not installed via Play Store (Android only).
 */
export interface AppNotOwnedError {
    type: "AppNotOwned";
}
/**
 * Error returned for invalid age gate configuration (iOS only).
 */
export interface InvalidRequestError {
    type: "InvalidRequest";
    data: string;
}
/**
 * Error returned for unexpected internal failures.
 */
export interface InternalError {
    type: "InternalError";
    data: string;
}
export type AgeSignalsError = ApiNotAvailableError | NetworkError | PlayStoreNotFoundError | AppNotOwnedError | InvalidRequestError | InternalError;
/**
 * Determine whether the current user is permitted to use the app, given a minimum age.
 *
 * "Permitted" means either old enough, or a supervised account approved by a guardian.
 *
 * Platform behavior:
 * - **Android (API 23+)**: Queries the Play Age Signals SDK transparently (no UI).
 *   Only available in regulated regions (Brazil from March 2026, Utah from May 2026, etc.)
 * - **iOS 26+**: Presents a native system sheet asking the user to share their age range.
 *   Requires `com.apple.developer.declared-age-range` entitlement in the consuming app.
 * - **Desktop / unsupported**: Always returns `"notApplicable"`.
 *
 * @param minimumAge - The minimum age threshold to check (e.g., `13` for 13+).
 *   Must be ≥ 2. On iOS, this becomes the single age gate passed to `requestAgeRange`.
 *
 * @returns A promise resolving to an {@link AgeSignal}:
 *   - `"meetsAgeGate"`  — old enough, or a guardian-approved supervised account
 *   - `"belowAgeGate"`  — confirmed under `minimumAge`, or supervised approval pending/denied
 *   - `"notApplicable"` — no usable signal (region/platform/declined/undetermined); apply your default
 *
 * @throws {ApiNotAvailableError} when the platform API is unavailable
 * @throws {NetworkError} on network failures (Android)
 * @throws {PlayStoreNotFoundError} when Play Store is missing (Android)
 * @throws {AppNotOwnedError} when app was not installed via Play Store (Android)
 * @throws {InvalidRequestError} for bad age gate configuration (iOS)
 * @throws {InternalError} for unexpected platform errors
 *
 * @example
 * ```typescript
 * import { ageSignal } from 'tauri-plugin-age-signals'
 *
 * try {
 *   switch (await ageSignal(13)) {
 *     case 'meetsAgeGate':
 *       // Old enough, or guardian-approved — allow.
 *       break
 *     case 'belowAgeGate':
 *       // Confirmed under 13 (or supervised approval pending/denied) — restrict.
 *       break
 *     case 'notApplicable':
 *       // No signal here — apply your own default policy.
 *       break
 *   }
 * } catch (error: unknown) {
 *   const e = error as AgeSignalsError
 *   // Genuine failure (network, Play Store missing, etc.) — could not get an answer.
 * }
 * ```
 */
export declare function ageSignal(minimumAge: number): Promise<AgeSignal>;
