use crate::{
    error::Error,
    models::{AgeSignal, MobileAgeRangeResult, MobileAgeRangeState},
};

/// Maps the bridge DTO returned by native Android/iOS code to the public [`AgeSignal`] result.
#[allow(dead_code)] // Used from mobile.rs (cfg(mobile)); always compiled for tests
///
/// This function is always compiled (not gated behind #[cfg(mobile)]) so it can be unit tested
/// on any host platform including desktop.
///
/// The native layer has already applied the platform-specific policy (age range comparison,
/// supervised-account rules, regulated-region detection). This function only translates the
/// resolved state into the public type, routing genuine failures to [`Error`].
pub(crate) fn map_mobile_result(result: MobileAgeRangeResult) -> crate::Result<AgeSignal> {
    match result.state {
        MobileAgeRangeState::InRange => Ok(AgeSignal::MeetsAgeGate),
        MobileAgeRangeState::BelowMinimumAge => Ok(AgeSignal::BelowAgeGate),
        MobileAgeRangeState::NotApplicable => Ok(AgeSignal::NotApplicable),
        MobileAgeRangeState::Error => {
            let message = result.error_message.unwrap_or_default();
            match result.error_code.as_deref() {
                Some("networkError") => Err(Error::NetworkError(message)),
                Some("invalidRequest") => Err(Error::InvalidRequest(message)),
                Some("internalError") => Err(Error::InternalError(message)),
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
    fn in_range_maps_to_meets_age_gate() {
        let result = make_result(MobileAgeRangeState::InRange, None, None, None);
        assert_eq!(map_mobile_result(result).unwrap(), AgeSignal::MeetsAgeGate);
    }

    #[test]
    fn below_minimum_age_maps_to_below_age_gate() {
        // "Too young" is a legitimate outcome, not an error.
        let result = make_result(MobileAgeRangeState::BelowMinimumAge, Some(13), None, None);
        assert_eq!(map_mobile_result(result).unwrap(), AgeSignal::BelowAgeGate);
    }

    #[test]
    fn not_applicable_maps_to_not_applicable() {
        let result = make_result(MobileAgeRangeState::NotApplicable, None, None, None);
        assert_eq!(map_mobile_result(result).unwrap(), AgeSignal::NotApplicable);
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
            map_mobile_result(result).unwrap_err(),
            Error::NetworkError(_)
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
            map_mobile_result(result).unwrap_err(),
            Error::InvalidRequest(_)
        ));
    }

    #[test]
    fn error_internal_maps_correctly() {
        let result = make_result(
            MobileAgeRangeState::Error,
            None,
            Some("internalError"),
            Some("Something broke"),
        );
        assert!(matches!(
            map_mobile_result(result).unwrap_err(),
            Error::InternalError(_)
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
            map_mobile_result(result).unwrap_err(),
            Error::InternalError(_)
        ));
    }

    #[test]
    fn error_no_code_maps_to_internal() {
        let result = make_result(MobileAgeRangeState::Error, None, None, None);
        assert!(matches!(
            map_mobile_result(result).unwrap_err(),
            Error::InternalError(_)
        ));
    }
}
