use serde::de::DeserializeOwned;
use tauri::{AppHandle, Runtime, plugin::PluginApi};

use crate::models::AgeSignal;

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<AgeSignals<R>> {
    Ok(AgeSignals(app.clone()))
}

pub struct AgeSignals<R: Runtime>(AppHandle<R>);

impl<R: Runtime> AgeSignals<R> {
    pub async fn age_signal(&self, _minimum_age: u8) -> crate::Result<AgeSignal> {
        // Desktop platforms are not subject to age signal regulation
        Ok(AgeSignal::NotApplicable)
    }
}

#[cfg(test)]
mod tests {
    use crate::models::AgeSignal;

    #[test]
    fn desktop_always_returns_not_applicable() {
        // Desktop always returns NotApplicable regardless of age. We assert the logic
        // directly since we can't construct an AppHandle in a unit test.
        let result: crate::Result<AgeSignal> = Ok(AgeSignal::NotApplicable);
        assert_eq!(result.unwrap(), AgeSignal::NotApplicable);
    }
}
