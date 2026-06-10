use serde::{Deserialize, Serialize};

/// Outcome of an age signal check.
///
/// This is the public result type. It answers a single legal question:
/// *is this individual permitted to use the app?* — where "permitted" means
/// either old enough, or a supervised account approved by a guardian.
///
/// Genuine failures (network, Play Store missing, etc.) are surfaced as
/// [`crate::Error`] instead — they are the absence of an answer, not an answer.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum AgeSignal {
    /// The user meets the age gate: confirmed at or above `minimum_age`, or a
    /// supervised account that is approved/managed by a guardian.
    MeetsAgeGate,
    /// The user is below the age gate: confirmed under `minimum_age`, or a
    /// supervised account whose guardian approval is pending or denied.
    BelowAgeGate,
    /// No usable age signal: the platform is not regulated here, does not
    /// support age signals (old OS / desktop), the user declined to share, or
    /// the platform could not determine an age. The caller applies its own
    /// default policy (typically: allow).
    NotApplicable,
}

/// Internal bridge DTO returned by native Android/iOS code.
/// These types are used in mobile.rs (cfg(mobile)) and in mapping.rs tests.
#[allow(dead_code)]
/// Native always resolves (never rejects) using one of:
///   {"state":"inRange"}
///   {"state":"notApplicable"}
///   {"state":"belowMinimumAge","minimumAge":13}
///   {"state":"error","code":"networkError","message":"..."}
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct MobileAgeRangeResult {
    pub state: MobileAgeRangeState,
    pub minimum_age: Option<u8>,
    pub error_code: Option<String>,
    pub error_message: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub(crate) enum MobileAgeRangeState {
    InRange,
    NotApplicable,
    BelowMinimumAge,
    Error,
}
