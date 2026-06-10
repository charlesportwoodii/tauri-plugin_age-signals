use tauri::{AppHandle, Runtime, command};
use log::info;

use crate::{AgeSignalsExt, models::AgeSignal};

#[command]
pub(crate) async fn age_signal<R: Runtime>(
    app: AppHandle<R>,
    minimum_age: u8,
) -> crate::Result<AgeSignal> {
    info!("age_signal called with minimum_age={minimum_age}");
    let result = app.age_signals().age_signal(minimum_age).await;
    match &result {
        Ok(value) => info!("age_signal result: Ok({value:?})"),
        Err(e) => info!("age_signal result: Err({e})"),
    }
    result
}
