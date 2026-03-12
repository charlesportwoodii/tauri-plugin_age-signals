use tauri_plugin_log::log::info;
use tauri::{AppHandle, Runtime, command};

use crate::AgeSignalsExt;

#[command]
pub(crate) async fn check_age_range<R: Runtime>(
    app: AppHandle<R>,
    minimum_age: u8,
) -> crate::Result<Option<bool>> {
    info!("check_age_range called with minimum_age={minimum_age}");
    let result = app.age_signals().check_age_range(minimum_age).await;
    match &result {
        Ok(value) => info!("check_age_range result: Ok({value:?})"),
        Err(e) => info!("check_age_range result: Err({e})"),
    }
    result
}
