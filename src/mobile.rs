use serde::de::DeserializeOwned;
use tauri::{
    AppHandle, Runtime,
    plugin::{PluginApi, PluginHandle},
};

use crate::{mapping::map_mobile_result, models::MobileAgeRangeResult};

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_age_signals);

pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<AgeSignals<R>> {
    #[cfg(target_os = "android")]
    let handle = api.register_android_plugin(
        "com.charlesportwoodii.tauri.plugin.agesignals",
        "AgeSignalsPlugin",
    )?;
    #[cfg(target_os = "ios")]
    let handle = api.register_ios_plugin(init_plugin_age_signals)?;
    Ok(AgeSignals(handle))
}

pub struct AgeSignals<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> AgeSignals<R> {
    pub async fn check_age_range(&self, minimum_age: u8) -> crate::Result<Option<bool>> {
        let result: MobileAgeRangeResult = self
            .0
            .run_mobile_plugin_async(
                "checkAgeRange",
                serde_json::json!({ "minimumAge": minimum_age }),
            )
            .await
            .map_err(crate::Error::from)?;

        map_mobile_result(result, minimum_age)
    }
}
