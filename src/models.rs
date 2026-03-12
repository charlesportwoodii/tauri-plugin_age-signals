use serde::{Deserialize, Serialize};

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
