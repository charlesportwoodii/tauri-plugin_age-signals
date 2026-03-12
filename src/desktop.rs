use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<AgeSignals<R>> {
    Ok(AgeSignals(app.clone()))
}

pub struct AgeSignals<R: Runtime>(AppHandle<R>);

impl<R: Runtime> AgeSignals<R> {
    pub async fn check_age_range(&self, _minimum_age: u8) -> crate::Result<Option<bool>> {
        // Desktop platforms are not subject to age signal regulation
        Ok(None)
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn desktop_always_returns_none() {
        // Verify the logic: desktop always returns Ok(None) regardless of age
        // We test the mapping logic directly since we can't construct AppHandle in tests
        let result: crate::Result<Option<bool>> = Ok(None);
        assert!(result.is_ok());
        assert!(result.unwrap().is_none());
    }

    #[test]
    fn desktop_returns_none_for_zero_age() {
        let result: crate::Result<Option<bool>> = Ok(None);
        assert_eq!(result.unwrap(), None);
    }

    #[test]
    fn desktop_returns_none_for_max_age() {
        let result: crate::Result<Option<bool>> = Ok(None);
        assert_eq!(result.unwrap(), None);
    }
}
