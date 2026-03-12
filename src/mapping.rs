use crate::{
    error::Error,
    models::{MobileAgeRangeResult, MobileAgeRangeState},
};

/// Maps the bridge DTO returned by native Android/iOS code to the unified Rust Result type.
#[allow(dead_code)] // Used from mobile.rs (cfg(mobile)); always compiled for tests
///
/// This function is always compiled (not gated behind #[cfg(mobile)]) so it can be unit tested
/// on any host platform including desktop.
pub(crate) fn map_mobile_result(
    result: MobileAgeRangeResult,
    minimum_age: u8,
) -> crate::Result<Option<bool>> {
    match result.state {
        MobileAgeRangeState::InRange => Ok(Some(true)),
        MobileAgeRangeState::NotApplicable => Ok(None),
        MobileAgeRangeState::BelowMinimumAge => Err(Error::BelowMinimumAge {
            minimum_age: result.minimum_age.unwrap_or(minimum_age),
        }),
        MobileAgeRangeState::Error => {
            let message = result.error_message.unwrap_or_default();
            match result.error_code.as_deref() {
                Some("networkError") => Err(Error::NetworkError(message)),
                Some("playStoreNotFound") => Err(Error::PlayStoreNotFound),
                Some("appNotOwned") => Err(Error::AppNotOwned),
                Some("apiNotAvailable") => Err(Error::ApiNotAvailable(message)),
                Some("invalidRequest") => Err(Error::InvalidRequest(message)),
                _ => Err(Error::InternalError(message)),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::{MobileAgeRangeResult, MobileAgeRangeState};

    fn make_result(
        state: MobileAgeRangeState,
        minimum_age: Option<u8>,
        error_code: Option<&str>,
        error_message: Option<&str>,
    ) -> MobileAgeRangeResult {
        MobileAgeRangeResult {
            state,
            minimum_age,
            error_code: error_code.map(String::from),
            error_message: error_message.map(String::from),
        }
    }

    #[test]
    fn in_range_returns_some_true() {
        let result = make_result(MobileAgeRangeState::InRange, None, None, None);
        assert_eq!(map_mobile_result(result, 13).unwrap(), Some(true));
    }

    #[test]
    fn not_applicable_returns_none() {
        let result = make_result(MobileAgeRangeState::NotApplicable, None, None, None);
        assert_eq!(map_mobile_result(result, 13).unwrap(), None);
    }

    #[test]
    fn below_minimum_age_returns_error_with_provided_age() {
        let result = make_result(MobileAgeRangeState::BelowMinimumAge, Some(13), None, None);
        match map_mobile_result(result, 13).unwrap_err() {
            Error::BelowMinimumAge { minimum_age } => assert_eq!(minimum_age, 13),
            e => panic!("expected BelowMinimumAge, got {e:?}"),
        }
    }

    #[test]
    fn below_minimum_age_falls_back_to_request_age() {
        let result = make_result(MobileAgeRangeState::BelowMinimumAge, None, None, None);
        match map_mobile_result(result, 18).unwrap_err() {
            Error::BelowMinimumAge { minimum_age } => assert_eq!(minimum_age, 18),
            e => panic!("expected BelowMinimumAge, got {e:?}"),
        }
    }

    #[test]
    fn error_network_maps_correctly() {
        let result = make_result(
            MobileAgeRangeState::Error,
            None,
            Some("networkError"),
            Some("No connection"),
        );
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::NetworkError(_)
        ));
    }

    #[test]
    fn error_app_not_owned_maps_correctly() {
        let result = make_result(MobileAgeRangeState::Error, None, Some("appNotOwned"), None);
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::AppNotOwned
        ));
    }

    #[test]
    fn error_play_store_not_found_maps_correctly() {
        let result = make_result(
            MobileAgeRangeState::Error,
            None,
            Some("playStoreNotFound"),
            None,
        );
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::PlayStoreNotFound
        ));
    }

    #[test]
    fn error_api_not_available_maps_correctly() {
        let result = make_result(
            MobileAgeRangeState::Error,
            None,
            Some("apiNotAvailable"),
            Some("API unavailable"),
        );
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::ApiNotAvailable(_)
        ));
    }

    #[test]
    fn error_invalid_request_maps_correctly() {
        let result = make_result(
            MobileAgeRangeState::Error,
            None,
            Some("invalidRequest"),
            Some("Bad age gates"),
        );
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::InvalidRequest(_)
        ));
    }

    #[test]
    fn error_unknown_code_maps_to_internal() {
        let result = make_result(
            MobileAgeRangeState::Error,
            None,
            Some("someUnknownCode"),
            Some("mystery"),
        );
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::InternalError(_)
        ));
    }

    #[test]
    fn error_no_code_maps_to_internal() {
        let result = make_result(MobileAgeRangeState::Error, None, None, None);
        assert!(matches!(
            map_mobile_result(result, 13).unwrap_err(),
            Error::InternalError(_)
        ));
    }
}
